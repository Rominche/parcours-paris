#!/usr/bin/env python3
"""Génère une vidéo de démo avec carte OSM, segments et itinéraire réel."""

from __future__ import annotations

import heapq
import json
import math
import subprocess
import sys
import time
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
GEOJSON = ROOT / "app/src/main/assets/paris_segments.geojson"
OUT_DIR = Path("/opt/cursor/artifacts")
FRAMES_DIR = Path("/tmp/parcours_frames")

# Notre-Dame → Place de la République (itinéraire réellement connecté dans le graphe OSM)
ORIGIN = (48.8530, 2.3499)
DEST = (48.8675, 2.3635)
ORIGIN_LABEL = "Notre-Dame"
DEST_LABEL = "République"

WIDTH, HEIGHT = 1080, 2400
FPS = 24
DURATION_S = 12

# Aligné sur MapLibreMap.kt — segments gris dans la bande « Paris plein écran »
LOD_UNEXPLORED_MIN_ZOOM = 12.4
LOD_UNEXPLORED_MAX_ZOOM = 12.9
LOD_SEGMENT_FADE_BAND = 0.2
LOD_ROUTE_MIN_ZOOM = 13.0
LOD_ROUTE_FADE_BAND = 0.35
PARIS_CENTER = (48.8566, 2.3522)
# Paris intra-muros (segments affichés sur toute la ville, pas en « tuile » corridor)
PARIS_BBOX = (2.249, 48.815, 2.421, 48.902)
PARIS_PERIPH_ZOOM = 10.8  # périphérique encore visible
PARIS_FULL_ZOOM = 12.7  # Paris remplit l'écran, seuil segments
ROUTE_DETAIL_ZOOM = 13.8
DEMO_SEGMENT_CAP = 6500  # sous-échantillonnage rendu vidéo


@dataclass
class Edge:
    to: str
    length: float
    geometry: list[tuple[float, float]]  # (lat, lon)


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def node_key(lat: float, lon: float) -> str:
    return f"{lat:.6f},{lon:.6f}"


def polyline_length(coords: list[tuple[float, float]]) -> float:
    total = 0.0
    for (lat1, lon1), (lat2, lon2) in zip(coords, coords[1:]):
        total += haversine(lat1, lon1, lat2, lon2)
    return total


def in_bbox(lat: float, lon: float, bbox: tuple[float, float, float, float]) -> bool:
    lon_min, lat_min, lon_max, lat_max = bbox
    return lon_min <= lon <= lon_max and lat_min <= lat <= lat_max


def load_all_segments() -> list[list[tuple[float, float]]]:
    with GEOJSON.open() as f:
        data = json.load(f)
    segments: list[list[tuple[float, float]]] = []
    for feature in data["features"]:
        coords = feature["geometry"]["coordinates"]
        if len(coords) < 2:
            continue
        segments.append([(c[1], c[0]) for c in coords])
    return segments


def filter_segments_for_view(
    segments: list[list[tuple[float, float]]],
    bbox: tuple[float, float, float, float],
) -> list[list[tuple[float, float]]]:
    return [s for s in segments if any(in_bbox(lat, lon, bbox) for lat, lon in s)]


def build_graph(segments: list[list[tuple[float, float]]]) -> tuple[dict[str, tuple[float, float]], dict[str, list[Edge]]]:
    nodes: dict[str, tuple[float, float]] = {}
    edges: dict[str, list[Edge]] = {}
    for geom in segments:
        length = polyline_length(geom)
        a, b = geom[0], geom[-1]
        ak, bk = node_key(*a), node_key(*b)
        nodes[ak], nodes[bk] = a, b
        edges.setdefault(ak, []).append(Edge(bk, length, geom))
        edges.setdefault(bk, []).append(Edge(ak, length, list(reversed(geom))))
    return nodes, edges


def nearest_node(nodes: dict[str, tuple[float, float]], point: tuple[float, float]) -> str | None:
    lat, lon = point
    best, best_d = None, float("inf")
    for key, (nlat, nlon) in nodes.items():
        d = haversine(lat, lon, nlat, nlon)
        if d < best_d:
            best, best_d = key, d
    return best


