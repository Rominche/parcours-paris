# Story 1.2: Pipeline OSM et couche données

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want que l'application charge les segments de rues de Paris depuis des données OSM,
So que la carte puisse afficher la géométrie des rues.

## Acceptance Criteria

1. **Given** un fichier GeoJSON des segments Paris est disponible dans assets/
2. **When** l'app démarre
3. **Then** les segments sont chargés et stockés (Room : entities Segment, SegmentVisit ; DAOs)
4. **And** SegmentRepository expose les segments et l'état exploré/non exploré
5. **And** le stockage reste ≤ 250 Mo (NFR-P4)
6. **And** aucune connexion réseau n'est requise (FR24, NFR-I1)

## Tasks / Subtasks

- [x] Créer les entities Room Segment et SegmentVisit (AC: #3)
  - [x] Segment : osm_way_id, géométrie (GeoJSON), métadonnées
  - [x] SegmentVisit : segment_id, explored_at, osm_way_id
- [x] Créer les DAOs SegmentDao et SegmentVisitDao (AC: #3)
  - [x] SegmentDao : getAll, getById, insertAll
  - [x] SegmentVisitDao : getAll, insert, delete, getExploredIds
- [x] Configurer AppDatabase avec les entities et migrations (AC: #3)
- [x] Implémenter le chargement GeoJSON depuis assets/ (AC: #1, #2)
  - [x] Parser paris_segments.geojson au démarrage
  - [x] Insérer les segments dans Room (bulk insert)
- [x] Implémenter SegmentRepository (AC: #4)
  - [x] Exposer Flow<List<Segment>> avec état exploré/non exploré
  - [x] Méthodes pour marquer exploré (story 1.3+)
- [x] Vérifier stockage ≤ 250 Mo et mode offline (AC: #5, #6)

## Dev Notes

### Developer Context

**Contexte Epic 1 :** Carte de Paris et visualisation de la progression. La story 1.1 a créé le projet, la structure et l'écran carte vide. Cette story 1.2 pose la couche données : GeoJSON OSM → Room. Les stories 1.3 (carte MapLibre), 1.4 (segments colorés) et 1.5 (GPS) en dépendent.

**Story précédente (1.1) :** Projet Android avec Empty Activity (Compose), Room 2.8.4, MapLibre 12.3.1, structure packages (data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/, settings/), bottom nav Map | Profile | Settings. Le dossier `data/` existe mais est vide (`.gitkeep`). Aucune entité Room ni DAO n'existe encore.

**Points critiques à ne pas manquer :**
- Unité de segment = OSM way entre intersections (PRD, architecture)
- GeoJSON dans `app/src/main/assets/paris_segments.geojson` — le fichier doit être fourni ou généré par un pipeline OSM externe (hors scope MVP : utiliser un fichier de démo si nécessaire)
- Room : tables `segment` et `segment_visit` (snake_case obligatoire)
- Pas de réseau : tout chargement depuis assets uniquement

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| Room | 2.8.4 (déjà ajouté en 1.1) |
| GeoJSON | Format FeatureCollection, Feature avec geometry LineString |
| Segment | osm_way_id (Long), géométrie stockée (JSON string ou colonnes dédiées) |
| SegmentVisit | segment_id (PK), explored_at (Long epoch), osm_way_id |
| Stockage max | 250 Mo (NFR-P4) |
| Réseau | Aucun — chargement 100 % local |

### Architecture Compliance

**Structure packages obligatoire** (architecture.md) :
```
app/src/main/java/com/parcoursparis/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   └── dao/
│   │       ├── SegmentDao.kt
│   │       └── SegmentVisitDao.kt
│   ├── entity/
│   │   ├── Segment.kt
│   │   └── SegmentVisit.kt
│   └── repository/
│       └── SegmentRepository.kt
```

**Conventions naming** (architecture.md) :
- Tables : `segment`, `segment_visit` (snake_case)
- Colonnes : `osm_way_id`, `explored_at`, `segment_id` (snake_case)
- DAOs : `SegmentDao`, `SegmentVisitDao`
- Entities : `Segment`, `SegmentVisit`

**Data flow** (architecture.md) :
1. GeoJSON chargé depuis assets au démarrage
2. Segments parsés et insérés dans Room
3. SegmentVisit vide au départ (tous segments non explorés)
4. SegmentRepository expose segments + état exploré pour MapViewModel (story 1.4)

### Library & Framework Requirements

**Room 2.8.4** — Déjà configuré en 1.1. Ajouter :
- Plugin KSP activé
- `room-runtime`, `room-ktx`, `room-compiler` (KSP)

**GeoJSON parsing** — Utiliser :
- `org.json` (Android SDK) ou `kotlinx.serialization` pour parser le JSON
- Pas de lib externe obligatoire pour GeoJSON simple (FeatureCollection → Feature[] → geometry)

**TypeConverter** — Si géométrie stockée en JSON :
```kotlin
@TypeConverter
fun fromGeoJson(value: String?): List<List<Double>>? = ...

@TypeConverter  
fun toGeoJson(list: List<List<Double>>?): String? = ...
```

### File Structure Requirements

| Fichier/Dossier | Action |
|-----------------|--------|
| `app/src/main/assets/` | Créer si absent |
| `app/src/main/assets/paris_segments.geojson` | Fichier source — fournir ou placeholder minimal pour tests |
| `data/entity/Segment.kt` | Entity avec osm_way_id, geometry |
| `data/entity/SegmentVisit.kt` | Entity avec segment_id, explored_at, osm_way_id |
| `data/db/dao/SegmentDao.kt` | DAO pour Segment |
| `data/db/dao/SegmentVisitDao.kt` | DAO pour SegmentVisit |
| `data/db/AppDatabase.kt` | Database avec entities, version, migrations |
| `data/repository/SegmentRepository.kt` | Repository exposant segments + état exploré |

**GeoJSON format attendu** (architecture, PRD) :
```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": { "osm_way_id": 12345 },
      "geometry": {
        "type": "LineString",
        "coordinates": [[2.35, 48.85], [2.36, 48.86]]
      }
    }
  ]
}
```

### Testing Requirements

- **Unit tests** : SegmentRepositoryTest (mock DAOs, vérifier logique d'agrégation exploré/non exploré)
- **Instrumented tests** : Optionnel — test d'insert/query Room si pertinent
- **Validation manuelle** : L'app démarre sans crash ; les segments sont chargés (vérifier via log ou debug)

### Previous Story Intelligence (1.1)

- **Structure** : Packages data/, map/, etc. déjà créés. Ne pas recréer.
- **Dépendances** : Room 2.8.4, KSP déjà dans build.gradle.kts. Vérifier que KSP est bien activé pour Room.
- **Conventions** : PascalCase Composables, snake_case Room, package `com.parcoursparis`
- **Problèmes rencontrés** : Compilation bloquée si JVM < 11 ; utiliser JBR 21 (Android Studio). Wrapper Gradle : `gradlew.bat` avec `gradle-wrapper.jar` présent.
- **Fichiers créés en 1.1** : MainActivity, ParcoursParisApp, NavHost, MapScreen (vide), ProfileScreen, SettingsScreen, theme. Ne pas modifier la navigation.

### Git Intelligence Summary

- **Dernier commit** : Story 1.1 — création projet Android et structure de base
- **Patterns** : Single-module app, Kotlin DSL, Compose BOM 2025.08.01 ou 2025.12.00
- **À réutiliser** : Structure packages, dépendances Room/MapLibre déjà déclarées

### Latest Tech Information (Web Research)

- **Room prepopulate** : `Room.databaseBuilder(...).createFromAsset("database/xxx.db")` pour DB pré-remplie. Ici on charge GeoJSON → insert au runtime (prepopulate non requis pour MVP).
- **GeoJSON depuis assets** : `context.assets.open("paris_segments.geojson").bufferedReader().use { it.readText() }`
- **Bulk insert Room** : `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(segments: List<Segment>)`

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.2]
- [Source: _bmad-output/planning-artifacts/architecture.md#Data Architecture, Project Structure]
- [Source: _bmad-output/planning-artifacts/prd.md#Données & Stockage, NFR-P4, NFR-I1]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — Data Architecture, Naming Patterns, Project Structure
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 1.2 acceptance criteria
- PRD : `_bmad-output/planning-artifacts/prd.md` — FR23, FR24, NFR-P4, NFR-I1

## Dev Agent Record

### Agent Model Used

Composer (dev-story workflow)

### Debug Log References

### Completion Notes List

- Entities Segment et SegmentVisit créées avec tables snake_case (segment, segment_visit)
- DAOs SegmentDao et SegmentVisitDao avec getAll, getById, insertAll, insert, delete, getExploredIds
- AppDatabase configurée avec Room 2.8.4, version 1, exportSchema=true avec documentation migration
- GeoJsonLoader parse FeatureCollection depuis assets/paris_segments.geojson avec validation complète
- ParcoursParisApplication charge les segments au démarrage avec logging des erreurs et gestion lifecycle
- SegmentRepository expose segmentsWithExploredState (Flow optimisé avec distinctUntilChanged)
- Fichier GeoJSON de démo avec 3 segments (placeholder pour pipeline OSM)
- Tests unitaires SegmentRepositoryTest avec Fake DAOs améliorés + GeoJsonLoaderTest
- Stockage ≤ 250 Mo : Documentation complète (docs/STORAGE_LIMITS.md) avec estimations réalistes
- Mode offline : Documentation validation (docs/OFFLINE_VALIDATION.md), aucun réseau requis
- TypeConverter pour geometry_json avec validation
- Backup rules configurées dans AndroidManifest (exclusion DB pour éviter corruption)

**Code Review Corrections (2026-02-17):**
- ✅ Fixed: SegmentVisit Entity - Supprimé champ osm_way_id redondant (11 issues HIGH/MEDIUM corrigées)
- ✅ Fixed: GeoJsonLoader - Ajouté validation GeoJSON (type, coordonnées, limites Paris)
- ✅ Fixed: ParcoursParisApplication - Ajouté logging erreurs + onTerminate pour cleanup scope
- ✅ Fixed: AppDatabase - exportSchema=true + documentation stratégie migration
- ✅ Fixed: Segment Entity - Ajouté TypeConverter et méthode validateGeometry()
- ✅ Fixed: SegmentRepository - Optimisé Flow avec distinctUntilChanged()
- ✅ Fixed: AndroidManifest - Ajouté backup_rules.xml pour exclure DB
- ✅ Fixed: Fake DAOs - Implémenté correctement OnConflictStrategy.REPLACE
- ✅ Added: GeoJsonLoaderTest avec Robolectric
- ✅ Documented: AC#5 (STORAGE_LIMITS.md) et AC#6 (OFFLINE_VALIDATION.md)

### File List

- app/src/main/java/com/parcoursparis/data/entity/Segment.kt (updated: TypeConverter + validation)
- app/src/main/java/com/parcoursparis/data/entity/SegmentVisit.kt (updated: removed osm_way_id field)
- app/src/main/java/com/parcoursparis/data/db/dao/SegmentDao.kt
- app/src/main/java/com/parcoursparis/data/db/dao/SegmentVisitDao.kt
- app/src/main/java/com/parcoursparis/data/db/AppDatabase.kt (updated: exportSchema=true + docs)
- app/src/main/java/com/parcoursparis/data/GeoJsonLoader.kt (updated: validation complète)
- app/src/main/java/com/parcoursparis/data/repository/SegmentRepository.kt (updated: optimized Flow)
- app/src/main/java/com/parcoursparis/ParcoursParisApplication.kt (updated: logging + lifecycle)
- app/src/main/assets/paris_segments.geojson
- app/src/main/AndroidManifest.xml (updated: backup rules)
- app/src/main/res/xml/backup_rules.xml (new)
- app/build.gradle.kts (updated: KSP schema location + test deps)
- app/src/test/java/com/parcoursparis/data/repository/SegmentRepositoryTest.kt (updated)
- app/src/test/java/com/parcoursparis/data/repository/FakeSegmentDao.kt (updated: better REPLACE)
- app/src/test/java/com/parcoursparis/data/repository/FakeSegmentVisitDao.kt (updated: better REPLACE)
- app/src/test/java/com/parcoursparis/data/GeoJsonLoaderTest.kt (new)
- docs/STORAGE_LIMITS.md (new: AC#5 validation)
- docs/OFFLINE_VALIDATION.md (new: AC#6 validation)
- _bmad-output/implementation-artifacts/sprint-status.yaml

### Change Log

- 2026-02-17 : Story 1.2 implémentée — pipeline OSM et couche données Room (Segment, SegmentVisit, DAOs, AppDatabase, GeoJsonLoader, SegmentRepository, chargement au démarrage)
- 2026-02-17 : Code review effectuée — 11 issues HIGH/MEDIUM corrigées, validation complète des données, logging, optimisations, documentation AC#5 et AC#6, tests améliorés → Story DONE
