# Story 1.5: Position GPS et gestion des permissions

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want voir ma position actuelle sur la carte lorsque le GPS est activé,
So que je sache où je me trouve pendant mes déplacements (FR4).

## Acceptance Criteria

1. **Given** l'app demande la permission de localisation
   **When** l'utilisateur accorde la permission
   **Then** la position GPS est affichée sur la carte en temps réel

2. **And** les permissions (localisation, stockage) sont demandées de façon explicite et justifiée (FR27, NFR-S2)

3. **And** si la permission est refusée, un message clair indique que la position ne sera pas affichée (sans bloquer l'app)

## Tasks / Subtasks

- [x] Déclarer les permissions dans AndroidManifest.xml (AC: #2)
  - [x] Ajouter `ACCESS_FINE_LOCATION` et `ACCESS_COARSE_LOCATION`
- [x] Créer `util/LocationUtils.kt` — Flow-based location provider (AC: #1)
  - [x] Wrapper `LocationManager` + `LocationListener` en `callbackFlow`
  - [x] Émettre `LatLng?` à chaque mise à jour GPS, null si unavailable
- [x] Étendre `MapUiState` avec la position utilisateur et le statut permission (AC: #1, #3)
  - [x] Champ `userLocation: LatLng?`
  - [x] Champ `locationPermissionGranted: Boolean`
- [x] Étendre `MapViewModel` pour collecter les mises à jour GPS (AC: #1)
  - [x] Recevoir `locationGranted: Boolean` en paramètre (ou via event)
  - [x] Lancer la collection Flow GPS quand la permission est accordée
  - [x] Mettre à jour `_uiState.userLocation` à chaque émission
- [x] Modifier `MapScreen` pour gérer la demande de permission (AC: #2, #3)
  - [x] `rememberLauncherForActivityResult(RequestMultiplePermissions)` pour demander `ACCESS_FINE_LOCATION`
  - [x] `LaunchedEffect` au démarrage : demande la permission si non accordée
  - [x] Transmettre `locationGranted` au ViewModel
  - [x] Afficher un bandeau/message si permission refusée (AC: #3)
- [x] Modifier `MapLibreMap` pour afficher la position GPS (AC: #1)
  - [x] Ajouter un paramètre `userLocation: LatLng?`
  - [x] Ajouter une `GeoJsonSource` dédiée à la position utilisateur
  - [x] Ajouter un `CircleLayer` ou `SymbolLayer` pour le marqueur de position
  - [x] Mettre à jour la source dynamiquement via `LaunchedEffect(userLocation)`
- [x] Modifier `MapViewModelFactory` pour accepter le contexte Application (AC: #1)
- [x] Tests unitaires : `MapViewModelLocationTest` (AC: #1)
- [ ] Tests manuels : position GPS visible sur la carte, message si refus (AC: #3)

## Dev Notes

### Developer Context

**Contexte Epic 1 :** Stories 1.1–1.4 ont créé le projet, le pipeline OSM (Room + SegmentRepository), MapLibre avec pan/zoom, et la couche de segments colorés (GeoJsonSource + 2 LineLayers). Cette story 1.5 est la **dernière story de l'Epic 1** : elle ajoute la localisation GPS en temps réel et la gestion des permissions Android (FR4, FR27, NFR-S2).

**État du code après 1.4 :**
- `MapViewModel` existe déjà — injecte `SegmentRepository`, expose `MapUiState(segments, isLoading, error)`
- `MapUiState` = `data class` avec `segments`, `isLoading`, `error`
- `MapScreen` utilise `viewModel(factory = MapViewModelFactory(repository))` — factory à étendre pour le contexte/location
- `MapLibreMap` : AndroidView + MapView, `setStyle` callback, 2 LineLayers (explored/unexplored), `GeoJsonSource` pour segments
- `AndroidManifest.xml` : uniquement `INTERNET` actuellement — à étendre
- `util/` package : vide, aucun fichier existant

**Contrainte architecture critique :** L'architecture prévoit explicitement `util/LocationUtils.kt` comme provider GPS, utilisé par `MapViewModel`. **Ne pas mettre la logique GPS directement dans MapViewModel ni MapScreen.**

**Stratégie permissions :** Utiliser `rememberLauncherForActivityResult` (activity-compose déjà en dépendance, v1.9.3). Pas de Accompanist ni de bibliothèque tierce nécessaire.

**Stratégie GPS :** Utiliser Android `LocationManager` (no proprietary APIs, conforme architecture "100% open source"). Wrapper en `callbackFlow` pour intégration Coroutines/Flow.

**Affichage position :** MapLibre 12.3.1 a un `LocationComponent` natif mais son API est complexe. Approche **simple et fiable recommandée** : ajouter une `GeoJsonSource` dédiée + `CircleLayer` pour le dot de position. Mise à jour via `LaunchedEffect(userLocation)` dans `MapLibreMap`.

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| Permissions manifeste | `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` |
| Runtime permission | `ActivityResultContracts.RequestMultiplePermissions()` ou `RequestPermission()` |
| LocationManager | `getSystemService(Context.LOCATION_SERVICE) as LocationManager` |
| GPS Provider | `LocationManager.GPS_PROVIDER` + fallback `NETWORK_PROVIDER` |
| Flow wrapper | `callbackFlow { LocationListener { loc -> trySend(loc) } }` |
| Marker position | `GeoJsonSource` point + `CircleLayer` (cercle bleu, rayon 8dp, outline blanc) |
| NFR-S2 | Message justification dans l'UI avant/pendant la demande de permission |
| NFR-S1 | Données GPS non persistées en base — seulement en mémoire (StateFlow) |

### Architecture Compliance

**Structure packages (architecture.md) :**
```
app/src/main/java/com/parcoursparis/
├── util/
│   └── LocationUtils.kt          # CRÉER — Flow<Location?> depuis LocationManager
├── map/
│   ├── MapUiState.kt             # MODIFIER — ajouter userLocation: LatLng?, locationPermissionGranted: Boolean
│   ├── MapViewModel.kt           # MODIFIER — collecter LocationUtils.locationFlow, exposer position
│   ├── MapViewModelFactory.kt    # MODIFIER — accepter Application context si besoin
│   ├── MapScreen.kt              # MODIFIER — demande permission, passer locationGranted + userLocation
│   └── MapLibreMap.kt            # MODIFIER — afficher le dot GPS (GeoJsonSource + CircleLayer)
```

**Conventions (architecture.md) :**
- `MapUiState`, `MapViewModel` — noms existants conservés, étendus
- Pas de classe `MapState` (clash Compose)
- `ViewModel exposes StateFlow<UiState>` — pattern déjà établi
- `LocationUtils` dans `util/` (cf. architecture.md "Cross-Cutting: Location/GPS: util/LocationUtils.kt")

**Data flow GPS :**
```
LocationManager (OS) → LocationUtils.locationFlow (Flow<Location?>) 
  → MapViewModel.collectLocation() → _uiState.userLocation (StateFlow)
  → MapScreen → MapLibreMap → CircleLayer mise à jour
```

**Ne pas créer :** aucun DAO/entity pour GPS (données non persistées), aucun nouveau ViewModel.

### Library & Framework Requirements

**Permission request (activity-compose 1.9.3 — déjà en dépendance) :**
```kotlin
// Dans MapScreen
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    viewModel.onLocationPermissionResult(granted)
}
LaunchedEffect(Unit) {
    permissionLauncher.launch(arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ))
}
```

**LocationUtils.kt — callbackFlow pattern :**
```kotlin
// util/LocationUtils.kt
fun locationFlow(context: Context): Flow<Location?> = callbackFlow {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { location -> trySend(location) }
    
    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
    
    if (provider != null) {
        try {
            locationManager.requestLocationUpdates(provider, 2000L, 5f, listener)
        } catch (e: SecurityException) {
            trySend(null)
        }
    } else {
        trySend(null)
    }
    
    awaitClose { locationManager.removeUpdates(listener) }
}
```

**MapViewModel — ajout location :**
```kotlin
fun onLocationPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(locationPermissionGranted = granted) }
    if (granted) {
        viewModelScope.launch {
            locationFlow(appContext)
                .collect { location ->
                    _uiState.update { state ->
                        state.copy(
                            userLocation = location?.let { 
                                LatLng(it.latitude, it.longitude) 
                            }
                        )
                    }
                }
        }
    }
}
```

**MapLibreMap — dot GPS (dans setStyle callback et LaunchedEffect) :**
```kotlin
private const val USER_LOCATION_SOURCE_ID = "user-location-source"
private const val USER_LOCATION_LAYER_ID = "user-location-layer"

// Dans setStyle callback (après les segment layers) :
val locationSource = GeoJsonSource(USER_LOCATION_SOURCE_ID, """{"type":"FeatureCollection","features":[]}""")
style.addSource(locationSource)
locationSourceRef.value = locationSource
val locationLayer = CircleLayer(USER_LOCATION_LAYER_ID, USER_LOCATION_SOURCE_ID)
    .withProperties(
        PropertyFactory.circleRadius(8f),
        PropertyFactory.circleColor(android.graphics.Color.parseColor("#2196F3")),  // Material Blue
        PropertyFactory.circleStrokeWidth(2f),
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
    )
style.addLayer(locationLayer)

// LaunchedEffect pour mise à jour position :
LaunchedEffect(userLocation) {
    locationSourceRef.value?.let { source ->
        val geoJson = if (userLocation != null) {
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${userLocation.longitude},${userLocation.latitude}]},"properties":{}}]}"""
        } else {
            """{"type":"FeatureCollection","features":[]}"""
        }
        source.setGeoJson(geoJson)
    }
}
```

**MapUiState — ajout champs :**
```kotlin
data class MapUiState(
    val segments: List<SegmentWithExploredState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userLocation: LatLng? = null,             // NOUVEAU
    val locationPermissionGranted: Boolean = false // NOUVEAU
)
```

### File Structure Requirements

| Fichier/Dossier | Action |
|-----------------|--------|
| `AndroidManifest.xml` | MODIFIER — ajouter `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| `util/LocationUtils.kt` | CRÉER — `fun locationFlow(context: Context): Flow<Location?>` |
| `map/MapUiState.kt` | MODIFIER — ajouter `userLocation: LatLng?`, `locationPermissionGranted: Boolean` |
| `map/MapViewModel.kt` | MODIFIER — `onLocationPermissionResult()`, collecte `locationFlow` |
| `map/MapViewModelFactory.kt` | MODIFIER si nécessaire pour passer Application context |
| `map/MapScreen.kt` | MODIFIER — launcher permission, bandeau si refusé, transmettre `userLocation` |
| `map/MapLibreMap.kt` | MODIFIER — param `userLocation: LatLng?`, source+layer GPS, `LaunchedEffect` |
| `res/values/strings.xml` | MODIFIER — messages permission justifiée + refus GPS |

**Ne pas créer :** `SegmentSelector` (story 3.x), `SearchBar` (story 2.x), aucun DAO GPS.

### Testing Requirements

- **Unit tests** : `MapViewModelLocationTest` — `onLocationPermissionResult(true)` lance la collecte, `onLocationPermissionResult(false)` ne lance pas la collecte, `userLocation` est mis à jour
- **Tests instrumentés** : Vérifier que `MapScreen` ne crash pas avec/sans permission accordée
- **Validation manuelle** : Dot bleu visible sur la carte avec GPS actif, message affiché si permission refusée, app fonctionnelle sans GPS

### Previous Story Intelligence (1.1–1.4)

**1.1** : Package `com.parcoursparis`, structure (data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/), Room 2.8.4, MapLibre 12.3.1, bottom nav Map|Profile|Settings.

**1.2** : `SegmentRepository.segmentsWithExploredState` = `Flow<List<SegmentWithExploredState>>`. `Segment.geometry_json` = JSON array de coordonnées. `GeoJsonLoader` charge `paris_segments.geojson` au démarrage.

**1.3** : `MapLibreMap` utilise `AndroidView + MapView`. Lifecycle via `DisposableEffect + LocalLifecycleOwner`. `getMapAsync { map -> map.setStyle(...) { style -> ... } }` = point d'injection pour sources et layers. Style URL : `demotiles.maplibre.org/style.json`.

**1.4** : `MapViewModel(segmentRepository)` + `MapUiState(segments, isLoading, error)`. `SegmentGeoJsonConverter` pour conversion. 2 LineLayers (`parcours-segments-explored` / `parcours-segments-unexplored`). `scope.launch + withContext(Dispatchers.Default)` pour la conversion hors main thread. `rememberUpdatedState(segments)` pour éviter la race condition dans le callback `setStyle`. `LaunchedEffect(segments)` pour mises à jour dynamiques. Bandeau d'erreur dans `MapScreen` si `uiState.error != null`.

**Problèmes rencontrés (1.4)** : Race condition callbacks async → `rememberUpdatedState`; mise à jour GeoJSON sur main thread → `withContext(Dispatchers.Default)`; LOD `setMinZoom` à appeler **après** `withProperties`.

**Point d'attention :** `MapViewModelFactory` actuellement accepte `repository: SegmentRepository`. Pour `LocationUtils.locationFlow(context)`, il faut un `Context` — passer `Application` context depuis `MapScreen` (déjà disponible via `LocalContext.current.applicationContext`).

### Git Intelligence Summary

- Commit 1.4 : `MapViewModel`, `MapUiState`, `SegmentGeoJsonConverter`, `MapLibreMap` (layers), `MapScreen` (ViewModel, error banner), `MapViewModelFactory`, `MapSegmentLayer` (optionnel), tests
- Commit 1.2/1.3 combinés : pipeline OSM + carte MapLibre avec pan/zoom
- Commit 1.1 : projet Android, dépendances, packages, navigation

**Patterns établis :**
- `scope = rememberCoroutineScope()` dans le Composable pour `scope.launch` dans callbacks AndroidView
- `rememberUpdatedState()` pour accéder aux dernières valeurs dans les callbacks async MapLibre
- `LaunchedEffect(key)` pour les mises à jour réactives des sources MapLibre
- `DisposableEffect(lifecycleOwner)` pour le lifecycle MapView

### Latest Tech Information

**Android Permissions Runtime (API 24+) :**
- `ActivityResultContracts.RequestMultiplePermissions()` est l'API moderne recommandée (activity-compose 1.9.3 déjà en dépendance)
- Vérifier le statut avant de relancer : `ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED`
- Sur Android 12+ (API 31+), `ACCESS_COARSE_LOCATION` suffit pour la localisation approximative si l'utilisateur refuse la précision

**LocationManager (Android natif, sans Google Play Services) :**
- `requestLocationUpdates(provider, minTimeMs, minDistanceM, listener)` — recommandé : 2000ms, 5m
- Sur Android 12+ (API 31) : utiliser `requestLocationUpdates(provider, request, executor, listener)` pour les background updates (pas nécessaire ici — foreground uniquement)
- `removeUpdates(listener)` dans `awaitClose` du `callbackFlow` (nettoyage essentiel)
- Toujours wrapper dans `try/catch(SecurityException)` même avec permission accordée

**MapLibre 12.3.1 — ajout source/layer position :**
- `CircleLayer` pour le dot de position : simple, pas de resource drawable nécessaire
- Ordre des layers : ajouter le layer position **après** les segment layers pour qu'il soit au-dessus
- `style.addLayerAbove(locationLayer, "parcours-segments-unexplored")` ou simplement `style.addLayer(locationLayer)` en dernier

**callbackFlow (Kotlin Coroutines) :**
- `trySend(value)` — non-suspending, sûr depuis les callbacks
- `awaitClose { ... }` — obligatoire pour libérer les ressources
- Ne jamais utiliser `send()` depuis un LocationListener (suspending, peut bloquer)

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.5 - Position GPS et gestion des permissions]
- [Source: _bmad-output/planning-artifacts/architecture.md#Cross-Cutting: Location/GPS: util/LocationUtils.kt]
- [Source: _bmad-output/planning-artifacts/architecture.md#Authentication & Security — Permissions explicites NFR-S2]
- [Source: _bmad-output/planning-artifacts/prd.md#FR4, FR27, NFR-S2]
- [Source: _bmad-output/implementation-artifacts/1-4-couche-segments-colores-avec-lod.md#Dev Notes]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — `util/LocationUtils.kt`, permissions NFR-S2, structure packages
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 1.5 acceptance criteria, FR4, FR27
- UX : `_bmad-output/planning-artifacts/ux-design-specification.md` — outdoor readability, 48dp touch targets, message clair si erreur
- PRD : `_bmad-output/planning-artifacts/prd.md` — FR4 (GPS sur carte), FR27 (gestion permissions), NFR-S2 (permissions explicites)
- Story précédente : `_bmad-output/implementation-artifacts/1-4-couche-segments-colores-avec-lod.md` — patterns MapLibre établis

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- Implémentation complète : permissions AndroidManifest, LocationUtils (callbackFlow), MapUiState/MapViewModel étendus, MapScreen (permission launcher + bandeau refus), MapLibreMap (GeoJsonSource + CircleLayer pour position GPS). Tests unitaires MapViewModelLocationTest avec Robolectric.

### File List

- app/src/main/AndroidManifest.xml
- app/src/main/java/com/parcoursparis/util/LocationUtils.kt
- app/src/main/java/com/parcoursparis/map/MapUiState.kt
- app/src/main/java/com/parcoursparis/map/MapViewModel.kt
- app/src/main/java/com/parcoursparis/map/MapViewModelFactory.kt
- app/src/main/java/com/parcoursparis/map/MapScreen.kt
- app/src/main/java/com/parcoursparis/map/MapLibreMap.kt
- app/src/main/res/values/strings.xml
- app/src/test/java/com/parcoursparis/map/MapViewModelTest.kt
- app/src/test/java/com/parcoursparis/map/MapViewModelLocationTest.kt

### Change Log

- 2026-02-24 : Story 1.5 implémentée — position GPS, permission launcher, bandeau refus, tests unitaires
- 2026-02-24 : Code review — 6 issues corrigées (2 HIGH, 4 MEDIUM) : fuite coroutines locationJob, dialog rationale NFR-S2, catch sur locationFlow, race condition GPS style load, API LocationManager 31+, test multi-appels