def largest_component(edges: dict[str, list[Edge]]) -> set[str]:
    seen_all: set[str] = set()
    largest: set[str] = set()
    for start in edges:
        if start in seen_all:
            continue
        comp: set[str] = set()
        stack = [start]
        while stack:
            u = stack.pop()
            if u in comp:
                continue
            comp.add(u)
            seen_all.add(u)
            for edge in edges.get(u, []):
                if edge.to not in comp:
                    stack.append(edge.to)
        if len(comp) > len(largest):
            largest = comp
    return largest


def nearest_node_in_component(
    nodes: dict[str, tuple[float, float]],
    point: tuple[float, float],
    component: set[str],
) -> str | None:
    lat, lon = point
    best, best_d = None, float("inf")
    for key in component:
        nlat, nlon = nodes[key]
        d = haversine(lat, lon, nlat, nlon)
        if d < best_d:
            best, best_d = key, d
    return best


def dijkstra(edges: dict[str, list[Edge]], start: str, end: str) -> tuple[float, list[str]] | None:
    dist = {start: 0.0}
    prev: dict[str, str | None] = {start: None}
    pq: list[tuple[float, str]] = [(0.0, start)]
    while pq:
        d, u = heapq.heappop(pq)
        if d > dist.get(u, float("inf")):
            continue
        if u == end:
            break
        for edge in edges.get(u, []):
            nd = d + edge.length
            if nd < dist.get(edge.to, float("inf")):
                dist[edge.to] = nd
                prev[edge.to] = u
                heapq.heappush(pq, (nd, edge.to))
    if end not in dist:
        return None
    path = []
    cur: str | None = end
    while cur is not None:
        path.append(cur)
        cur = prev.get(cur)
    path.reverse()
    return dist[end], path


def rebuild_route_geometry(path: list[str], edges: dict[str, list[Edge]]) -> list[tuple[float, float]]:
    geometry: list[tuple[float, float]] = []
    for i in range(len(path) - 1):
        u, v = path[i], path[i + 1]
        for edge in edges[u]:
            if edge.to == v:
                part = edge.geometry if not geometry else edge.geometry[1:]
                geometry.extend(part)
                break
    return geometry


def latlon_to_world_px(lat: float, lon: float, zoom: float) -> tuple[float, float]:
    s = 256 * (2**zoom)
    x = (lon + 180) / 360 * s
    sin_lat = math.sin(math.radians(lat))
    y = (0.5 - math.log((1 + sin_lat) / (1 - sin_lat)) / (4 * math.pi)) * s
    return x, y


def world_px_to_latlon(x: float, y: float, zoom: float) -> tuple[float, float]:
    s = 256 * (2**zoom)
    lon = x / s * 360 - 180
    n = math.pi - 2 * math.pi * y / s
    lat = math.degrees(math.atan(math.sinh(n)))
    return lat, lon


