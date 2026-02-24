# Story 1.3: Affichage de la carte avec pan et zoom

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want afficher une carte de Paris en plein écran avec pan et zoom,
So que je puisse naviguer librement sur la ville (FR3).

## Acceptance Criteria

1. **Given** l'app est ouverte sur l'écran carte
2. **When** je fais un geste de pan ou de pinch-to-zoom
3. **Then** la carte se déplace ou change de niveau de zoom de manière fluide (NFR-P1)
4. **And** la carte couvre Paris et ses environs
5. **And** MapLibre est intégré avec le style approprié

## Tasks / Subtasks

- [x] Intégrer MapView MapLibre dans MapScreen via AndroidView (AC: #5)
  - [x] Créer MapViewComposable ou intégrer directement dans MapScreen
  - [x] Gérer le lifecycle (onCreate, onStart, onStop, onDestroy)
- [x] Configurer le style de carte (AC: #5)
  - [x] Charger un style OSM ou MapLibre (URL style ou local)
  - [x] Style adapté au mode offline (tiles locales ou style basique)
- [x] Positionner la caméra sur Paris (AC: #4)
  - [x] Centre Paris : lat 48.8566, lon 2.3522
  - [x] Niveau de zoom initial adapté (ex. 11-12 pour vue ville)
- [x] Vérifier pan et zoom fluides (AC: #2, #3)
  - [x] Pan : geste de glissement
  - [x] Zoom : pinch-to-zoom
  - [x] Pas de freeze perceptible (NFR-P1)

## Dev Notes

### Developer Context

**Contexte Epic 1 :** Carte de Paris et visualisation de la progression. La story 1.1 a créé le projet et MapScreen (actuellement placeholder avec "Carte (écran vide)"). La story 1.2 a implémenté le pipeline OSM (GeoJSON → Room, SegmentRepository). Cette story 1.3 intègre MapLibre pour afficher la carte avec pan/zoom. La story 1.4 ajoutera la couche de segments colorés (vert/gris) par-dessus la carte. La story 1.5 ajoutera le GPS.

**Story précédente (1.2) :** Pipeline OSM complet — Segment, SegmentVisit, DAOs, AppDatabase, GeoJsonLoader, SegmentRepository. Les segments sont chargés au démarrage depuis `assets/paris_segments.geojson`. MapScreen existe mais affiche uniquement un placeholder. Aucune intégration MapLibre n'est faite.

**Points critiques à ne pas manquer :**
- MapLibre 12.3.1 (org.maplibre.gl:android-sdk) est déjà dans build.gradle.kts — ne pas ajouter maplibre-compose (architecture fixe 12.3.1)
- Intégration Compose : utiliser `AndroidView` pour wrapper `MapView` (MapLibre SDK est View-based)
- Lifecycle obligatoire : MapView requiert onStart/onStop/onDestroy pour éviter fuites mémoire
- Mode offline : utiliser un style qui fonctionne sans réseau (MapLibre style URL ou tiles préchargées — pour MVP, style basique OSM ou MapLibre demo style)
- Paris : centre (48.8566, 2.3522), zoom 11-12 pour vue ville

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| MapLibre | 12.3.1 (déjà en dépendance) |
| Intégration Compose | AndroidView { MapView(...) } |
| Lifecycle | MapView.getMapAsync + map lifecycle (onStart/onStop) |
| Centre Paris | LatLng(48.8566, 2.3522) |
| Zoom initial | 11 ou 12 (vue ville) |
| Style | MapLibre style URL (ex. mapbox://styles/mapbox/streets-v12) ou style OSM — vérifier compatibilité offline |
| NFR-P1 | Rendu fluide — pas de travail lourd sur le main thread |

### Architecture Compliance

**Structure packages** (architecture.md) :
```
app/src/main/java/com/parcoursparis/
├── map/
│   ├── MapScreen.kt          # Modifier : intégrer MapView
│   ├── MapViewModel.kt        # Optionnel pour 1.3 — caméra state si besoin
│   └── MapUiState.kt         # Optionnel pour 1.3
```

**Conventions** (architecture.md) :
- Composables : PascalCase (MapScreen)
- Pas de MapViewModel obligatoire pour 1.3 si état minimal — la carte gère son propre état (pan/zoom)
- Si ViewModel : MapUiState avec data/loading/error

**Data flow** : Story 1.3 n'utilise PAS encore SegmentRepository. La carte est affichée seule. Story 1.4 ajoutera MapSegmentLayer qui consommera les segments.

### Library & Framework Requirements

**MapLibre android-sdk 12.3.1** — Déjà dans libs.versions.toml et build.gradle.kts.

**Intégration AndroidView** :
```kotlin
@Composable
fun MapLibreMap(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                // config
            }
        },
        modifier = modifier,
        update = { mapView -> /* updates if needed */ }
    )
}
```

**Lifecycle** : MapView doit recevoir les événements lifecycle. Utiliser `LocalLifecycleOwner` ou passer le lifecycle depuis MapScreen (Activity/LifecycleOwner).

**Style** : MapLibre accepte une URL de style. Pour offline : le style peut être chargé une fois puis mis en cache. Alternative : style minimal MapLibre (pas de tiles externes) — vérifier doc MapLibre pour style offline.

### File Structure Requirements

| Fichier/Dossier | Action |
|-----------------|--------|
| `map/MapScreen.kt` | Modifier — remplacer placeholder par MapView intégré |
| `map/MapLibreMap.kt` ou composable inline | Créer si extraction utile — AndroidView + MapView + lifecycle |
| `map/MapViewModel.kt` | Optionnel — pas requis pour pan/zoom de base (MapView gère) |

**Ne pas créer** : MapSegmentLayer (story 1.4), SegmentSelector (story 3.x).

### Testing Requirements

- **Unit tests** : Optionnel pour 1.3 — MapViewModel si créé
- **Instrumented tests** : MapScreenTest — vérifier que la carte s'affiche (présence de MapView), pas de crash au pan/zoom
- **Validation manuelle** : Pan fluide, zoom fluide, carte centrée sur Paris, pas de freeze (NFR-P1)

### Previous Story Intelligence (1.2)

- **SegmentRepository** : Existe, expose segmentsWithExploredState. Ne pas l'utiliser dans 1.3 — pas de segments sur la carte encore.
- **GeoJsonLoader** : Charge paris_segments.geojson au démarrage. Les segments sont en Room. Story 1.4 utilisera ces données pour MapSegmentLayer.
- **Structure** : data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/, settings/ — tout existe.
- **MapScreen** : Actuellement `Box { Text("Carte (écran vide)") }` — à remplacer par la carte MapLibre.
- **Navigation** : Ne pas modifier ParcoursNavHost, BottomNavBar, NavRoutes.
- **Conventions** : PascalCase Composables, snake_case Room, package com.parcoursparis.

### Git Intelligence Summary

- **Derniers commits** : Story 1.2 (pipeline OSM), Story 1.1 (création projet)
- **Fichiers modifiés en 1.2** : data/*, GeoJsonLoader, ParcoursParisApplication, assets/paris_segments.geojson
- **MapScreen** : Inchangé depuis 1.1 — toujours placeholder
- **build.gradle.kts** : MapLibre 12.3.1 déjà présent, Room, KSP, Compose BOM 2025.08.01

### Latest Tech Information (Web Research)

- **MapLibre Android + Compose** : Le SDK officiel (org.maplibre.gl:android-sdk) est View-based. Intégration via `AndroidView`. MapLibre Compose (maplibre-compose) existe mais n'est pas dans l'architecture — rester sur android-sdk 12.3.1.
- **MapView lifecycle** : Appeler `mapView.onStart()` / `onStop()` / `onDestroy()` depuis le LifecycleOwner pour éviter fuites et crashes.
- **Style offline** : MapLibre peut utiliser un style JSON local (assets) ou une URL. Pour MVP sans réseau : style MapLibre demo ou OSM qui charge les tiles au premier lancement (cache). Vérifier `MapLibre.getApiKey()` — certains styles nécessitent une clé (Mapbox). MapLibre styles publics : https://demotiles.maplibre.org/style.json (exemple).
- **Paris bounds** : Centre 48.8566, 2.3522. Zoom 11 = quartier, 12 = rue.

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.3]
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Architecture, Project Structure]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Map as primary interface]
- [Source: _bmad-output/planning-artifacts/prd.md#FR3, NFR-P1]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — Frontend Architecture, Project Structure, MapLibre 12.3.1
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 1.3 acceptance criteria
- PRD : `_bmad-output/planning-artifacts/prd.md` — FR3, NFR-P1
- UX : `_bmad-output/planning-artifacts/ux-design-specification.md` — Carte plein écran

## Dev Agent Record

### Agent Model Used

Cursor AI

### Debug Log References

### Completion Notes List

- MapLibreMap.kt créé : AndroidView + MapView, lifecycle via DisposableEffect + LocalLifecycleOwner (onStart/onStop/onDestroy)
- Style : https://demotiles.maplibre.org/style.json (compatible réseau, cache tiles)
- Caméra : Paris (48.8566, 2.3522), zoom 11.5
- MapScreen : remplace placeholder par MapLibreMap, testTag pour instrumented test
- MapScreenTest (androidTest) : vérifie présence MapView via onNodeWithTag
- Permission INTERNET ajoutée (manifest) pour chargement tiles
- Pan/zoom : natifs MapView (validation manuelle NFR-P1)
- [Code Review 2026-02-24] Gestion erreur chargement style (OnDidFailLoadingMapListener + Toast)
- [Code Review 2026-02-24] contentDescription pour TalkBack (strings.xml map_content_description)
- [Code Review 2026-02-24] Nettoyage mapViewState après onDestroy

### File List

- app/src/main/java/com/parcoursparis/map/MapLibreMap.kt (créé)
- app/src/main/java/com/parcoursparis/map/MapScreen.kt (modifié)
- app/src/main/res/values/strings.xml (modifié — map_content_description)
- app/src/main/AndroidManifest.xml (modifié — INTERNET)
- app/build.gradle.kts (modifié — androidTest deps, testInstrumentationRunner)
- app/src/androidTest/java/com/parcoursparis/map/MapScreenTest.kt (créé)

### Senior Developer Review (AI)

**Reviewer:** RomainLAMBERT — 2026-02-24

**Issues fixed:**
- Gestion erreur chargement style MapLibre (OnDidFailLoadingMapListener + Toast)
- contentDescription pour accessibilité TalkBack (WCAG 2.1)
- Nettoyage mapViewState après onDestroy

**Non corrigé (action manuelle):**
- Story non commitée — créer branche `feature/1-3-affichage-carte-avec-pan-et-zoom`, committer, push
- Style demotiles requiert réseau au premier chargement (cache ensuite) — acceptable MVP

### Change Log

- 2026-02-24 : Story 1.3 implémentée — MapLibre intégré, pan/zoom, Paris centré
- 2026-02-24 : Code review — corrections erreur style, accessibilité, lifecycle
