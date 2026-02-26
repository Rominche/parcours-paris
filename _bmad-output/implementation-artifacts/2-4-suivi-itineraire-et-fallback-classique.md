# Story 2.4: Suivi de l'itinéraire et fallback classique

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a utilisateur,
I want suivre l'itinéraire proposé avec indication de progression et avoir un fallback classique si besoin,
so that j'arrive à destination même si aucun itinéraire découverte n'est satisfaisant (FR8, FR9).

## Acceptance Criteria

1. **Given** un itinéraire est affiché
   **When** je me déplace, le GPS suit ma position
   **Then** la progression le long de l'itinéraire est indiquée (position, segments franchis)

2. **And** si aucun itinéraire découverte n'est trouvé, l'app propose un itinéraire classique (rapide)

3. **And** je peux choisir entre itinéraire découverte (si trouvé) ou classique

4. **And** en cas de perte GPS, la carte et le trajet restent affichés ; au retour du signal, la position se recalcule

## Tasks / Subtasks

- [x] Implémenter le suivi de progression le long de l'itinéraire (AC: #1)
  - [x] Créer util/RouteProgressUtils.kt : projection point sur polyline, distance restante
  - [x] MapUiState : routeProgressPercent, distanceRemainingMeters
  - [x] MapViewModel : calculer progression à chaque mise à jour userLocation quand route != null
  - [x] RouteBottomSheet : afficher progression (ex. "~500 m restants", "75 % parcouru")