class TileCache:
    DISK_CACHE = Path("/tmp/osm_tile_cache")

    def __init__(self) -> None:
        self.cache: dict[tuple[int, int, int], Image.Image] = {}
        self.session = requests.Session()
        self.session.headers["User-Agent"] = "parcours-paris-demo/1.0 (educational demo)"
        self.DISK_CACHE.mkdir(parents=True, exist_ok=True)

    def needed_tiles(
        self,
        center: tuple[float, float],
        zoom: float,
    ) -> set[tuple[int, int, int]]:
        clat, clon = center
        cx, cy = latlon_to_world_px(clat, clon, zoom)
        tile_z = int(zoom)
        frac = zoom - tile_z
        scale = 2**frac
        top_left_x = cx - WIDTH / (2 * scale)
        top_left_y = cy - HEIGHT / (2 * scale)
        tile_size = 256
        start_tx = int(math.floor(top_left_x / tile_size))
        end_tx = int(math.floor((top_left_x + WIDTH / scale) / tile_size))
        start_ty = int(math.floor(top_left_y / tile_size))
        end_ty = int(math.floor((top_left_y + HEIGHT / scale) / tile_size))
        max_tile = 2**tile_z
        needed: set[tuple[int, int, int]] = set()
        for tx in range(start_tx, end_tx + 1):
            for ty in range(start_ty, end_ty + 1):
                if 0 <= ty < max_tile:
                    needed.add((tile_z, tx % max_tile, ty))
        return needed

    def _disk_path(self, z: int, x: int, y: int) -> Path:
        return self.DISK_CACHE / f"{z}_{x}_{y}.png"

    def prefetch(self, keys: set[tuple[int, int, int]]) -> None:
        missing = [k for k in keys if k not in self.cache]
        print(f"  téléchargement de {len(missing)} tuiles OSM…")
        for i, (z, x, y) in enumerate(sorted(missing)):
            disk = self._disk_path(z, x, y)
            if disk.exists():
                self.cache[(z, x, y)] = Image.open(disk).convert("RGB")
                continue
            url = f"https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            for attempt in range(5):
                resp = self.session.get(url, timeout=30)
                if resp.status_code == 200:
                    img = Image.open(BytesIO(resp.content)).convert("RGB")
                    img.save(disk)
                    self.cache[(z, x, y)] = img
                    break
                time.sleep(1.0 * (attempt + 1))
            else:
                self.cache[(z, x, y)] = Image.new("RGB", (256, 256), (232, 228, 220))
            if i % 10 == 0:
                time.sleep(0.3)

    def get(self, z: int, x: int, y: int) -> Image.Image:
        n = 2**z
        key = (z, x % n, max(0, min(n - 1, y)))
        if key not in self.cache:
            self.prefetch({key})
        return self.cache[key]


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def hex_rgba(hex_color: str, alpha: int) -> tuple[int, int, int, int]:
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def render_map(
    center: tuple[float, float],
    zoom: float,
    segments: list[list[tuple[float, float]]],
    route: list[tuple[float, float]] | None,
    route_progress: float,
    tiles: TileCache,
    show_sheet: bool,
    distance_m: float,
    eta_min: int,
    tile_progress: float = 1.0,
    segment_alpha: float = 1.0,
    marker_alpha: float = 1.0,
) -> Image.Image:
    clat, clon = center
    # Zoom entier pour les tuiles : évite le « swimming » et les trous entre tuiles
    tile_zoom = round(zoom)
    cx, cy = latlon_to_world_px(clat, clon, tile_zoom)
    draw_zoom = float(tile_zoom)
    scale = 1.0

    # Fond neutre type « carte en chargement »
    img = Image.new("RGBA", (WIDTH, HEIGHT), (232, 228, 220, 255))
    tile_z = tile_zoom

    top_left_x = cx - WIDTH / (2 * scale)
    top_left_y = cy - HEIGHT / (2 * scale)

    tile_size = 256
    start_tx = int(math.floor(top_left_x / tile_size))
    end_tx = int(math.floor((top_left_x + WIDTH / scale) / tile_size))
    start_ty = int(math.floor(top_left_y / tile_size))
    end_ty = int(math.floor((top_left_y + HEIGHT / scale) / tile_size))

    max_tile = 2**tile_z
    max_dist = math.hypot(WIDTH, HEIGHT)
    for tx in range(start_tx, end_tx + 1):
        for ty in range(start_ty, end_ty + 1):
            if ty < 0 or ty >= max_tile:
                continue
            tile = tiles.get(tile_z, tx, ty)
            px = int((tx * tile_size - top_left_x) * scale)
            py = int((ty * tile_size - top_left_y) * scale)
            scaled = tile.resize((int(tile_size * scale), int(tile_size * scale)), Image.Resampling.BILINEAR)
            # Fondu radial : les tuiles proches du centre apparaissent en premier
            tcx = px + scaled.width / 2
            tcy = py + scaled.height / 2
            dist_norm = math.hypot(tcx - WIDTH / 2, tcy - HEIGHT / 2) / max_dist
            tile_alpha = clamp01((tile_progress - dist_norm * 0.55) / 0.35)
            if tile_alpha <= 0:
                continue
            if tile_alpha < 1:
                alpha_layer = Image.new("RGBA", scaled.size, (255, 255, 255, int(255 * tile_alpha)))
                scaled = Image.composite(
                    scaled.convert("RGBA"),
                    Image.new("RGBA", scaled.size, (232, 228, 220, 255)),
                    alpha_layer,
                )
            else:
                scaled = scaled.convert("RGBA")
            img.alpha_composite(scaled, (px, py))

    draw = ImageDraw.Draw(img, "RGBA")

    def to_screen(lat: float, lon: float) -> tuple[float, float]:
        wx, wy = latlon_to_world_px(lat, lon, draw_zoom)
        return (wx - top_left_x) * scale, (wy - top_left_y) * scale

    # Segments piétonniers : bande de zoom « Paris plein écran »
    if segment_alpha > 0:
        seg_a = int(180 * clamp01(segment_alpha))
        line_w = max(2, int(3 * scale))
        for seg in segments:
            pts = [to_screen(lat, lon) for lat, lon in seg]
            if len(pts) >= 2:
                draw.line(pts, fill=(158, 158, 158, seg_a), width=line_w)

    if route and route_progress > 0:
        count = max(2, int(len(route) * route_progress))
        partial = route[:count]
        pts = [to_screen(lat, lon) for lat, lon in partial]
        if len(pts) >= 2:
            draw.line(pts, fill=(33, 150, 243, 255), width=max(3, int(6 * scale)))

    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
    except OSError:
        font = ImageFont.load_default()

    if marker_alpha > 0:
        m_a = int(255 * clamp01(marker_alpha))
        for point, color, label in [
            (ORIGIN, "#E53935", "A"),
            (DEST, "#43A047", "B"),
        ]:
            sx, sy = to_screen(*point)
            r = 14
            fill = hex_rgba(color, m_a)
            draw.ellipse((sx - r, sy - r, sx + r, sy + r), fill=fill, outline=(255, 255, 255, m_a), width=3)
            draw.text((sx + 18, sy - 10), label, fill=fill, font=font)

    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)

    # Barre de recherche
    od.rounded_rectangle((40, 80, WIDTH - 40, 170), radius=40, fill=(255, 255, 255, 245))
    od.text((80, 108), f"Place de la {DEST_LABEL}", fill=(30, 30, 30), font=font)

    # Bottom nav
    od.rectangle((0, HEIGHT - 160, WIDTH, HEIGHT), fill=(255, 255, 255, 250))
    nav_y = HEIGHT - 110
    for i, (label, active) in enumerate([("Carte", True), ("Profil", False), ("Paramètres", False)]):
        x = WIDTH * (i + 0.5) / 3
        color = (33, 150, 243, 255) if active else (120, 120, 120, 255)
        od.text((x - 30, nav_y), label, fill=color, font=font)

    # FAB
    fab_r = 56
    fab_cx, fab_cy = WIDTH - 90, HEIGHT - 280
    od.ellipse((fab_cx - fab_r, fab_cy - fab_r, fab_cx + fab_r, fab_cy + fab_r), fill=(33, 150, 243, 255))

    if show_sheet:
        sheet_h = 320
        od.rounded_rectangle((0, HEIGHT - 160 - sheet_h, WIDTH, HEIGHT - 160), radius=24, fill=(255, 255, 255, 250))
        od.text((48, HEIGHT - 160 - sheet_h + 36), "Itinéraire découverte", fill=(20, 20, 20), font=font)
        od.text((48, HEIGHT - 160 - sheet_h + 90), f"{distance_m/1000:.1f} km  ·  {eta_min} min à pied", fill=(80, 80, 80), font=font)
        od.text(
            (48, HEIGHT - 160 - sheet_h + 150),
            f"{ORIGIN_LABEL} → {DEST_LABEL}",
            fill=(33, 150, 243, 255),
            font=font,
        )
        # barre progression
        bar_y = HEIGHT - 160 - sheet_h + 220
        od.rounded_rectangle((48, bar_y, WIDTH - 48, bar_y + 16), radius=8, fill=(230, 230, 230, 255))
        prog_w = int((WIDTH - 96) * route_progress)
        od.rounded_rectangle((48, bar_y, 48 + prog_w, bar_y + 16), radius=8, fill=(33, 150, 243, 255))

    # Échelle
    od.text((40, HEIGHT - 200), "500 m", fill=(60, 60, 60), font=font)
    od.line((40, HEIGHT - 215, 140, HEIGHT - 215), fill=(60, 60, 60), width=3)

    return Image.alpha_composite(img, overlay).convert("RGB")


