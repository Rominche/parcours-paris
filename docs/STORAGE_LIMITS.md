# Limites de stockage - AC#5 (NFR-P4)

## Exigence
Le stockage local ne doit pas dépasser **250 Mo** (NFR-P4).

## Analyse de l'implémentation actuelle

### Données GeoJSON
- **Fichier de test actuel** : 3 segments (~800 bytes)
- **Estimation Paris réel** : ~50 000 segments (ways OSM)
- **Taille estimée par segment** : ~200 bytes (osm_way_id + geometry_json)
- **Total estimé pour Paris** : 50 000 × 200 bytes = **10 Mo**

### Base de données Room
- **Table segment** : ~10 Mo (voir ci-dessus)
- **Table segment_visit** : 
  - ~100 000 visites max (2× le nombre de segments)
  - 16 bytes par visite (Long segment_id + Long explored_at)
  - Total estimé : 100 000 × 16 = **1.6 Mo**
- **Overhead Room/SQLite** : ~10-20% = **2 Mo**

### Total estimé
**10 Mo (segments) + 1.6 Mo (visits) + 2 Mo (overhead) = ~14 Mo**

## Conclusion
✅ L'implémentation actuelle respecte largement la limite de 250 Mo.

## Validation en production
Pour valider avec des données réelles de Paris :
1. Générer le fichier `paris_segments.geojson` complet depuis OSM
2. Charger dans l'app sur un appareil de test
3. Vérifier la taille de la DB via `adb shell du -h /data/data/com.parcoursparis/databases/`
4. Confirmer que le total reste < 250 Mo

## Facteurs de risque
- **Géométries complexes** : Segments avec beaucoup de points (boulevards, quais)
- **Métadonnées supplémentaires** : Noms de rues, types (si ajoutés post-MVP)
- **Croissance des visits** : Si l'utilisateur marque/démarque fréquemment

## Stratégie d'optimisation (si nécessaire)
1. Simplifier les géométries (Douglas-Peucker algorithm)
2. Compresser geometry_json (GZIP)
3. Limiter l'historique des visits (garder seulement le dernier état)
