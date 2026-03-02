#!/usr/bin/env python3
"""
Récupère toutes les rues de Paris depuis OpenStreetMap via l'API Overpass
et génère paris_segments.geojson pour l'app Android.

Usage: python scripts/fetch_paris_streets.py

Nécessite: pip install requests
"""

import json
import sys
import time
from pathlib import Path

try:
    import requests
except ImportError:
    print("Erreur: pip install requests")
    sys.exit(1)

# Paris intra-muros bbox
PARIS_BBOX = (48.815, 2.225, 48.902, 2.417)  # min_lat, min_lon, max_lat, max_lon
# Alternative: https://overpass.kumi.systems/api/interpreter (souvent plus permissif)
OVERPASS_URL = "https://overpass.kumi.systems/api/interpreter"

def fetch_paris_ways():
    """Récupère les ways OSM dans Paris, en 16 tuiles pour éviter timeouts et rate limit."""
    min_lat, min_lon, max_lat, max_lon = PARIS_BBOX
    tiles = []
    for i in range(4):
        for j in range(4):
            s = min_lat + (max_lat - min_lat) * i / 4
            n = min_lat + (max_lat - min_lat) * (i + 1) / 4
            w = min_lon + (max_lon - min_lon) * j / 4
            e = min_lon + (max_lon - min_lon) * (j + 1) / 4
            tiles.append((s, w, n, e))

    all_elements = []
    seen_ids = set()
    n_tiles = len(tiles)

    for i, (s, w, n, e) in enumerate(tiles):
        query = f"""
[out:json][timeout:90];
(
  way["highway"]({s},{w},{n},{e});
);
out body geom;
"""
        if i > 0:
            time.sleep(8)  # Éviter rate limit Overpass (429)
        print(f"Tuile {i+1}/{n_tiles} ({s:.3f},{w:.3f},{n:.3f},{e:.3f})...", end=" ", flush=True)
        try:
            r = requests.post(OVERPASS_URL, data={"data": query}, timeout=120)
            if r.status_code == 429:
                print("Rate limit, attente 60s...", flush=True)
                time.sleep(60)
                r = requests.post(OVERPASS_URL, data={"data": query}, timeout=120)
            r.raise_for_status()
            data = r.json()
            count = 0
            for el in data.get("elements", []):
                if el.get("type") == "way" and el.get("id") and el.get("id") not in seen_ids:
                    if "geometry" in el and len(el["geometry"]) >= 2:
                        all_elements.append(el)
                        seen_ids.add(el["id"])
                        count += 1
            print(f"{count} ways")
        except Exception as ex:
            print(f"ERREUR: {ex}")
            raise

    return all_elements


def to_geojson(elements, precision=5):
    """Convertit les éléments Overpass en GeoJSON FeatureCollection.
    precision=5 donne ~1m, réduit la taille du fichier."""
    features = []
    for el in elements:
        geom = el.get("geometry", [])
        if len(geom) < 2:
            continue
        coords = [
            [round(n["lon"], precision), round(n["lat"], precision)]
            for n in geom
        ]
        features.append({
            "type": "Feature",
            "properties": {"osm_way_id": el["id"]},
            "geometry": {"type": "LineString", "coordinates": coords}
        })
    return {"type": "FeatureCollection", "features": features}


def main():
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    output_path = project_root / "app" / "src" / "main" / "assets" / "paris_segments.geojson"

    print("Récupération des rues de Paris depuis OpenStreetMap...")
    try:
        elements = fetch_paris_ways()
    except Exception as e:
        print(f"\nErreur: {e}")
        print("Conseil: relancez le script plus tard (rate limit) ou utilisez un autre réseau.")
        sys.exit(1)
    print(f"Total: {len(elements)} segments")

    geojson = to_geojson(elements)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(geojson, f, ensure_ascii=False, separators=(",", ":"))

    print(f"Écrit: {output_path}")
    print(f"Taille: {output_path.stat().st_size / 1024 / 1024:.1f} Mo")


if __name__ == "__main__":
    main()