@dataclass
class FrameState:
    center: tuple[float, float]
    zoom: float
    route_progress: float
    show_sheet: bool
    tile_progress: float
    segment_alpha: float
    marker_alpha: float


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def ease_in_out(t: float) -> float:
    return t * t * (3 - 2 * t)


def alpha_above_threshold(zoom: float, threshold: float, band: float) -> float:
    """Opacité 0→1 quand le zoom franchit un seuil (pas le temps)."""
    if zoom < threshold - band:
        return 0.0
    if zoom >= threshold:
        return 1.0
    return ease_in_out((zoom - (threshold - band)) / band)


def segment_alpha_from_zoom(zoom: float) -> float:
    """Segments gris visibles seulement entre min et max zoom (pas périph', pas tuile rue)."""
    band = LOD_SEGMENT_FADE_BAND
    z_min = LOD_UNEXPLORED_MIN_ZOOM
    z_max = LOD_UNEXPLORED_MAX_ZOOM
    if zoom < z_min - band or zoom > z_max + band:
        return 0.0
    if z_min <= zoom <= z_max:
        return 1.0
    if zoom < z_min:
        return ease_in_out((zoom - (z_min - band)) / band)
    return ease_in_out((z_max + band - zoom) / band)


def visibility_from_zoom(zoom: float) -> tuple[float, float, float, bool, float]:
    """Dérive segments, marqueurs, itinéraire et tuiles du niveau de zoom."""
    segment_alpha = segment_alpha_from_zoom(zoom)
    route_progress = alpha_above_threshold(zoom, LOD_ROUTE_MIN_ZOOM, LOD_ROUTE_FADE_BAND)
    marker_alpha = route_progress  # A/B visibles quand l'itinéraire devient pertinent
    show_sheet = route_progress > 0.12
    # Tuiles OSM : toujours rendues au zoom courant (pas de délai temporel)
    tile_progress = 1.0
    return segment_alpha, marker_alpha, route_progress, show_sheet, tile_progress


