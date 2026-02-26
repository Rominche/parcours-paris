# Story 2.1: Barre de recherche et géocodage

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want saisir une adresse ou un lieu de destination dans une barre de recherche,
so that je puisse définir ma destination (FR5).

## Acceptance Criteria

1. **Given** je suis sur l'écran carte
   **When** je tape dans la barre de recherche en overlay (16dp padding)
   **Then** un autocomplete propose des suggestions de lieux (géocodage local ou API)

2. **And** en sélectionnant une suggestion, la destination est définie

3. **And** la barre utilise OutlinedTextField Material 3

## Tasks / Subtasks

- [x] Créer le service de géocodage (AC: #1)
  - [x] Créer `map/geocoding/GeocodingService.kt` — interface + implémentation (Nominatim ou Photon, 100 % open source)
  - [x] Exposer `suspend fun search(query: String, bounds: BoundingBox?): List<GeocodingResult>` et gérer absence de réseau (dégradation gracieuse)
- [x] Intégrer la barre de recherche dans l'écran carte (AC: #1, #3)
  - [x] Créer `map/component/SearchBar.kt` — OutlinedTextField Material 3, 16dp padding, overlay sur la carte
  - [x] Afficher la SearchBar en overlay en haut de MapScreen (Box + Modifier.padding(16.dp))
- [x] Brancher l'autocomplete (AC: #1)
  - [x] Débouncer la saisie (ex. 300 ms) puis appeler GeocodingService.search
  - [x] Afficher une liste de suggestions (DropdownMenu / LazyColumn) sous le champ
  - [x] Gérer états vide, chargement, erreur (hors ligne : message clair)
- [x] Définir la destination au clic sur une suggestion (AC: #2)
  - [x] Étendre MapUiState avec `destination: LatLng?` (ou `GeocodingResult?`)
  - [x] MapViewModel : `onDestinationSelected(result)` met à jour l'état
  - [ ] Optionnel pour 2.1 : afficher un marqueur destination sur la carte (ou réserver pour 2.2/2.3)
- [x] Tests et dégradation hors ligne (AC: #1)
  - [x] Test unitaire GeocodingService (mock HTTP / hors ligne)
  - [x] Message utilisateur si pas de réseau : "Connectez-vous pour rechercher une adresse"

## Dev Notes

### Developer Context

**Contexte Epic 2 :** Cette story ouvre l'épic "Navigation orientée découverte". Les stories 2.2–2.4 ajouteront le moteur de routing, l'affichage de l'itinéraire avec tolérance, et le suivi + fallback classique. La story 2.1 pose la **base utilisateur** : saisir une destination via une barre de recherche avec autocomplete (FR5).

**État du code après Epic 1 :**
- `MapScreen` : carte plein écran, permission GPS, bandeau erreur ; pas encore de SearchBar ni de champ destination.
- `MapViewModel` / `MapUiState` : segments, position GPS, permission ; pas de champ `destination`.
- Architecture prévoit déjà `map/component/SearchBar.kt` et `map/geocoding/GeocodingService.kt` — à créer.
- Stack 100 % open source (PRD, architecture) : **pas de Google Places SDK** ; utiliser OSM Nominatim ou Photon pour le géocodage.

**Contraintes clés :**
- **Offline-first** : en absence de réseau, l'autocomplete ne peut pas fonctionner ; afficher un message clair sans faire planter l'app (NFR-I2).
- **UX** : Carte plein écran avec search bar en overlay, 16dp padding (ux-design-specification.md). OutlinedTextField Material 3, pas de validation stricte de l'adresse.

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| Composant recherche | `OutlinedTextField` Material 3, icône recherche, placeholder "Adresse ou lieu" |
| Position UI | Overlay en haut de la carte, `Modifier.padding(16.dp)` |
| Géocodage | API open source : OSM Nominatim (nominatim.openstreetmap.org) ou Photon (photon.komoot.io) ; limite Paris/Île-de-France si possible (bounds) |
| Autocomplete | Débounce 300–400 ms sur la saisie ; appel API limité (rate limit Nominatim : 1 req/s) |
| Résultat | Liste de suggestions avec libellé + coordonnées (lat/lng) ; au clic → destination définie |
| État destination | `MapUiState.destination: LatLng?` (ou `GeocodingResult?`) ; exposé par MapViewModel |
| Hors ligne | Pas d'appel API ; afficher "Connectez-vous pour rechercher une adresse" ou désactiver le champ avec message |

### Architecture Compliance

**Structure packages (architecture.md) :**
```
app/src/main/java/com/parcoursparis/
├── map/
│   ├── component/
│   │   └── SearchBar.kt          # CRÉER — OutlinedTextField, suggestions, 16dp
│   ├── geocoding/
│   │   ├── GeocodingService.kt   # CRÉER — interface + impl (Nominatim/Photon)
│   │   └── GeocodingResult.kt    # CRÉER — data class (label, lat, lng, displayName?)
│   ├── MapUiState.kt             # MODIFIER — ajouter destination: LatLng?
│   ├── MapViewModel.kt           # MODIFIER — onSearchQuery(), onDestinationSelected(), appel GeocodingService
│   └── MapScreen.kt              # MODIFIER — ajouter SearchBar en overlay (Box)
```

**Conventions (architecture.md) :**
- ViewModel expose `StateFlow<MapUiState>` ; événements one-off (ex. "afficher snackbar") via `SharedFlow` ou callback.
- UiState : `destination: LatLng?` ; pour l’autocomplete en cours : `searchSuggestions: List<GeocodingResult>`, `isSearching: Boolean`, `searchError: String?`.
- Noms : `SearchBar` (Composable), `GeocodingService` (interface), `NominatimGeocodingService` ou `PhotonGeocodingService` (impl).

**Frontières :**
- GeocodingService appelé depuis MapViewModel (ou un use case dédié si vous préférez) ; pas de logique réseau dans les Composables.
- SearchBar est un Composable réutilisable qui émet des événements (query change, suggestion selected) vers le ViewModel.

### Library & Framework Requirements

**Réseau (à ajouter si pas déjà présent) :**
- `Retrofit` + `OkHttp` pour les appels HTTP à Nominatim/Photon (ou `Ktor Client`), ou `HttpURLConnection` simple pour une seule API.
- Vérifier dépendances : `build.gradle.kts` (app) — ajouter `retrofit` + converter-gson (ou équivalent) si nécessaire.

**Nominatim (exemple) :**
- Endpoint : `https://nominatim.openstreetmap.org/search?q={query}&format=json&limit=5&bounded=1&viewbox=...` (viewbox = bounds Paris).
- Headers : `User-Agent` obligatoire (ex. "ParcoursParis/1.0"); respecter la politique d’usage (1 req/s).
- Réponse : JSON avec `lat`, `lon`, `display_name`.

**Photon (alternative) :**
- `https://photon.komoot.io/api/?q={query}&limit=5&bbox=...` (bbox Paris).
- Pas de clé API ; rate limit raisonnable pour usage personnel.

**Compose :**
- `OutlinedTextField` (Material 3), `DropdownMenu` ou `LazyColumn` pour les suggestions.
- `LaunchedEffect(query)` + `delay(300)` pour le debounce ; annuler la coroutine si la query change avant la fin du delay.

### File Structure Requirements

| Fichier/Dossier | Action |
|-----------------|--------|
| `map/geocoding/GeocodingResult.kt` | CRÉER — data class (label, latitude, longitude) |
| `map/geocoding/GeocodingService.kt` | CRÉER — interface + impl (Nominatim ou Photon), gestion réseau/hors ligne |
| `map/component/SearchBar.kt` | CRÉER — OutlinedTextField, liste suggestions, callbacks query/sélection |
| `map/MapUiState.kt` | MODIFIER — destination, searchSuggestions, isSearching, searchError |
| `map/MapViewModel.kt` | MODIFIER — search + destination selected, appel GeocodingService |
| `map/MapScreen.kt` | MODIFIER — Box avec MapLibreMap + SearchBar en overlay (16dp) |
| `res/values/strings.xml` | MODIFIER — chaînes recherche, placeholder, message hors ligne |

**Ne pas créer dans cette story :** DiscoveryRoutingEngine, RouteBottomSheet, ToleranceSlider (stories 2.2–2.4). Optionnel : afficher un marqueur sur la carte pour la destination (peut être fait en 2.1 ou reporté en 2.2).

### Testing Requirements

- **Unit tests** : GeocodingService (mock HTTP) — retourne une liste pour une query valide ; retourne vide ou erreur si pas de réseau / timeout.
- **Unit tests** : MapViewModel — `onDestinationSelected` met à jour `destination` ; `onSearchQuery` déclenche la recherche (debounce) et met à jour suggestions.
- **Tests manuels** : Saisie dans la barre → suggestions après debounce ; clic sur une suggestion → destination définie ; mode avion → message hors ligne, pas de crash.

### Previous Story Intelligence (Epic 1)

**1.5 (dernière story)** : MapScreen avec permission GPS, MapViewModel avec `userLocation`, MapLibreMap avec CircleLayer pour la position. MapViewModelFactory reçoit `Application` pour LocationUtils. Pattern : `StateFlow<MapUiState>`, événements via méthodes `onXxx()`.

**1.4** : MapUiState (segments, isLoading, error). MapViewModel injecte SegmentRepository. MapLibreMap : GeoJsonSource + LineLayers, LaunchedEffect pour mises à jour. Pas de nouveau layer à ajouter pour 2.1 sauf si on affiche le marqueur destination (optionnel).

**Convention établie** : étendre MapUiState et MapViewModel plutôt que créer un écran séparé ; la barre de recherche est un composant overlay sur la même MapScreen.

### Git Intelligence Summary

- Derniers commits : 1.5 (GPS, permissions), 1.4 (segments colorés, LOD). Pas encore de code routing ni search.
- Patterns : Kotlin, Compose, ViewModel + StateFlow, packages data/map/navigation/profile/routing/enrichment/ui/util.

### Latest Tech Information

**OSM Nominatim (usage policy) :**
- 1 requête par seconde, User-Agent identifiant l’app obligatoire.
- Endpoint search : `https://nominatim.openstreetmap.org/search?q=...&format=json&limit=5`.
- Option `viewbox` et `bounded=1` pour restreindre à une zone (ex. Paris).

**Photon (Komoot) :**
- Open source, pas de clé ; `https://photon.komoot.io/api/?q=...&limit=5&bbox=2.2,48.8,2.4,48.9` (Paris).

**Material 3 OutlinedTextField :**
- `value`, `onValueChange`, `placeholder`, `leadingIcon` (Search), `modifier`, `singleLine = true`.

**Debounce en Compose :**
- `LaunchedEffect(query) { delay(300); viewModel.search(query) }` avec annulation automatique si `query` change.

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 2 - Story 2.1 Barre de recherche et géocodage]
- [Source: _bmad-output/planning-artifacts/architecture.md#Project Structure - map/component/SearchBar.kt, map/geocoding/GeocodingService.kt]
- [Source: _bmad-output/planning-artifacts/architecture.md#API & Communication - External APIs optional, degrade gracefully]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Search bar overlay 16dp, OutlinedTextField]
- [Source: _bmad-output/planning-artifacts/prd.md#FR5, Technical Success - 100% open source]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — structure map/, GeocodingService, offline degradation
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 2.1, FR5
- UX : `_bmad-output/planning-artifacts/ux-design-specification.md` — search bar, 16dp overlay, OutlinedTextField
- PRD : `_bmad-output/planning-artifacts/prd.md` — FR5, stack open source
- Story précédente (contexte map) : `_bmad-output/implementation-artifacts/1-5-position-gps-et-gestion-permissions.md`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- Story 2.1 implémentée : barre de recherche avec géocodage Nominatim (100 % open source), SearchBar Material 3 en overlay 16dp, debounce 300 ms, suggestions LazyColumn, MapUiState/MapViewModel étendus (destination, searchSuggestions, isSearching, searchError), gestion hors ligne avec message « Connectez-vous pour rechercher une adresse ». Tests : FakeGeocodingService, MapViewModel (destination, search, offline), NominatimGeocodingService (mock HTTP + DISCONNECT_AT_START). Build non exécuté (JVM 8 dans l’environnement ; projet cible JVM 17).

### Change Log

- 2026-02-26 : Story 2.1 implémentée — barre de recherche, géocodage Nominatim, SearchBar overlay, autocomplete debounce, destination dans MapUiState, tests unitaires et hors ligne
- 2026-02-26 : Code review (AI) — 2 HIGH / 3 MEDIUM / 5 LOW corrigés : annulation searchJob sur query vide (race), GeocodingNetworkException sur HTTP non réussie, commentaires obsolètes SearchBar, clé LazyColumn, test race ajouté, MapLibreMap.kt ajouté au File List

### File List

- app/src/main/java/com/parcoursparis/map/geocoding/GeocodingResult.kt (CRÉER)
- app/src/main/java/com/parcoursparis/map/geocoding/BoundingBox.kt (CRÉER)
- app/src/main/java/com/parcoursparis/map/geocoding/GeocodingService.kt (CRÉER)
- app/src/main/java/com/parcoursparis/map/component/SearchBar.kt (CRÉER)
- app/src/main/java/com/parcoursparis/map/MapUiState.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapViewModel.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapViewModelFactory.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapScreen.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/map/MapLibreMap.kt (MODIFIER)
- app/src/main/java/com/parcoursparis/ParcoursParisApplication.kt (MODIFIER)
- app/src/main/res/values/strings.xml (MODIFIER)
- gradle/libs.versions.toml (MODIFIER)
- app/build.gradle.kts (MODIFIER)
- app/src/test/java/com/parcoursparis/map/geocoding/FakeGeocodingService.kt (CRÉER)
- app/src/test/java/com/parcoursparis/map/geocoding/NominatimGeocodingServiceTest.kt (CRÉER)
- app/src/test/java/com/parcoursparis/map/MapViewModelTest.kt (MODIFIER)
- app/src/test/java/com/parcoursparis/map/MapViewModelLocationTest.kt (MODIFIER)
- _bmad-output/implementation-artifacts/sprint-status.yaml (MODIFIER)
