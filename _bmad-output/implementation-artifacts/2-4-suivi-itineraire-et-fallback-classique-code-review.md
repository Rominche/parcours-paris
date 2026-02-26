# Code Review — Story 2.4 : Suivi itinéraire et fallback classique

**Story:** 2-4-suivi-itineraire-et-fallback-classique  
**Date:** 2026-02-26  
**Reviewer:** AI (adversarial)  
**Git vs Story Discrepancies:** 3 (fichiers modifiés non listés dans la story)  
**Issues trouvées:** 2 High, 3 Medium, 2 Low  

---

## 🔴 CRITICAL / HIGH

### HIGH-1 : `CancellationException` capturée et affichée comme erreur utilisateur

**Fichier:** `MapViewModel.kt` lignes 291-299

```kotlin
} catch (e: Exception) {
    _uiState.update {
        it.copy(
            isComputingRoute = false,
            route = null,
            routeError = e.message ?: "Erreur de calcul d'itinéraire"
        )
    }
}
```

**Problème :** En Kotlin, l'annulation d'une coroutine lance `CancellationException`. Ici, tout `Exception` est capturé, y compris `CancellationException`. Conséquences :
- Quand l'utilisateur change rapidement la tolérance (debounce 300 ms), le job précédent est annulé
- Le message d'erreur affiché peut être "StandaloneCoroutine was cancelled" ou similaire
- `isComputingRoute` peut rester à `true` si on ne réinjecte pas correctement l'état après annulation

**Correction :** Rethrow `CancellationException` et ne traiter que les vraies erreurs :

```kotlin
} catch (e: CancellationException) {
    _uiState.update { it.copy(isComputingRoute = false) }
    throw e
} catch (e: Exception) {
    _uiState.update {
        it.copy(
            isComputingRoute = false,
            route = null,
            discoveryRoute = null,
            classicRoute = null,
            routeError = e.message ?: "Erreur de calcul d'itinéraire"
        )
    }
}
```

---

### HIGH-2 : Incohérence d'état en cas d'exception — `discoveryRoute` et `classicRoute` non réinitialisés

**Fichier:** `MapViewModel.kt` lignes 291-298

**Problème :** En cas d'exception pendant le calcul (réseau, crash moteur, etc.), on met `route = null` et `routeError`, mais on ne remet pas à zéro `discoveryRoute` et `classicRoute`. Un état précédent peut rester en mémoire et provoquer des comportements incohérents (ex. boutons "Chemin rapide" / "Chemin découverte" visibles alors qu'il n'y a pas de route affichée).

**Correction :** Inclure `discoveryRoute = null` et `classicRoute = null` dans l’update du bloc `catch`.

---

## 🟡 MEDIUM

### MEDIUM-1 : Calcul discovery et classique séquentiel — opportunité de parallélisation

**Fichier:** `MapViewModel.kt` lignes 254-265

**Problème :** `computeRoute` et `computeClassicRoute` sont appelés l’un après l’autre. Chaque appel fait un Dijkstra complet. Pour une meilleure UX, les deux calculs peuvent être lancés en parallèle :

```kotlin
coroutineScope {
    val discoveryDeferred = async {
        discoveryRoutingEngine.computeRoute(...)
    }
    val classicDeferred = async {
        discoveryRoutingEngine.computeClassicRoute(...)
    }
    val discoveryResult = discoveryDeferred.await()
    val classicResult = classicDeferred.await()
    // ...
}
```

---

### MEDIUM-2 : Fichiers modifiés non listés dans la story

**Fichiers concernés :** `MapLibreMap.kt`, `MapViewModelFactory.kt`, `GraphBuilder.kt`

**Problème :** Ces fichiers apparaissent dans `git status` comme modifiés, mais ne figurent pas dans la section "File List" de la story 2.4. Cela peut venir de stories précédentes (2.2, 2.3), mais la story 2.4 devrait documenter clairement tous les fichiers impactés pour faciliter la traçabilité.

**Action :** Mettre à jour la File List de la story ou préciser que ces changements proviennent de stories antérieures.

---

### MEDIUM-3 : AC#1 — "segments franchis" non affichés

**Story AC#1 :** "la progression le long de l'itinéraire est indiquée (position, segments franchis)"

**Implémentation actuelle :** `RouteBottomSheet` affiche uniquement `routeProgressPercent` et `distanceRemainingMeters` (ex. "75 % parcouru · ~500 m restants"). Aucun compteur de segments franchis (ex. "3/10 segments").

**Impact :** L’AC est partiellement couverte. La position et la distance restante sont bien présentes, mais pas le nombre de segments franchis. À clarifier avec le product owner si c’est acceptable.

---

## 🟢 LOW

### LOW-1 : Nom de variable trompeur dans `RouteProgressUtils`

**Fichier:** `RouteProgressUtils.kt` ligne 19

```kotlin
var bestDistSq = Double.POSITIVE_INFINITY
// ...
if (d < bestDistSq) {
    bestDistSq = d
```

**Problème :** La variable s’appelle `bestDistSq` (distance au carré) mais stocke une distance en mètres (`haversineMeters`). Le nom est trompeur.

**Correction :** Renommer en `bestDist` ou `bestDistanceMeters`.

---

### LOW-2 : `FakeDiscoveryRoutingEngine` — pas de traçage des appels à `computeClassicRoute`

**Fichier:** `FakeDiscoveryRoutingEngine.kt`

**Problème :** `computeCalls` enregistre uniquement les appels à `computeRoute`, pas ceux à `computeClassicRoute`. Les tests ne peuvent pas vérifier que `computeClassicRoute` est bien appelé (par ex. pour le fallback).

**Correction :** Ajouter un `classicComputeCalls` ou étendre `computeCalls` pour inclure les appels à `computeClassicRoute`.

---

## ✅ Validation des AC et tâches

| AC / Tâche | Statut | Preuve |
|------------|--------|--------|
| AC#1 Progression GPS | ✅ Implémenté | `RouteProgressUtils`, `MapViewModel.updateRouteProgress`, `RouteBottomSheet` |
| AC#2 Fallback classique | ✅ Implémenté | `computeClassicRoute`, `MapViewModel.onRequestRoute` |
| AC#3 Choix découverte/classique | ✅ Implémenté | Boutons dans `RouteBottomSheet`, `onRequestClassicRoute` / `onRequestDiscoveryRoute` |
| AC#4 Perte GPS | ✅ Implémenté | `updateRouteProgress` retourne l’état inchangé si `userLocation == null` |
| Tâche RouteProgressUtils | ✅ | `projectPointOnPolyline`, `distanceRemaining` |
| Tâche computeClassicRoute | ✅ | `DiscoveryRoutingEngine.computeClassicRoute` |
| Tâche MapViewModel progression | ✅ | `updateRouteProgress` appelé à chaque mise à jour de `userLocation` |
| Tâche RouteBottomSheet | ✅ | Progression, boutons découverte/classique |
| Tests | ✅ | `RouteProgressUtilsTest`, `MapViewModelTest`, `DiscoveryRoutingEngineTest` |

---

## Recommandation

**Statut proposé :** `in-progress` — corriger les 2 issues HIGH avant de passer en `done`.

---

*Reviewer: RomainLAMBERT on 2026-02-26*