def compute_camera(t: float, mid_lat: float, mid_lon: float, route: list[tuple[float, float]]) -> tuple[tuple[float, float], float]:
    """Le temps ne pilote que la caméra (centre + zoom)."""
    if t < 0.30:
        # Île-de-France : périphérique visible, pas de segments
        p = ease_in_out(t / 0.30)
        zoom = lerp(PARIS_PERIPH_ZOOM, 11.5, p)
        center = PARIS_CENTER
    elif t < 0.58:
        # Paris plein écran : segments uniquement quand z ≥ 12.4
        p = ease_in_out((t - 0.30) / 0.28)
        zoom = lerp(11.5, PARIS_FULL_ZOOM, p)
        center = PARIS_CENTER
    elif t < 0.72:
        # Rapprochement itinéraire : sortie de la bande segments (zoom > 12.8)
        p = ease_in_out((t - 0.58) / 0.14)
        zoom = lerp(PARIS_FULL_ZOOM, 13.2, p)
        center = (lerp(PARIS_CENTER[0], mid_lat, p), lerp(PARIS_CENTER[1], mid_lon, p))
    else:
        # Suivi rue : itinéraire seul, sans nuage de segments
        p = ease_in_out((t - 0.72) / 0.28)
        idx = int(p * (len(route) - 1))
        zoom = lerp(13.2, ROUTE_DETAIL_ZOOM, p)
        center = route[idx]
    return center, zoom


