---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: 'complete'
completedAt: '2026-02-17'
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/planning-artifacts/product-brief-parcours-paris-2026-02-13.md
  - _bmad-output/planning-artifacts/ux-design-specification.md
workflowType: 'architecture'
project_name: 'parcours-paris'
user_name: 'Rominche'
date: '2026-02-17'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
- **Map & visualization (FR1–4):** Colored segment display (explored/unexplored), LOD by zoom, pan/zoom, GPS position — implies MapLibre integration, segment geometry storage, real-time rendering.
- **Navigation (FR5–10):** Destination search with autocomplete, discovery-oriented routing algorithm, tolerance parameter, route display, GPS tracking — implies custom routing engine, geocoding, fallback to classic route.
- **Manual marking (FR11–14):** Segment selection on map, mark/unmark, real-time update — implies touch interaction on map, state persistence, immediate UI feedback.
- **Profile & stats (FR15–19):** % Paris explored, km, top days, best month, monthly recap — implies aggregation queries, time-series data.
- **Enrichment (FR20–22):** POI bubbles (Wikipedia, OSM) on demand — implies optional network calls, graceful offline handling.
- **Data & storage (FR23–25):** Local history, offline mode, optional Google Timeline sync — implies Room/SQLite, OSM geometry preload, sync strategy.
- **Settings (FR26–27):** Tolerance config, permissions — implies preferences, runtime permission handling.

**Non-Functional Requirements:**
- **Performance:** Map fluency (no freeze on zoom/pan), route calculation < 5 s, segment mark update < 1 s, storage ≤ 250 Mo.
- **Security:** Local-only data, explicit permissions.
- **Integration:** Offline-first, APIs must handle network absence without blocking.

**Scale & Complexity:**
- Primary domain: **mobile (Android native)**
- Complexity level: **low**
- Estimated architectural components: Map layer, routing engine, data layer (Room), profile/stats, enrichment service, settings

### Technical Constraints & Dependencies

- **Platform:** Android only (Kotlin), no cross-platform.
- **Data:** OSM geometry (segments between intersections), preloaded locally.
- **Map:** MapLibre or equivalent, custom styling for colored segments.
- **Storage:** Room or SQLite, 250 Mo limit.
- **APIs:** Wikipedia, OSM, Google Timeline (optional) — must degrade gracefully offline.
- **Stack:** 100 % open source, no proprietary APIs.

### Cross-Cutting Concerns Identified

- **Offline-first:** All core features must work without network; enrichment and sync are optional.
- **Map performance:** LOD, segment rendering, real-time updates — critical for UX.
- **Routing algorithm:** Custom discovery-oriented logic with tolerance and fallback.
- **State consistency:** Segment state (explored/unexplored) shared between map, routing, and profile.
- **Accessibility:** WCAG 2.1 AA (contrast, touch targets, TalkBack).

## Starter Template Evaluation

### Primary Technology Domain

**Mobile (Android native)** based on project requirements analysis.

### Starter Options Considered

| Option | Type | Verdict |
|--------|------|---------|
| Android Studio Empty Activity (Compose) | Official template | **Selected** — aligned with UX spec, maintained by Google |
| GitHub Jetpack Compose Starter (modular) | Community | Rejected for MVP — overkill for solo project, single-module sufficient |

### Selected Starter: Android Studio Empty Activity (Compose)

**Rationale for Selection:**
- Aligns with UX specification (Jetpack Compose, Material 3)
- Officially maintained by Google
- Appropriate for low-complexity MVP and solo development
- Avoids over-engineering (no modularization at start)
- Dependencies (MapLibre, Room) can be added incrementally

**Initialization:**

```
1. Open Android Studio
2. File > New > New Project
3. Select "Empty Activity" template
4. Configure: Name (parcours-paris), Package, Language (Kotlin), Min SDK (API 24+)
5. Finish
```

*Note: No official CLI for Android project creation; use Android Studio UI.*

### Architectural Decisions Provided by Starter

| Category | Decision |
|----------|----------|
| **Language & Runtime** | Kotlin, JVM Android |
| **UI Framework** | Jetpack Compose (Material 3 to add explicitly) |
| **Build** | Gradle (Kotlin DSL), Android Gradle Plugin |
| **Testing** | JUnit 4 default (extend with Compose testing) |
| **Structure** | Single-module app, MainActivity + MainScreen Composable |
| **Dev Experience** | Compose hot reload, Android Studio |

**To add manually:** Material 3 dependency, MapLibre, Room, Compose BOM (e.g. 2025.12.00).

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Room 2.8.4 for local persistence
- MapLibre 12.3.1 for map rendering
- OSM geometry: GeoJSON preloaded + Room for segment state
- ViewModel + Compose State for UI state
- Compose Navigation for bottom nav (Map | Profile | Settings)

