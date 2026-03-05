# Évolution POC → Production

Document de référence pour faire évoluer le POC Parcours Paris vers une version production.

---

## 1. Carte et tuiles (MapLibre + OpenStreetMap)

### État actuel (POC)

- **Moteur de rendu** : MapLibre Android SDK 12.3.1
- **Source de tuiles** : OpenStreetMap (raster) via `tile.openstreetmap.org`
- **Style** : `app/src/main/assets/osm_style.json` — RasterSource avec tuiles OSM
- **Données superposées** : segments (GeoJsonSource), itinéraire, position GPS (CircleLayer)

```
MapLibre (rendu) + osm_style.json (tuiles OSM) + GeoJsonSource (segments, route, GPS)
```

### Points d’attention pour la production

#### 1.1 Politique d’utilisation des tuiles OSM

Les tuiles OSM ([Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)) imposent notamment :

- **User-Agent** : identifier clairement l’application (ex. `ParcoursParis/1.0 (contact@example.com)`)
- **Référent** : URL ou identifiant de l’app
- **Pas de surcharge** : éviter les requêtes excessives (cache, préchargement, etc.)

**Action** : Configurer un User-Agent dédié dans les requêtes HTTP des tuiles (OkHttp, MapLibre ou client HTTP utilisé par le SDK).

#### 1.2 Alternative : fournisseur de tuiles tiers

Pour une utilisation plus intensive ou des contraintes de SLA :

- **Stadia Maps** : tuiles OSM, plan gratuit limité
- **MapTiler** : tuiles OSM/Mapbox, plan gratuit
- **Self-hosted** : serveur de tuiles OSM (TileServer GL, OpenMapTiles) pour un contrôle total

#### 1.3 Mode offline

Actuellement, les tuiles sont chargées à la demande. Pour un usage hors ligne :

- Pré-télécharger les tuiles pour une zone (ex. Paris)
- Utiliser un style local avec tuiles embarquées ou cache persistant
- Voir `docs/OFFLINE_VALIDATION.md` pour le contexte existant

---

## 2. Géocodage (Nominatim)

### État actuel

- **Service** : Nominatim (OSM) via `nominatim.openstreetmap.org`
- **Implémentation** : `NominatimGeocodingService.kt`
- **Limites** : 1 req/s, usage fair (voir [Usage Policy](https://operations.osmfoundation.org/policies/nominatim/))

### Production

- **Self-hosted Nominatim** ou **Photon** pour plus de requêtes
- Ou passer par un fournisseur payant (Google, Mapbox, etc.) si besoin de volume

---

## 3. Données et stockage

- Voir `docs/STORAGE_LIMITS.md` pour la limite 250 Mo
- GeoJSON des segments chargé depuis `assets/` (pas de réseau pour les données core)

---

## 4. Checklist production (résumé)

| Composant      | POC                          | Production à prévoir                          |
|----------------|------------------------------|-----------------------------------------------|
| Tuiles carte   | OSM direct                   | User-Agent, cache, éventuellement fournisseur tiers |
| Géocodage      | Nominatim public             | Self-hosted ou fournisseur dédié               |
| Mode offline   | Tuiles à la demande          | Préchargement / cache persistant               |
| Monitoring     | Aucun                        | Crash reporting, analytics (RGPD)              |
| Sécurité       | API keys vides ou publiques  | Secrets, ProGuard, obfuscation                 |

---

## 5. Fichiers clés à modifier pour la production

- `app/src/main/assets/osm_style.json` — source de tuiles, éventuellement URL d’un fournisseur
- `ParcoursParisApplication.kt` — initialisation MapLibre, configuration tile server
- `NominatimGeocodingService.kt` — endpoint, User-Agent, gestion des limites
- `build.gradle.kts` — versions, ProGuard, build flavors (debug/release)

---

*Document créé le 2026-03-05. À mettre à jour au fur et à mesure des évolutions.*