- [x] Implémenter le fallback itinéraire classique (AC: #2)
  - [x] DiscoveryRoutingEngine : exposer computeClassicRoute() (Dijkstra shortest path)
  - [x] MapViewModel : quand computeRoute retourne null, appeler computeClassicRoute et proposer
  - [x] RouteResult : ajouter routeType (DISCOVERY | CLASSIC) pour distinguer
- [x] Permettre le choix découverte vs classique (AC: #3)
  - [x] RouteBottomSheet : bouton "Chemin rapide" si route discovery ; "Chemin découverte" si route classique
  - [x] MapViewModel : onRequestClassicRoute(), onRequestDiscoveryRoute()
  - [x] Gérer l'état : discoveryRoute, classicRoute, route affichée
- [x] Vérifier la robustesse perte GPS (AC: #4)
  - [x] S'assurer que route reste affichée quand userLocation = null (déjà le cas)
  - [x] Pas de crash ni masquage du tracé lors de la perte de signal
- [x] Tests (AC: #1–4)
  - [x] RouteProgressUtils : projection point sur segment, distance restante
  - [x] MapViewModel : progression calculée, fallback classique, choix route
  - [x] DiscoveryRoutingEngine : computeClassicRoute retourne chemin le plus court

### Review Follow-ups (AI)

- [ ] [AI-Review][LOW] AC#1 partiel — "segments franchis" non affichés (uniquement % et distance restante) — à valider avec le PO si un compteur de segments franchis (ex. "3/10") est requis [RouteBottomSheet.kt]

## Dev Notes

### Developer Context

**Contexte Epic 2 :** Les stories 2.1–2.3 sont terminées. La story 2.4 ajoute le **suivi GPS le long de l'itinéraire** (progression, distance restante) et le **fallback classique** quand aucun itinéraire découverte n'est trouvé. L'utilisateur peut choisir entre les deux types d'itinéraire.

**État du code après 2.3 :**
- `MapUiState` : route, userLocation, tolerancePercent, showRouteBottomSheet
- `MapViewModel` : onRequestRoute() appelle DiscoveryRoutingEngine ; retourne null si aucun chemin
- `DiscoveryRoutingEngine` : dijkstraShortest (chemin le plus court) existe déjà en interne ; dijkstraDiscovery (pondéré) pour le discovery
- `RouteBottomSheet` : ETA, distance, ToleranceSlider
- `MapLibreMap` : affiche route et userLocation ; userLocation null → point masqué (features vides)
- Pas de calcul de progression le long de l'itinéraire
- Pas de fallback classique exposé
- Pas de choix découverte/classique

**Contraintes clés :**
- **UX** : Bottom sheet avec progression (ux-design-specification.md) ; fallback explicite (Journey 4)
- **Performance** : calcul progression léger (projection point sur polyline, pas de recalcul Dijkstra)
- **Robustesse** : perte GPS ne doit pas masquer le tracé (NFR-I1)

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| Progression | Projection point GPS sur polyline (RouteResult.geometry) ; distance restante = somme haversine depuis point projeté jusqu'à destination |
| RouteProgressUtils | `projectPointOnPolyline(point, geometry): Int` (index du segment) ; `distanceRemaining(geometry, projectedIndex): Double` |
| RouteType | `enum RouteType { DISCOVERY, CLASSIC }` ; RouteResult étendu ou MapUiState.routeType |
| Fallback classique | DiscoveryRoutingEngine.computeClassicRoute() utilise dijkstraShortest + buildGeometryFromPath (réutiliser code existant) |
| Choix route | MapUiState : discoveryRoute, classicRoute, displayedRoute ; RouteBottomSheet : boutons selon contexte |
| Perte GPS | userLocation = null → route reste affichée ; pas de clear de route |

### Architecture Compliance

**Structure packages (architecture.md) :**
```
app/src/main/java/com/parcoursparis/
├── map/
│   ├── MapLibreMap.kt           # Inchangé (route, userLocation déjà gérés)
│   ├── MapScreen.kt              # MODIFIER — passer routeProgress si pertinent
│   ├── MapViewModel.kt            # MODIFIER — progression, fallback, choix route
│   ├── MapUiState.kt             # MODIFIER — routeProgressPercent, distanceRemainingMeters, routeType, discoveryRoute, classicRoute
│   └── component/
│       └── RouteBottomSheet.kt   # MODIFIER — afficher progression, boutons découverte/classique
├── routing/
│   ├── DiscoveryRoutingEngine.kt # MODIFIER — exposer computeClassicRoute()
│   └── RouteResult.kt           # MODIFIER — routeType: RouteType (optionnel)
├── util/
│   ├── GeoUtils.kt              # Inchangé (haversineMeters)
│   └── RouteProgressUtils.kt    # CRÉER — projection, distance restante
```

**Conventions :**
- Réutiliser dijkstraShortest et buildGeometryFromPath depuis DiscoveryRoutingEngine (ne pas dupliquer)
- RouteType : enum dans routing/ ou map/

### Library & Framework Requirements

**Projection point sur polyline :**
- Algorithme : pour chaque segment [P_i, P_{i+1}], calculer le point projeté ; garder le plus proche
- Formule : projection sur segment = point le plus proche sur la droite ; clamp aux extrémités si hors segment

**DiscoveryRoutingEngine :**
- computeClassicRoute(segments, origin, destination): RouteResult? — chemin le plus court (Dijkstra sans pondération)
- Réutiliser GraphBuilder.build(), findNearestNode, dijkstraShortest, reconstructPath, buildGeometryFromPath

### File Structure Requirements

| Fichier/Dossier | Action |
|----------------|--------|
| `util/RouteProgressUtils.kt` | CRÉER — projectPointOnPolyline, distanceRemaining |
| `routing/DiscoveryRoutingEngine.kt` | MODIFIER — computeClassicRoute() public |
| `routing/RouteResult.kt` | MODIFIER — routeType: RouteType = DISCOVERY |
| `map/MapViewModel.kt` | MODIFIER — progression, fallback, onRequestClassicRoute, onRequestDiscoveryRoute |
| `map/MapUiState.kt` | MODIFIER — routeProgressPercent, distanceRemainingMeters, routeType, discoveryRoute, classicRoute |
| `map/component/RouteBottomSheet.kt` | MODIFIER — progression, boutons découverte/classique |
| `res/values/strings.xml` | MODIFIER — chaînes progression, fallback, choix route |

**Ne pas créer dans cette story :** Marquage automatique des segments franchis (Epic 3), POI bulles (Epic 5).

### Testing Requirements

- **Unit tests** : RouteProgressUtils — projection sur segment droit, distance restante correcte.
- **Unit tests** : DiscoveryRoutingEngine — computeClassicRoute retourne chemin plus court que discovery (si discovery existe).
- **Unit tests** : MapViewModel — quand discovery échoue, classicRoute proposé ; onRequestClassicRoute affiche classic.
- **Unit tests** : MapViewModel — progression mise à jour quand userLocation change et route != null.

### Previous Story Intelligence (2.3)

**2.3** : RouteBottomSheet avec ETA, ToleranceSlider ; MapLibreMap reçoit route en paramètre ; MapViewModel collecte tolerancePercent depuis UserPreferencesRepository ; onToleranceChanged avec debounce 300 ms ; DataStore pour persistance. GeoUtils.haversineMeters dans util/.

**Patterns à réutiliser :**
- MapViewModel : LaunchedEffect/collect pour réagir aux changements (ex. userLocation → recalcul progression)
- RouteBottomSheet : ModalBottomSheet, Column, Text pour ETA/distance — ajouter progression et boutons
- DiscoveryRoutingEngine : code interne dijkstraShortest, buildGeometryFromPath — extraire pour computeClassicRoute

**Attention :** Ne pas casser onToleranceChanged ni la persistance tolérance. Le fallback et le choix route sont des flux additionnels.

### Git Intelligence Summary

- Derniers commits : 2.3 (RouteBottomSheet, ToleranceSlider, DataStore, GeoUtils). Pas de suivi progression ni fallback.
- Patterns : Kotlin, Compose, ViewModel + StateFlow, MapLibre, DataStore.

### Latest Tech Information

**Projection point sur segment (2D) :**
- Soit A, B les extrémités du segment, P le point. Vecteur AP, AB. t = dot(AP, AB) / dot(AB, AB). Point projeté = A + t*(B-A), t clampé [0,1].
- En coordonnées géographiques : approximation plane acceptable pour Paris (petites distances). Utiliser haversineMeters pour distances.

**Dijkstra shortest path :**
- Déjà implémenté dans DiscoveryRoutingEngine.dijkstraShortest. Réutiliser pour computeClassicRoute.

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 2 - Story 2.4 Suivi itinéraire et fallback classique]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Journey 4 - Edge Case No Route]
- [Source: _bmad-output/planning-artifacts/prd.md#FR8, FR9]
- [Source: _bmad-output/implementation-artifacts/2-3-affichage-itineraire-et-parametre-tolerance.md#RouteBottomSheet, MapViewModel]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — routing/, map/component/
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 2.4, FR8, FR9
- UX : `_bmad-output/planning-artifacts/ux-design-specification.md` — Journey 4, fallback
- Story précédente : `_bmad-output/implementation-artifacts/2-3-affichage-itineraire-et-parametre-tolerance.md`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- Suivi progression : RouteProgressUtils avec projectPointOnPolyline et distanceRemaining ; MapViewModel recalcule à chaque userLocation ; RouteBottomSheet affiche "% parcouru · ~X m/km restants"
- Fallback classique : computeClassicRoute exposé dans DiscoveryRoutingEngine ; MapViewModel calcule discovery puis classic, propose classic si discovery null
- Choix route : discoveryRoute et classicRoute stockés ; boutons "Chemin rapide" / "Chemin découverte" selon route affichée
- Perte GPS : route reste affichée quand userLocation = null (updateRouteProgress retourne state inchangé)

### File List

- app/src/main/java/com/parcoursparis/util/RouteProgressUtils.kt (créé)
- app/src/main/java/com/parcoursparis/routing/RouteResult.kt (modifié)
- app/src/main/java/com/parcoursparis/routing/DiscoveryRoutingEngine.kt (modifié)
- app/src/main/java/com/parcoursparis/map/MapUiState.kt (modifié)
- app/src/main/java/com/parcoursparis/map/MapViewModel.kt (modifié)
- app/src/main/java/com/parcoursparis/map/MapScreen.kt (modifié)
- app/src/main/java/com/parcoursparis/map/component/RouteBottomSheet.kt (modifié)
- app/src/main/res/values/strings.xml (modifié)
- app/src/test/java/com/parcoursparis/util/RouteProgressUtilsTest.kt (créé)
- app/src/test/java/com/parcoursparis/routing/DiscoveryRoutingEngineTest.kt (modifié)
- app/src/test/java/com/parcoursparis/routing/FakeDiscoveryRoutingEngine.kt (modifié)
- app/src/test/java/com/parcoursparis/map/MapViewModelTest.kt (modifié)

> Note : `MapLibreMap.kt`, `MapViewModelFactory.kt` et `GraphBuilder.kt` apparaissent comme modifiés dans git (non commités depuis stories 2.2/2.3) mais ne font pas partie du périmètre de la story 2.4.

## Change Log

- 2026-02-26 : Story 2.4 implémentée — suivi progression, fallback classique, choix découverte/classique
- 2026-02-26 : Code review AI — correction HIGH-1 (CancellationException), HIGH-2 (état incohérent), MEDIUM-1 (parallélisation async), LOW-1 (renommage variable), LOW-2 (traçage FakeDiscoveryRoutingEngine)