**Important Decisions (Shape Architecture):**
- MVVM architecture pattern
- No authentication (personal app)
- Optional APIs (Wikipedia, OSM) with graceful offline degradation

**Deferred Decisions (Post-MVP):**
- Google Timeline sync implementation
- CI/CD pipeline
- Modularization (if project grows)

### Data Architecture

| Decision | Choice | Version | Rationale |
|----------|--------|---------|-----------|
| Local database | Room | 2.8.4 | PRD requirement, Kotlin coroutines, migrations |
| OSM data format | GeoJSON (preloaded) + Room | — | Geometry in assets, segment state in DB |
| Segment unit | OSM way between intersections | — | PRD: segments entre intersections |

### Authentication & Security

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Authentication | None | Personal app, single user |
| Data storage | Local only | NFR-S1 |
| Permissions | Explicit (location, storage) | NFR-S2 |

### API & Communication Patterns

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Backend | None | Offline-first |
| External APIs | Wikipedia, OSM (optional) | Enrichment on demand |
| Offline strategy | Core fully offline; APIs degrade gracefully | NFR-I1, NFR-I2 |

### Frontend Architecture

| Decision | Choice | Rationale |
|----------|--------|-----------|
| State management | ViewModel + Compose State | Android standard, Compose-native |
| Navigation | Compose Navigation | Bottom nav per UX spec |
| Pattern | MVVM | Clear separation, testability |

### Infrastructure & Deployment

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Build | Gradle (Kotlin DSL) | From starter |
| Distribution | APK, Play Store | PRD: free, open source |
| CI/CD | Deferred | Solo project, MVP focus |

### Decision Impact Analysis

**Implementation Sequence:**
1. Project setup (Android Studio template)
2. Add Room, MapLibre, Material 3 dependencies
3. OSM data pipeline (extract Paris, GeoJSON)
4. Map layer + segment rendering
5. Data layer (entities, DAOs)
6. Routing engine (discovery-oriented)
7. Navigation flow + Profile screen
8. Enrichment (optional APIs)

**Cross-Component Dependencies:**
- Map ↔ Data: segment state drives map colors
- Routing ↔ Data: uses segment state for discovery preference
- Profile ↔ Data: aggregates from segment history

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**Critical Conflict Points Identified:**
5 areas where AI agents could make different choices (naming, structure, format, state, process)

### Naming Patterns

**Database (Room) Naming Conventions:**
- Tables: `snake_case`, singular (`segment`, `segment_visit`, `user_preference`)
- Columns: `snake_case` (`segment_id`, `explored_at`, `osm_way_id`)
- DAOs: `XxxDao` (e.g. `SegmentDao`, `SegmentVisitDao`)
- Entities: singular noun (`Segment`, `SegmentVisit`)

**Code Naming Conventions (Kotlin):**
- Packages: `lowercase`, feature-based (`com.parcoursparis.map`, `com.parcoursparis.data`)
- Composables: `PascalCase` (`MapScreen`, `SegmentSelector`, `ProfileStatCard`)
- ViewModels: `XxxViewModel` (`MapViewModel`, `ProfileViewModel`)
- Files: match primary type (`MapScreen.kt`, `SegmentDao.kt`)

### Structure Patterns

**Project Organization:**
```
app/src/main/java/com/parcoursparis/
├── data/           # Room, repositories, entities
├── map/            # Map screen, MapLibre, segment layer
├── navigation/     # Compose Navigation, bottom nav
├── profile/        # Profile screen, stats
├── routing/        # Discovery routing engine
├── enrichment/     # Wikipedia, OSM POI (optional)
├── ui/             # Shared composables, theme
└── util/           # Extensions, helpers
```

- Tests: `src/test/` for unit, `src/androidTest/` for instrumented
- Assets: `assets/` for GeoJSON; `res/` for drawables, strings

**File Structure:**
- One primary class per file
- Screen composables in feature package (e.g. `map/MapScreen.kt`)

### Format Patterns

**External API Responses (Wikipedia, OSM):**
- JSON: accept both snake_case and camelCase; normalize to camelCase in Kotlin data classes
- Dates: ISO 8601 when exchanging

**Room / Local Data:**
- Use Kotlin types; Room handles conversion
- Prefer `Long` for timestamps (epoch millis)

### Communication Patterns

**State Management:**
- ViewModel exposes: `StateFlow<UiState>` or `State<T>` for Compose
- UiState: sealed class or data class with `data`, `loading`, `error` variants
- Naming: `MapUiState`, `ProfileUiState` (not `MapState` to avoid clash with Compose `State`)

