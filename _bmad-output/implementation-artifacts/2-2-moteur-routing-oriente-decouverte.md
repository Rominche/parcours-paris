# Story 2.2: Moteur de routing orienté découverte

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want obtenir un itinéraire A→B qui privilégie les rues non parcourues,
so that je découvre de nouvelles rues tout en allant à destination (FR6).

## Acceptance Criteria

1. **Given** j'ai saisi une destination et ma position est connue
   **When** je demande un itinéraire
   **Then** DiscoveryRoutingEngine calcule un chemin privilégiant les segments non explorés

2. **And** le surplus de temps par rapport au chemin le plus court est maîtrisé (~15 % par défaut)

3. **And** le calcul se termine en moins de 5 secondes pour un trajet Paris typique (NFR-P2)

4. **And** l'itinéraire est retourné avec géométrie et ETA

## Tasks / Subtasks

- [x] Créer le modèle RouteResult et les types de données (AC: #4)
  - [x] Créer `routing/RouteResult.kt` — data class avec geometry (List<LatLng>), ETA (durée estimée en secondes), distance (mètres)
  - [x] Créer `routing/RoutingRequest.kt` — origin, destination, tolerancePercent (défaut 15)
- [x] Construire le graphe à partir des segments (AC: #1)
  - [x] Créer `routing/GraphBuilder.kt` — construit un graphe (nœuds = intersections, arêtes = segments) depuis les segments Room
  - [x] Extraire les coordonnées depuis geometry_json (format GeoJSON LineString)
  - [x] Calculer la longueur de chaque segment (Haversine ou approximation) pour les coûts
- [x] Implémenter DiscoveryRoutingEngine (AC: #1, #2, #3)
  - [x] Créer `routing/DiscoveryRoutingEngine.kt` — interface + implémentation
  - [x] Algorithme : Dijkstra/A* avec poids modifiés (segments explorés coûtent plus, non explorés moins)
  - [x] Paramètre tolérance : accepter un surplus max (ex. 15 %) par rapport au chemin le plus court
  - [x] Retourner RouteResult avec géométrie concaténée et ETA
  - [x] Exécuter le calcul sur Dispatchers.Default (pas le main thread)
- [x] Intégrer le moteur dans MapViewModel (AC: #1–4)
  - [x] Étendre MapUiState : `route: RouteResult?`, `isComputingRoute: Boolean`, `routeError: String?`
  - [x] MapViewModel : injecter DiscoveryRoutingEngine (ou RoutingRepository si abstraction)
  - [x] Méthode `onRequestRoute()` : appelle le moteur avec origin (userLocation), destination, tolérance 15 %
  - [x] Gérer les cas : pas de position, pas de destination, aucun chemin trouvé
- [x] Point d'entrée utilisateur pour demander l'itinéraire (AC: #1)
  - [x] Bouton ou action "Calculer itinéraire" visible quand destination est définie (ex. FAB, bouton dans SearchBar, ou icône direction)
  - [x] Au clic → `onRequestRoute()`
- [x] Tests unitaires (AC: #1, #2, #3)
  - [x] DiscoveryRoutingEngineTest : graphe simple, favorise segments non explorés, respecte tolérance
  - [x] GraphBuilderTest : construction correcte depuis segments
  - [x] MapViewModel : onRequestRoute met à jour route / routeError

## Dev Notes

### Developer Context

**Contexte Epic 2 :** Story 2.1 a livré la barre de recherche et le géocodage (destination dans MapUiState). La story 2.2 ajoute le **moteur de routing orienté découverte** — cœur métier de l'app. Les stories 2.3 (affichage itinéraire + ToleranceSlider) et 2.4 (suivi + fallback classique) s'appuient sur ce moteur.

**État du code après 2.1 :**
- `MapUiState` : `destination: LatLng?`, `userLocation: LatLng?`, segments avec état exploré
- `MapViewModel` : `onDestinationSelected()`, `SegmentRepository`, `GeocodingService`
- `SegmentRepository` : `segmentsWithExploredState: Flow<List<SegmentWithExploredState>>`, `markAsExplored`/`markAsUnexplored`
- `Segment` : `osm_way_id`, `geometry_json` (LineString GeoJSON)
- Pas encore de package `routing/` (vide ou .gitkeep)

**Contraintes clés :**
- **Offline-first** : tout le calcul se fait localement (pas d'API OSRM/GraphHopper externe). Stack 100 % open source.
- **Performance** : calcul < 5 s pour un trajet Paris typique (NFR-P2).
- **Tolérance** : surplus temps ~15 % par défaut (paramètre pour 2.3).

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| DiscoveryRoutingEngine | Interface + implémentation dans `routing/` |
| Entrée | Origin (LatLng), destination (LatLng), tolérance (%), Set<Long> des segment IDs explorés |
| Sortie | RouteResult(geometry: List<LatLng>, etaSeconds: Long, distanceMeters: Double) ou null si aucun chemin |
| Algorithme | Graphe construit depuis segments ; Dijkstra ou A* avec coûts : segment exploré = coût × 1.2, non exploré = coût × 0.9 ; contrainte max coût = shortest × (1 + tolerance/100) |
| GraphBuilder | Lit segments depuis SegmentRepository (ou liste injectée) ; nœuds = coordonnées uniques (extrémités) ; arêtes = segments avec osm_way_id, geometry, length |
| Tolérance | 15 % par défaut ; accepter routes jusqu'à 15 % plus longues que le chemin le plus court |
| Threading | `withContext(Dispatchers.Default)` pour le calcul ; résultat exposé via StateFlow |

### Architecture Compliance

**Structure packages (architecture.md) :**
```
app/src/main/java/com/parcoursparis/
├── routing/
│   ├── DiscoveryRoutingEngine.kt   # CRÉER — interface + impl
│   ├── DiscoveryRoutingEngineImpl.kt  # ou dans le même fichier
│   ├── RouteResult.kt             # CRÉER — geometry, ETA, distance
│   ├── RoutingRequest.kt          # CRÉER — origin, destination, tolerance
│   ├── GraphBuilder.kt            # CRÉER — construction graphe
│   └── (optionnel) RoutingGraph.kt # structure interne graphe
├── map/
│   ├── MapUiState.kt              # MODIFIER — route, isComputingRoute, routeError
│   ├── MapViewModel.kt            # MODIFIER — onRequestRoute(), injection engine
│   └── MapViewModelFactory.kt      # MODIFIER — fournir DiscoveryRoutingEngine
```

**Conventions (architecture.md) :**
- Routing ↔ Data : DiscoveryRoutingEngine reçoit les segments avec état exploré (Flow ou snapshot) ; pas d'accès direct à Room.
- Noms : `DiscoveryRoutingEngine`, `RouteResult`, `GraphBuilder` ; package `routing/`.

**Frontières :**
- Le moteur est une dépendance injectée dans MapViewModel (testable avec fake).
- Pas de RouteBottomSheet ni ToleranceSlider dans cette story (2.3).

### Library & Framework Requirements

**Aucune nouvelle dépendance externe requise.** Le routing est implémenté en Kotlin pur :
- Utiliser `kotlin.math` pour Haversine (distance entre 2 points)
- Coroutines : `Dispatchers.Default`, `withContext`, `suspend`
- Pas de GraphHopper, OSRM, ni API externe (offline-first)

**Format geometry_json (Segment) :**
- JSON array de [lon, lat] : `[[2.3522,48.8566],[2.353,48.857]]`
- Parser avec `JSONArray(segment.geometry_json)` (déjà utilisé dans Segment.validateGeometry)

### File Structure Requirements

| Fichier/Dossier | Action |
|-----------------|--------|
| `routing/RouteResult.kt` | CRÉER — data class (geometry, etaSeconds, distanceMeters) |
| `routing/RoutingRequest.kt` | CRÉER — origin, destination, tolerancePercent |
| `routing/GraphBuilder.kt` | CRÉER — build graph from segments |
| `routing/DiscoveryRoutingEngine.kt` | CRÉER — interface + impl |
| `map/MapUiState.kt` | MODIFIER — route, isComputingRoute, routeError |
| `map/MapViewModel.kt` | MODIFIER — onRequestRoute(), injection engine |
| `map/MapViewModelFactory.kt` | MODIFIER — fournir DiscoveryRoutingEngine |
| `map/MapScreen.kt` | MODIFIER — bouton/action "Calculer itinéraire" quand destination définie |
| `res/values/strings.xml` | MODIFIER — chaînes itinéraire, calcul en cours, erreur |

**Ne pas créer dans cette story :** RouteBottomSheet, ToleranceSlider, affichage du tracé sur la carte (2.3), fallback classique (2.4).

### Testing Requirements

- **Unit tests** : `DiscoveryRoutingEngineTest` — graphe avec 2 chemins (un via exploré, un via non exploré) → doit choisir le non exploré si dans la tolérance ; respect de la tolérance (pas de route > 15 % du plus court).
- **Unit tests** : `GraphBuilderTest` — segments connectés → graphe avec nœuds et arêtes corrects.
- **Unit tests** : `MapViewModelTest` — `onRequestRoute()` avec fake engine : route mise à jour ; pas de position → routeError ; pas de chemin → routeError.
- **Performance** : pour un jeu de données Paris réaliste (si disponible), vérifier que le calcul < 5 s.

### Previous Story Intelligence (2.1)

**2.1** : SearchBar, GeocodingService (Nominatim), MapUiState.destination, MapViewModel.onDestinationSelected. Pattern : injection de services dans ViewModel, StateFlow, méthodes `onXxx()`. FakeGeocodingService pour les tests. MapViewModelFactory reçoit Application + services.

**Convention** : étendre MapUiState et MapViewModel ; pas d'écran séparé pour le routing. Le bouton "Calculer itinéraire" peut être un FAB ou un bouton dans la SearchBar (quand destination affichée).

### Git Intelligence Summary

- Derniers commits : 2.1 (barre recherche, géocodage Nominatim). Package `routing/` vide ou absent.
- Patterns : Kotlin, Compose, ViewModel + StateFlow, injection via Factory.

### Latest Tech Information

**Algorithme discovery routing :**
- Poids modifiés : exploré = coût × 1.2, non exploré = coût × 0.9. L'algorithme favorise naturellement les chemins non explorés.
- Contrainte tolérance : calculer d'abord le chemin le plus court (Dijkstra classique), puis chercher un chemin discovery avec coût max = shortest × (1 + tolerance/100).
- Alternative : A* avec heuristique standard (distance à vol d'oiseau) et coûts modifiés ; pas de contrainte explicite si les poids suffisent à rester dans la tolérance.

**Haversine (distance approximative) :**
- `val R = 6371000.0` (mètres) ; `a = sin²(Δlat/2) + cos(lat1)*cos(lat2)*sin²(Δlon/2)` ; `c = 2*atan2(√a, √(1−a))` ; `d = R * c`

**Vitesse marche** : ~5 km/h ≈ 1.39 m/s → ETA = distanceMeters / 1.39

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 2 - Story 2.2 Moteur de routing orienté découverte]
- [Source: _bmad-output/planning-artifacts/architecture.md#routing/DiscoveryRoutingEngine.kt, GraphBuilder.kt]
- [Source: _bmad-output/planning-artifacts/architecture.md#Routing ↔ Data: DiscoveryRoutingEngine reads SegmentRepository]
- [Source: _bmad-output/planning-artifacts/prd.md#FR6, NFR-P2, Technical Success - 100% open source]
- [Source: _bmad-output/implementation-artifacts/2-1-barre-recherche-et-geocodage.md#MapUiState.destination, MapViewModel]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — routing/, DiscoveryRoutingEngine, GraphBuilder
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 2.2, FR6
- PRD : `_bmad-output/planning-artifacts/prd.md` — NFR-P2 (< 5 s), stack open source
- Story précédente : `_bmad-output/implementation-artifacts/2-1-barre-recherche-et-geocodage.md`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- Implémentation complète du moteur de routing discovery : RouteResult, RoutingRequest, GraphBuilder, DiscoveryRoutingEngine (Dijkstra avec poids modifiés : exploré × 1.2, non exploré × 0.9 ; tolérance 15 %).
- Intégration dans MapViewModel : onRequestRoute(), états route, isComputingRoute, routeError.
- FAB "Calculer itinéraire" visible quand destination définie ; affichage des erreurs (pas de position, pas de destination, aucun chemin).
- Tests unitaires : GraphBuilderTest, DiscoveryRoutingEngineTest, MapViewModelTest (onRequestRoute).

### File List

- app/src/main/java/com/parcoursparis/routing/RouteResult.kt (créé)
- app/src/main/java/com/parcoursparis/routing/RoutingRequest.kt (créé)
- app/src/main/java/com/parcoursparis/routing/RoutingGraph.kt (créé)
- app/src/main/java/com/parcoursparis/routing/GraphBuilder.kt (créé)
- app/src/main/java/com/parcoursparis/routing/DiscoveryRoutingEngine.kt (créé)
- app/src/main/java/com/parcoursparis/map/MapUiState.kt (modifié)
- app/src/main/java/com/parcoursparis/map/MapViewModel.kt (modifié)
- app/src/main/java/com/parcoursparis/map/MapViewModelFactory.kt (modifié)
- app/src/main/java/com/parcoursparis/map/MapScreen.kt (modifié)
- app/src/main/java/com/parcoursparis/ParcoursParisApplication.kt (modifié)
- app/src/main/res/values/strings.xml (modifié)
- app/src/test/java/com/parcoursparis/routing/FakeDiscoveryRoutingEngine.kt (créé)
- app/src/test/java/com/parcoursparis/routing/GraphBuilderTest.kt (créé)
- app/src/test/java/com/parcoursparis/routing/DiscoveryRoutingEngineTest.kt (créé)
- app/src/test/java/com/parcoursparis/map/MapViewModelTest.kt (modifié)
- app/src/test/java/com/parcoursparis/map/MapViewModelLocationTest.kt (modifié)

### Change Log

- Story 2.2 : moteur de routing orienté découverte (Date: 2026-02-26)
