# Story 1.4: Couche de segments colorés avec LOD

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want voir les segments de rues colorés (vert = parcouru, gris = non parcouru) sur la carte,
So que je visualise ma progression de découverte (FR1, FR2).

## Acceptance Criteria

1. **Given** la carte est affichée et les données segments sont chargées
2. **When** je regarde la carte
3. **Then** les segments sont affichés avec vert pour parcouru, gris pour non parcouru
4. **And** le niveau de détail (LOD) s'adapte au niveau de zoom (artères en dézoom, détails en zoom)
5. **And** le rendu reste fluide lors du zoom/pan (NFR-P1)
6. **And** les segments non encore parcourus sont tous en gris par défaut

## Tasks / Subtasks

- [x] Créer MapSegmentLayer ou intégrer la couche segments dans MapLibreMap (AC: #3)
  - [x] Convertir SegmentWithExploredState en GeoJSON FeatureCollection
  - [x] Ajouter GeoJsonSource + LineLayer dans le callback setStyle
  - [x] Couleurs : vert (#4CAF50 ou Material green) pour parcouru, gris (#9E9E9E) pour non parcouru
- [x] Implémenter le LOD adaptatif (AC: #4)
  - [x] Stratégie : minzoom/maxzoom sur couches OU filtrage segments selon zoom
  - [x] Dézoom : artères principales (segments longs) ; zoom : tous les détails
- [x] Connecter MapScreen à SegmentRepository via MapViewModel (AC: #1, #6)
  - [x] MapViewModel collecte segmentsWithExploredState
  - [x] Passe les données à MapLibreMap ou MapSegmentLayer
- [x] Vérifier rendu fluide (NFR-P1) (AC: #5)
  - [x] Pas de travail lourd sur main thread
  - [x] Mise à jour GeoJsonSource efficace (éviter recréation complète à chaque changement)

## Dev Notes

### Developer Context

**Contexte Epic 1 :** Carte de Paris et visualisation de la progression. Les stories 1.1–1.3 ont créé le projet, le pipeline OSM (Room + SegmentRepository), et l'intégration MapLibre avec pan/zoom. Cette story 1.4 ajoute la couche de segments colorés par-dessus la carte. La story 1.5 ajoutera le GPS.

**Story précédente (1.3) :** MapLibreMap.kt affiche la carte avec MapView, style demotiles.maplibre.org, Paris centré (48.8566, 2.3522), zoom 11.5. MapScreen appelle MapLibreMap directement. Aucune connexion à SegmentRepository. Le callback `map.setStyle(Style.Builder().fromUri(...)) { style -> ... }` est le point d'injection pour ajouter des sources et layers.

**Points critiques à ne pas manquer :**
- SegmentRepository.segmentsWithExploredState expose déjà Flow<List<SegmentWithExploredState>> — l'utiliser, ne pas recréer
- Segment.geometry_json contient les coordonnées LineString (format JSON array)
- MapLibre : GeoJsonSource + LineLayer. Pour couleurs par segment : soit 2 sources (explored/unexplored), soit 1 source avec propriété `isExplored` et expression data-driven
- LOD : UX dit "artères en dézoom, détails en zoom" — filtrer ou simplifier selon zoom level
- NFR-P1 : éviter recalculs sur main thread ; préférer collecte Flow dans ViewModelScope

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| MapLibre | 12.3.1 (déjà en dépendance) |
| GeoJsonSource | FeatureCollection avec geometry LineString, properties isExplored |
| LineLayer | lineColor via PropertyFactory ou expression ["match", ["get", "isExplored"], true, "#4CAF50", "#9E9E9E"] |
| Couleurs | Vert parcouru : #4CAF50 (Material Green 500) ; Gris non parcouru : #9E9E9E (Material Grey 500) |
| LOD | Adapter visibilité ou densité selon map.cameraPosition.zoom |
| NFR-P1 | Rendu fluide — Flow collect dans ViewModel, mise à jour source asynchrone |

### Architecture Compliance

**Structure packages** (architecture.md) :
```
app/src/main/java/com/parcoursparis/
├── map/
│   ├── MapScreen.kt          # Modifier : injecter MapViewModel, passer segments
│   ├── MapViewModel.kt       # Créer : collecte segmentsWithExploredState
│   ├── MapUiState.kt         # Créer : segments, isLoading, error
│   ├── MapLibreMap.kt        # Modifier : accepter segments en param, ajouter GeoJsonSource+LineLayer
│   └── layer/
│       └── MapSegmentLayer.kt  # Optionnel : extraire logique couche segments
```

**Conventions** (architecture.md) :
- MapViewModel, MapUiState (pas MapState)
- SegmentRepository injecté via Hilt ou constructeur
- UiState : data, loading, error

**Data flow** (architecture.md) :
- MapViewModel → SegmentRepository.segmentsWithExploredState
- MapViewModel expose MapUiState(segments, isLoading, error)
- MapScreen/MapLibreMap reçoit segments et les affiche via GeoJsonSource

### Library & Framework Requirements

**MapLibre GeoJsonSource + LineLayer** :
```kotlin
// Dans le callback setStyle (après style chargé)
val featureCollection = buildFeatureCollection(segmentsWithExploredState)
style.addSource(GeoJsonSource(SEGMENTS_SOURCE_ID, featureCollection))
style.addLayer(
    LineLayer(SEGMENTS_LAYER_ID, SEGMENTS_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(Color.parseColor("#9E9E9E")), // ou expression data-driven
            PropertyFactory.lineWidth(3f)
        )
)
```

**Conversion Segment → GeoJSON Feature** :
- Chaque Segment a geometry_json (array of [lon, lat])
- Créer Feature avec geometry LineString, properties {"isExplored": true/false}
- FeatureCollection = type "FeatureCollection", features = [Feature, ...]

**Mise à jour dynamique** : GeoJsonSource.setGeoJson(featureCollection) quand segmentsWithExploredState change. Appeler depuis le main thread (MapLibre exige UI thread pour style updates).

### File Structure Requirements

| Fichier/Dossier | Action |
|-----------------|--------|
| `map/MapViewModel.kt` | Créer — inject SegmentRepository, collect segmentsWithExploredState |
| `map/MapUiState.kt` | Créer — data class (segments, isLoading, error) |
| `map/MapScreen.kt` | Modifier — ViewModel, passer segments à MapLibreMap |
| `map/MapLibreMap.kt` | Modifier — param segments, ajouter GeoJsonSource+LineLayer dans setStyle callback |
| `map/layer/MapSegmentLayer.kt` | Optionnel — extraire logique si MapLibreMap devient trop chargé |
| `ui/theme/Color.kt` | Vérifier — couleurs vert/gris sémantiques (parcouru/non parcouru) |

**Ne pas créer** : SegmentSelector (story 3.x), SearchBar (story 2.x).

### Testing Requirements

- **Unit tests** : MapViewModel — vérifier que segmentsWithExploredState est collecté et exposé
- **Instrumented tests** : MapScreenTest — vérifier présence de segments colorés sur la carte (ou au moins pas de crash)
- **Validation manuelle** : Segments visibles vert/gris, LOD adaptatif au zoom, pas de freeze (NFR-P1)

### Previous Story Intelligence (1.1, 1.2, 1.3)

**1.1** : Structure packages, Room 2.8.4, MapLibre 12.3.1, bottom nav Map|Profile|Settings. Package com.parcoursparis.

**1.2** : SegmentRepository.segmentsWithExploredState : Flow combinant SegmentDao.getAll() et SegmentVisitDao. Segment.geometry_json = JSON array de coordonnées. SegmentVisit vide au départ → tous segments isExplored=false. GeoJsonLoader charge paris_segments.geojson au démarrage.

**1.3** : MapLibreMap utilise AndroidView + MapView. Lifecycle via DisposableEffect + LocalLifecycleOwner. setStyle callback : c'est là qu'ajouter GeoJsonSource et LineLayer. MapScreen actuellement sans ViewModel — à ajouter. Style URL : demotiles.maplibre.org/style.json.

**Problèmes rencontrés (1.3)** : MapView lifecycle obligatoire ; contentDescription pour TalkBack ; gestion erreur chargement style.

### Git Intelligence Summary

- Derniers commits : 1.3 (MapLibre pan/zoom), 1.2 (pipeline OSM), 1.1 (projet)
- MapLibreMap.kt : getMapAsync { map -> map.setStyle(...) { style -> ... } }
- SegmentRepository : segmentsWithExploredState = combine(segmentDao.getAll(), segmentVisitDao.getAll())

### Latest Tech Information (Web Research)

- **MapLibre GeoJsonSource** : Constructeur GeoJsonSource(id, FeatureCollection). setGeoJson() pour mise à jour. FeatureCollection = JSONObject avec type "FeatureCollection", features = JSONArray.
- **LineLayer data-driven** : PropertyFactory.lineColor() accepte des expressions. ["match", ["get", "isExplored"], true, "#4CAF50", "#9E9E9E"] pour colorer selon propriété.
- **LOD MapLibre** : LineLayer.withMinZoom() / withMaxZoom() pour afficher à certains niveaux. Alternative : 2 LineLayers (artères vs détails) avec minzoom/maxzoom différents.
- **Performance** : Éviter de recréer toute la FeatureCollection à chaque emission Flow si possible ; ou throttling/debounce des mises à jour.

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.4]
- [Source: _bmad-output/planning-artifacts/architecture.md#Project Structure, Map ↔ Data]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#MapSegmentLayer, Couleurs sémantiques]
- [Source: _bmad-output/planning-artifacts/prd.md#FR1, FR2, NFR-P1]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — map/layer/MapSegmentLayer.kt, Map ↔ Data
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 1.4 acceptance criteria
- UX : `_bmad-output/planning-artifacts/ux-design-specification.md` — vert=parcouru, gris=non parcouru, MapSegmentLayer
- PRD : `_bmad-output/planning-artifacts/prd.md` — FR1, FR2, NFR-P1

## Change Log

- 2026-02-24 : Story 1.4 implémentée — couche segments colorés (vert/gris), MapViewModel, GeoJsonSource, LineLayers, tests unitaires
- 2026-02-24 : Code review — correctifs : LOD setMinZoom unexploredLayer (AC#4), race condition currentSegments+scope.launch, conversion GeoJSON hors main thread, try-catch JSONException par segment, affichage erreur MapScreen

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- MapViewModel + MapUiState créés, collecte segmentsWithExploredState via SegmentRepository
- SegmentGeoJsonConverter : conversion en GeoJSON FeatureCollection (JSON string) avec try-catch JSONException par segment
- MapLibreMap : GeoJsonSource + 2 LineLayers (explored/unexplored) avec filtres Expression.eq
- Couleurs : #4CAF50 (vert) parcouru, #9E9E9E (gris) non parcouru
- LOD : unexploredLayer.setMinZoom(12f) — rues grises visibles à zoom ≥ 12, segments verts toujours visibles (aperçu progression en dézoom)
- NFR-P1 : conversion GeoJSON via scope.launch + withContext(Dispatchers.Default) depuis callback setStyle — pas de blocage main thread
- Race condition corrigée : rememberUpdatedState(segments) + currentSegments.value dans setStyle callback
- MapScreen : bandeau d'erreur (errorContainer) si uiState.error != null
- Tests : MapViewModelTest, SegmentGeoJsonConverterTest (Robolectric pour org.json)
- Tests instrumentés : MapScreenTest (androidTest)

### File List

- app/src/main/java/com/parcoursparis/map/MapUiState.kt (créé)
- app/src/main/java/com/parcoursparis/map/MapViewModel.kt (créé)
- app/src/main/java/com/parcoursparis/map/MapViewModelFactory.kt (créé)
- app/src/main/java/com/parcoursparis/map/SegmentGeoJsonConverter.kt (créé)
- app/src/main/java/com/parcoursparis/map/MapLibreMap.kt (modifié)
- app/src/main/java/com/parcoursparis/map/MapScreen.kt (modifié)
- app/src/main/java/com/parcoursparis/ui/theme/Color.kt (modifié)
- app/src/main/java/com/parcoursparis/ParcoursParisApplication.kt (modifié - imports inutilisés)
- app/build.gradle.kts (modifié - lifecycle-viewmodel-compose)
- gradle/libs.versions.toml (modifié)
- app/src/test/java/com/parcoursparis/map/MapViewModelTest.kt (créé)
- app/src/test/java/com/parcoursparis/map/SegmentGeoJsonConverterTest.kt (créé)
- app/src/test/java/com/parcoursparis/util/MainDispatcherRule.kt (créé)
- app/src/androidTest/java/com/parcoursparis/map/MapScreenTest.kt (créé)
- app/src/main/AndroidManifest.xml (modifié)
- app/src/main/res/values/strings.xml (modifié)
- _bmad-output/implementation-artifacts/sprint-status.yaml (modifié)