**Events (one-off actions):**
- `SharedFlow` or callback for events (e.g. "show snackbar", "navigate")
- Naming: `MapEvent`, `ProfileEvent`; events as sealed class

### Process Patterns

**Error Handling:**
- UiState includes `error: String?` or `UiState.Error(message)`
- User-facing: short, actionable messages
- Logging: use `Log` or Timber; include context

**Loading States:**
- `UiState.Loading` or `UiState(data = null, isLoading = true)`
- One loading state per screen/section; avoid nested loaders

### Enforcement Guidelines

**All AI Agents MUST:**
- Use `snake_case` for Room tables and columns
- Follow package structure: `data/`, `map/`, `profile/`, `routing/`, `ui/`, `util/`
- Name ViewModels `XxxViewModel` and UiState `XxxUiState`
- Include error and loading in UiState for screens with async data

**Pattern Verification:**
- Lint rules where possible (e.g. naming)
- Code review checklist referencing this document

### Pattern Examples

**Good:**
```kotlin
// Entity
@Entity(tableName = "segment_visit")
data class SegmentVisit(
    @PrimaryKey val segmentId: Long,
    val exploredAt: Long,
    val osmWayId: Long
)

// UiState
data class MapUiState(
    val segments: List<Segment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

**Anti-Patterns:**
- `Segment` as table name (use `segment`); `userId` in Room (use `user_id`)
- Mixing feature and layer organization
- `MapState` for screen state (clashes with Compose)

## Project Structure & Boundaries

### Complete Project Directory Structure

```
parcours-paris/
├── .gitignore
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/parcoursparis/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ParcoursParisApp.kt
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   ├── dao/
│       │   │   │   │   │   ├── SegmentDao.kt
│       │   │   │   │   │   ├── SegmentVisitDao.kt
│       │   │   │   │   │   └── UserPreferenceDao.kt
│       │   │   │   ├── entity/
│       │   │   │   │   ├── Segment.kt
│       │   │   │   │   ├── SegmentVisit.kt
│       │   │   │   │   └── UserPreference.kt
│       │   │   │   └── repository/
│       │   │   │       ├── SegmentRepository.kt
│       │   │   │       └── ProfileRepository.kt
│       │   │   ├── map/
│       │   │   │   ├── MapScreen.kt
│       │   │   │   ├── MapViewModel.kt
│       │   │   │   ├── MapUiState.kt
│       │   │   │   ├── layer/
│       │   │   │   │   └── MapSegmentLayer.kt
│       │   │   │   ├── component/
│       │   │   │   │   ├── SegmentSelector.kt
│       │   │   │   │   ├── SearchBar.kt
│       │   │   │   │   └── RouteBottomSheet.kt
│       │   │   │   └── geocoding/
│       │   │   │       └── GeocodingService.kt
│       │   │   ├── navigation/
│       │   │   │   ├── ParcoursNavHost.kt
│       │   │   │   ├── NavRoutes.kt
│       │   │   │   └── BottomNavBar.kt
│       │   │   ├── profile/
│       │   │   │   ├── ProfileScreen.kt
│       │   │   │   ├── ProfileViewModel.kt
│       │   │   │   ├── ProfileUiState.kt
│       │   │   │   └── component/
│       │   │   │       ├── ProfileStatCard.kt
│       │   │   │       └── MonthlyRecapCard.kt
│       │   │   ├── routing/
│       │   │   │   ├── DiscoveryRoutingEngine.kt
│       │   │   │   ├── RouteResult.kt
│       │   │   │   └── GraphBuilder.kt
│       │   │   ├── enrichment/
│       │   │   │   ├── EnrichmentBubble.kt
│       │   │   │   ├── EnrichmentService.kt
│       │   │   │   └── EnrichmentContent.kt
│       │   │   ├── settings/
│       │   │   │   ├── SettingsScreen.kt
│       │   │   │   └── SettingsViewModel.kt
│       │   │   ├── ui/
│       │   │   │   ├── theme/
│       │   │   │   │   ├── Color.kt
│       │   │   │   │   ├── Theme.kt
│       │   │   │   │   └── Type.kt
│       │   │   │   └── component/
│       │   │   │       ├── ToleranceSlider.kt
│       │   │   │       └── LoadingOverlay.kt
│       │   │   └── util/
│       │   │       ├── Extensions.kt
│       │   │       └── LocationUtils.kt
│       │   ├── assets/
│       │   │   └── paris_segments.geojson
│       │   └── res/
│       │       ├── values/
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       └── drawable/
│       ├── test/
│       │   └── java/com/parcoursparis/
│       │       ├── routing/
│       │       │   └── DiscoveryRoutingEngineTest.kt
│       │       └── data/
│       │           └── repository/
│       │               └── SegmentRepositoryTest.kt
│       └── androidTest/
│           └── java/com/parcoursparis/
│               └── map/
│                   └── MapScreenTest.kt
└── _bmad-output/
    └── planning-artifacts/
        └── architecture.md