def compute_frame_state(
    t: float,
    mid_lat: float,
    mid_lon: float,
    route: list[tuple[float, float]],
) -> FrameState:
    center, zoom = compute_camera(t, mid_lat, mid_lon, route)
    segment_alpha, marker_alpha, route_progress, show_sheet, tile_progress = visibility_from_zoom(zoom)
    return FrameState(
        center=center,
        zoom=zoom,
        route_progress=route_progress,
        show_sheet=show_sheet,
        tile_progress=tile_progress,
        segment_alpha=segment_alpha,
        marker_alpha=marker_alpha,
    )


def main() -> int:
    global ORIGIN, DEST
    print("Chargement des segments…")
    all_segments = load_all_segments()
    print(f"  {len(all_segments)} segments au total")

    nodes, edges = build_graph(all_segments)
    component = largest_component(edges)
    start = nearest_node_in_component(nodes, ORIGIN, component)
    end = nearest_node_in_component(nodes, DEST, component)
    if not start or not end:
        print("Impossible de trouver les nœuds de départ/arrivée", file=sys.stderr)
        return 1

    result = dijkstra(edges, start, end)
    if not result:
        print("Aucun itinéraire trouvé", file=sys.stderr)
        return 1

    distance_m, path = result
    route = rebuild_route_geometry(path, edges)
    eta_min = max(1, int(distance_m / 1.39 / 60))
    print(f"Itinéraire : {distance_m/1000:.2f} km, {eta_min} min, {len(route)} points")

    ORIGIN = nodes[start]
    DEST = nodes[end]

    paris_segments = filter_segments_for_view(all_segments, PARIS_BBOX)
    if len(paris_segments) > DEMO_SEGMENT_CAP:
        step = max(1, len(paris_segments) // DEMO_SEGMENT_CAP)
        paris_segments = paris_segments[::step]
    print(f"  {len(paris_segments)} segments Paris (démo)")

    mid_lat = (ORIGIN[0] + DEST[0]) / 2
    mid_lon = (ORIGIN[1] + DEST[1]) / 2

    FRAMES_DIR.mkdir(parents=True, exist_ok=True)
    for f in FRAMES_DIR.glob("*.png"):
        f.unlink()

    tiles = TileCache()
    total_frames = FPS * DURATION_S

    camera_frames: list[FrameState] = []
    for frame in range(total_frames):
        t = frame / max(1, total_frames - 1)
        camera_frames.append(compute_frame_state(t, mid_lat, mid_lon, route))

    all_tiles: set[tuple[int, int, int]] = set()
    for state in camera_frames:
        all_tiles |= tiles.needed_tiles(state.center, state.zoom)
    tiles.prefetch(all_tiles)

    for frame, state in enumerate(camera_frames):
        img = render_map(
            state.center,
            state.zoom,
            paris_segments,
            route,
            state.route_progress,
            tiles,
            state.show_sheet,
            distance_m,
            eta_min,
            tile_progress=state.tile_progress,
            segment_alpha=state.segment_alpha,
            marker_alpha=state.marker_alpha,
        )
        img.save(FRAMES_DIR / f"frame_{frame:04d}.png")
        if frame % 24 == 0:
            print(f"  frame {frame}/{total_frames}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_mp4 = OUT_DIR / "parcours-paris-demo.mp4"
    subprocess.run(
        [
            "ffmpeg", "-y", "-framerate", str(FPS),
            "-i", str(FRAMES_DIR / "frame_%04d.png"),
            "-c:v", "libx264", "-pix_fmt", "yuv420p",
            "-movflags", "+faststart", str(out_mp4),
        ],
        check=True,
        capture_output=True,
    )
    print(f"Vidéo générée : {out_mp4}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
