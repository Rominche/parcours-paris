# Story 2.3: Affichage de l'itinéraire et paramètre de tolérance

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want voir l'itinéraire tracé sur la carte et ajuster la tolérance de surplus de temps,
so that je puisse obtenir un itinéraire adapté si le premier ne me convient pas (FR7, FR10, FR26).

## Acceptance Criteria

1. **Given** un itinéraire a été calculé
   **When** il est affiché
   **Then** le tracé apparaît sur la carte (couleur accent)

2. **And** un bottom sheet affiche ETA et un ToleranceSlider (10–25 %)

3. **And** en ajustant la tolérance, je peux relancer le calcul

4. **And** le paramètre est persisté (UserPreference)

## Tasks / Subtasks

- [x] Afficher le tracé de l'itinéraire sur la carte (AC: #1)
  - [x] Étendre MapLibreMap : paramètre `route: RouteResult?`
  - [x] Ajouter GeoJsonSource + LineLayer pour la route (couleur accent, ex. #2196F3 ou MaterialTheme.primary)
  - [x] Convertir RouteResult.geometry en GeoJSON LineString
  - [x] Ordre des layers : segments → route → userLocation (route visible au-dessus)
- [x] Créer RouteBottomSheet (AC: #2)
  - [x] Créer `map/component/RouteBottomSheet.kt` — ModalBottomSheet avec ETA formaté (ex. "~12 min") et distance (km)
  - [x] Intégrer ToleranceSlider (10–25 %) — architecture : `ui/component/ToleranceSlider.kt`
  - [x] Afficher le bottom sheet quand `route != null`
- [x] Créer ToleranceSlider (AC: #2)
  - [x] Créer `ui/component/ToleranceSlider.kt` — Slider Material 3, valeurs 10–25, label clair ("Surplus temps max : X %")
  - [x] Exposer `value: Int`, `onValueChange: (Int) -> Unit`
- [x] Relancer le calcul à l'ajustement de la tolérance (AC: #3)
  - [x] MapViewModel : `onToleranceChanged(newValue: Int)` → met à jour état + appelle `onRequestRoute()` avec nouvelle tolérance
  - [x] RoutingRequest : utiliser `tolerancePercent` depuis l'état (ou préférence)
- [x] Persister le paramètre de tolérance (AC: #4)
  - [x] Option A : DataStore Preferences (recommandé MVP) — clé `tolerance_percent`, valeur Int 10–25
  - [ ] Option B : Room UserPreference — entity `user_preference` (key, value), migration DB v1→v2
  - [x] Charger la tolérance au démarrage ; l'utiliser dans RoutingRequest ; sauvegarder à chaque changement
- [x] Tests (AC: #1–4)
  - [x] MapLibreMap : route null → pas de layer route ; route non null → layer affiché
  - [x] RouteBottomSheet : affiche ETA et ToleranceSlider
  - [x] MapViewModel : onToleranceChanged → onRequestRoute avec nouvelle tolérance
  - [x] Persistance : valeur sauvegardée et rechargée au redémarrage

## Dev Notes

### Developer Context

**Contexte Epic 2 :** Story 2.1 (barre recherche, géocodage) et 2.2 (moteur routing discovery) sont terminées. La story 2.3 ajoute l'**affichage visuel de l'itinéraire** sur la carte et le **paramètre de tolérance** ajustable via un bottom sheet. La story 2.4 (suivi + fallback classique) s'appuiera sur ce tracé.

**État du code après 2.2 :**
- `MapUiState` : `route: RouteResult?`, `isComputingRoute`, `routeError` ; `destination`, `userLocation`
- `MapViewModel` : `onRequestRoute()` appelle DiscoveryRoutingEngine avec `RoutingRequest(origin, destination, tolerancePercent = 15.0)` (valeur en dur)
- `MapLibreMap` : affiche segments (LineLayers) et userLocation (CircleLayer) ; **pas de layer pour la route**
- `MapScreen` : FAB "Calculer itinéraire" quand destination définie ; affichage routeError en bandeau
- `RoutingRequest` : `tolerancePercent: Double = 15.0` (non persisté)
- Pas de RouteBottomSheet, ToleranceSlider, ni persistance tolérance

**Contraintes clés :**
- **UX** : Bottom sheet pour détails itinéraire (ux-design-specification.md) ; ToleranceSlider 10–25 % (RouteSummaryCard, ToleranceSlider)
- **Couleur** : tracé en couleur accent (distinct des segments vert/gris)
- **Performance** : pas de freeze (NFR-P1)

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| Tracé carte | GeoJsonSource + LineLayer MapLibre ; geometry = RouteResult.geometry → GeoJSON LineString |
| Couleur route | Accent (ex. #2196F3) ou `MaterialTheme.colorScheme.primary` ; lineWidth 4–5f |
| RouteBottomSheet | ModalBottomSheet ; ETA formaté (etaSeconds → "~X min") ; distance en km ; ToleranceSlider |
| ToleranceSlider | Slider 10–25, step 1 ou 5 ; label "Surplus temps max : X %" |
| Relance calcul | `onToleranceChanged(value)` → update state → `onRequestRoute()` avec `RoutingRequest(..., tolerancePercent = value.toDouble())` |
| Persistance | DataStore Preferences (clé `tolerance_percent`) ou Room UserPreference ; charger au init ViewModel |
| RoutingRequest | MapViewModel doit passer la tolérance depuis l'état (ou préférence) au lieu de 15.0 en dur |

### Architecture Compliance

**Structure packages (architecture.md) :**
```
app/src/main/java/com/parcoursparis/
├── map/
│   ├── MapLibreMap.kt           # MODIFIER — paramètre route, layer route
│   ├── MapScreen.kt              # MODIFIER — RouteBottomSheet quand route != null
│   ├── MapViewModel.kt           # MODIFIER — tolerancePercent depuis préférence, onToleranceChanged
│   ├── MapUiState.kt             # MODIFIER — tolerancePercent: Int (optionnel, pour UI)
│   └── component/
│       └── RouteBottomSheet.kt   # CRÉER — bottom sheet ETA + ToleranceSlider
├── ui/
│   └── component/
│       └── ToleranceSlider.kt    # CRÉER — Slider 10–25
├── data/
│   └── (DataStore ou UserPreference) # Persistance tolérance
```

**Conventions :**
- MapLibre : LineLayer pour route ; source ID dédié (ex. `route-source`) ; layer au-dessus des segments, sous userLocation
- Bottom sheet : `ModalBottomSheet` Material 3 ; `rememberModalBottomSheetState`
- Noms : `RouteBottomSheet`, `ToleranceSlider` (PascalCase Composables)

### Library & Framework Requirements

**MapLibre (déjà présent) :**
- GeoJSON LineString : `{"type":"LineString","coordinates":[[lon,lat],[lon,lat],...]}`
- LineLayer : `PropertyFactory.lineColor()`, `PropertyFactory.lineWidth(4f)`

**DataStore (si Option A) :**
- `androidx.datastore:datastore-preferences` ; `intPreferencesKey("tolerance_percent")`
- Valeur par défaut 15 ; sauvegarder à chaque `onToleranceChanged`

**Room UserPreference (si Option B) :**
- Entity : `@Entity(tableName = "user_preference")` avec `key: String`, `value: String`
- DAO : `@Query` get/insert ; migration v1→v2

### File Structure Requirements

| Fichier/Dossier | Action |
|-----------------|--------|
| `map/MapLibreMap.kt` | MODIFIER — paramètre `route: RouteResult?`, GeoJsonSource + LineLayer route |
| `map/component/RouteBottomSheet.kt` | CRÉER — bottom sheet ETA, distance, ToleranceSlider |
| `ui/component/ToleranceSlider.kt` | CRÉER — Slider 10–25, label |
| `map/MapScreen.kt` | MODIFIER — passer route à MapLibreMap ; afficher RouteBottomSheet si route != null |
| `map/MapViewModel.kt` | MODIFIER — tolérance depuis préférence ; onToleranceChanged ; RoutingRequest avec tolérance |
| `map/MapUiState.kt` | MODIFIER — tolerancePercent: Int (pour affichage slider) |
| `data/` (DataStore ou UserPreference) | CRÉER — persistance tolérance |
| `res/values/strings.xml` | MODIFIER — chaînes ETA, tolérance, bottom sheet |

**Ne pas créer dans cette story :** Suivi GPS le long de l'itinéraire, fallback classique (2.4).

### Testing Requirements

- **Unit tests** : MapViewModel — `onToleranceChanged` met à jour tolérance et déclenche recalcul ; persistance rechargée au init.
- **Unit tests** : ToleranceSlider — valeur 10–25, callback onValueChange.
- **UI tests** (optionnel) : RouteBottomSheet affiché quand route calculée ; slider modifiable.

### Previous Story Intelligence (2.2)

**2.2** : DiscoveryRoutingEngine, RouteResult, GraphBuilder, RoutingRequest. MapViewModel.onRequestRoute() avec tolerancePercent = 15 en dur. FAB "Calculer itinéraire" visible quand destination définie. Pattern : étendre MapUiState/MapViewModel, pas d'écran séparé. Code review fixes : GeoUtils.haversineMeters, PriorityQueue dans Dijkstra, return@withContext.

**Convention** : MapLibreMap reçoit les données en paramètres ; LaunchedEffect pour mises à jour (segments, userLocation). Même pattern pour route : LaunchedEffect(route) pour mettre à jour le GeoJsonSource.

### Git Intelligence Summary

- Derniers commits : 2.2 (moteur routing, FAB, MapViewModel onRequestRoute). Pas de RouteBottomSheet ni layer route.
- Patterns : Kotlin, Compose, ViewModel + StateFlow, MapLibre GeoJsonSource + LineLayer.

### Latest Tech Information

**MapLibre LineLayer (route) :**
- Source : GeoJsonSource avec LineString ; coordinates = [[lon, lat], ...] (GeoJSON standard)
- LineLayer : lineColor (accent), lineWidth 4–5, lineJoin ROUND pour lisser les angles

**DataStore Preferences :**
- `context.dataStore.edit { it[intPreferencesKey("tolerance_percent")] = value }`
- `context.dataStore.data.map { prefs -> prefs[intPreferencesKey("tolerance_percent")] ?: 15 }`

**ModalBottomSheet :**
- `ModalBottomSheet(onDismissRequest = {...})` ; `SheetValue.PartiallyExpanded` ou `Expanded` pour hauteur

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 2 - Story 2.3 Affichage itinéraire et paramètre tolérance]
- [Source: _bmad-output/planning-artifacts/architecture.md#map/component/RouteBottomSheet.kt, ui/component/ToleranceSlider.kt]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#RouteSummaryCard, ToleranceSlider, Bottom sheet]
- [Source: _bmad-output/implementation-artifacts/2-2-moteur-routing-oriente-decouverte.md#MapViewModel, RouteResult, RoutingRequest]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — map/component/, ui/component/
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 2.3, FR7, FR10, FR26
- UX : `_bmad-output/planning-artifacts/ux-design-specification.md` — RouteSummaryCard, ToleranceSlider
- Story précédente : `_bmad-output/implementation-artifacts/2-2-moteur-routing-oriente-decouverte.md`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- MapLibreMap : paramètre `route: RouteResult?`, GeoJsonSource + LineLayer route (couleur #2196F3, lineWidth 4f). LaunchedEffect(route) et initialisation dans setStyle callback.
- RouteBottomSheet : ModalBottomSheet avec ETA formaté (etaSeconds → "~X min"), distance en km, ToleranceSlider intégré.
- ToleranceSlider : Slider 10–25, step 1, label "Surplus temps max : X %".
- MapViewModel : UserPreferencesRepository injecté ; collect tolerancePercent au init ; onToleranceChanged → debounce 300 ms + coerceIn(10,25) + setTolerancePercent + onRequestRoute. computeRouteJob/toleranceDebounceJob séparés pour éviter l'auto-annulation.
- DataStore : DataStoreUserPreferencesRepository avec clé `tolerance_percent`, valeur par défaut 15.
- MapUiState : showRouteBottomSheet géré dans le ViewModel (non dans l'UI locale) pour éviter la réouverture involontaire.
- GeoUtils : haversineMeters extrait dans util/GeoUtils.kt (refactoring depuis DiscoveryRoutingEngine).
- Tests : MapViewModelTest (onToleranceChanged avec vérification debounce, toleranceLoadedFromPreferencesOnInit corrigé pour créer le VM après la préférence), MapViewModelLocationTest mis à jour.

### File List

- app/src/main/java/com/parcoursparis/map/MapLibreMap.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapScreen.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapViewModel.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapUiState.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapViewModelFactory.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/component/RouteBottomSheet.kt (CRÉER)
- app/src/main/java/com/parcoursparis/ui/component/ToleranceSlider.kt (CRÉER)
- app/src/main/java/com/parcoursparis/data/preferences/UserPreferencesRepository.kt (CRÉER)
- app/src/main/java/com/parcoursparis/data/preferences/DataStoreUserPreferencesRepository.kt (CRÉER)
- app/src/main/java/com/parcoursparis/ParcoursParisApplication.kt (MODIFIER)
- app/src/main/res/values/strings.xml (MODIFIER)
- app/build.gradle.kts (MODIFIER)
- gradle/libs.versions.toml (MODIFIER)
- app/src/test/java/com/parcoursparis/data/preferences/FakeUserPreferencesRepository.kt (CRÉER)
- app/src/test/java/com/parcoursparis/map/MapViewModelTest.kt (MODIFIER)
- app/src/test/java/com/parcoursparis/map/MapViewModelLocationTest.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/util/GeoUtils.kt (CRÉER — refactoring haversineMeters depuis DiscoveryRoutingEngine)
- app/src/main/java/com/parcoursparis/routing/DiscoveryRoutingEngine.kt (MODIFIER — utilise GeoUtils.haversineMeters)
- app/src/main/java/com/parcoursparis/routing/GraphBuilder.kt (MODIFIER — utilise GeoUtils.haversineMeters)
- app/src/test/java/com/parcoursparis/routing/DiscoveryRoutingEngineTest.kt (MODIFIER — suite au refactoring GeoUtils)