```

### Architectural Boundaries

**API Boundaries:**
- No backend API; app is offline-first
- External (optional): Wikipedia API, OSM Overpass — called from `EnrichmentService`, must degrade gracefully when offline

**Component Boundaries:**
- **Map** ↔ **Data**: MapViewModel uses SegmentRepository; segment state flows to MapSegmentLayer
- **Profile** ↔ **Data**: ProfileViewModel uses ProfileRepository for stats
- **Routing** ↔ **Data**: DiscoveryRoutingEngine reads SegmentRepository for explored segments
- **Enrichment** ↔ **Map**: EnrichmentBubble overlays on map; EnrichmentService fetches on demand

**Data Boundaries:**
- Room: single database `AppDatabase`; entities in `data/entity/`
- GeoJSON: read from `assets/paris_segments.geojson` at startup
- Repositories: single source of truth for segment state and profile stats

### Requirements to Structure Mapping

| FR Category | Location |
|-------------|----------|
| Carte & visualisation (FR1–4) | `map/`, `map/layer/MapSegmentLayer.kt` |
| Navigation (FR5–10) | `map/`, `routing/`, `map/geocoding/` |
| Marquage manuel (FR11–14) | `map/component/SegmentSelector.kt`, `MapViewModel` |
| Profil & stats (FR15–19) | `profile/`, `data/repository/ProfileRepository.kt` |
| Enrichissement (FR20–22) | `enrichment/` |
| Données & stockage (FR23–25) | `data/` |
| Paramètres (FR26–27) | `settings/` |

**Cross-Cutting:**
- **Theme/Design**: `ui/theme/`
- **Navigation**: `navigation/`
- **Location/GPS**: `util/LocationUtils.kt`, used by MapViewModel

### Integration Points

**Internal Communication:**
- ViewModels → Repositories (data)
- Composables → ViewModels (events, state)
- Compose Navigation for screen transitions

**External Integrations:**
- MapLibre: map rendering
- Wikipedia/OSM: EnrichmentService (optional, cached)
- Google Timeline: deferred post-MVP

**Data Flow:**
1. GeoJSON loaded → Room (segment geometry metadata)
2. SegmentVisit records → explored state
3. Map reads SegmentRepository → colors segments
4. Routing reads explored state → discovery-oriented path
5. Profile aggregates SegmentVisit → stats

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:** Room, MapLibre, Compose, Material 3 — all compatible. Versions verified (Room 2.8.4, MapLibre 12.3.1).

**Pattern Consistency:** Naming (snake_case Room, PascalCase Composables), structure (feature-based packages), UiState pattern — all aligned.

**Structure Alignment:** Project tree supports all decisions; boundaries (data, map, profile, routing, enrichment) clearly defined.

### Requirements Coverage Validation ✅

**Functional Requirements Coverage:** All 27 FRs mapped to components (map, routing, profile, enrichment, data, settings).

**Non-Functional Requirements Coverage:** Performance (NFR-P1–4), Security (NFR-S1–2), Integration (NFR-I1–2) — architecturally addressed.

### Implementation Readiness Validation ✅

**Decision Completeness:** Critical decisions documented with versions and rationale.

**Structure Completeness:** Full directory tree with files; integration points specified.

**Pattern Completeness:** Naming, structure, format, communication, process patterns defined with examples.

### Gap Analysis Results

- **Critical:** None
- **Important:** OSM data pipeline (Paris extraction → GeoJSON) to be implemented; document during build
- **Nice-to-have:** Compose UI tests, CI/CD

### Architecture Completeness Checklist

**✅ Requirements Analysis** — Context, scale, constraints, cross-cutting concerns
**✅ Architectural Decisions** — Stack, versions, integration patterns
**✅ Implementation Patterns** — Naming, structure, communication, process
**✅ Project Structure** — Directory tree, boundaries, mapping

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** High

**Key Strengths:**
- Clear offline-first architecture
- Feature-based structure
- Consistent patterns for AI agents
- All FRs and NFRs covered

**Areas for Future Enhancement:**
- OSM pipeline documentation
- Google Timeline sync (post-MVP)

### Implementation Handoff

**AI Agent Guidelines:**
- Follow architectural decisions as documented
- Use implementation patterns consistently
- Respect project structure and boundaries
- Refer to this document for architectural questions

**First Implementation Priority:**
1. Create project via Android Studio (Empty Activity, Compose)
2. Add dependencies: Room 2.8.4, MapLibre 12.3.1, Material 3, Compose BOM
3. Set up package structure per Project Structure section
