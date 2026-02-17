# Validation AC#6 - Mode offline (FR24, NFR-I1)

## Exigence
L'application doit fonctionner **sans connexion réseau** pour toutes les fonctionnalités core.

## Implémentation actuelle

### Chargement des données
✅ **GeoJsonLoader** : Charge depuis `assets/` (stockage local uniquement)
```kotlin
context.assets.open("paris_segments.geojson")  // Pas de réseau
```

### Base de données Room
✅ **AppDatabase** : SQLite local, aucun appel réseau
```kotlin
Room.databaseBuilder(...).build()  // Local uniquement
```

### Dépendances potentiellement réseau
⚠️ **MapLibre** : Peut faire des appels réseau pour :
- Télécharger les tiles de carte (style, sprites, glyphs)
- Récupérer les données vectorielles

## Validation manuelle

### Test 1 : Mode avion
1. Activer le mode avion sur l'appareil
2. Lancer l'app
3. ✅ L'app doit démarrer sans crash
4. ✅ Les segments doivent être chargés depuis la DB
5. ⚠️ La carte MapLibre peut afficher une carte vide si les tiles ne sont pas en cache

### Test 2 : Permissions réseau désactivées
```bash
adb shell cmd appops set com.parcoursparis WIFI_SCAN deny
adb shell cmd appops set com.parcoursparis FINE_LOCATION deny
```

### Test 3 : Logcat sans appels réseau
```bash
adb logcat | grep -i "http\|socket\|network\|connection"
```
Résultat attendu : Aucun appel réseau pendant le chargement des segments.

## Recommandations pour Story 1.3 (MapLibre)

Pour garantir le mode offline complet avec MapLibre :
1. **Utiliser un style offline** : Configurer MapLibre avec un style local
2. **Télécharger les tiles** : Pré-télécharger les tiles de Paris ou utiliser un serveur local
3. **Configurer l'API key** : S'assurer qu'aucune API key externe n'est requise

## Conclusion Story 1.2
✅ **AC#6 respecté** : Aucun appel réseau dans le pipeline OSM et la couche données.
⚠️ **Note** : MapLibre (Story 1.3) nécessitera une configuration offline explicite.
