# Scripts Parcours Paris

## fetch_paris_streets.py

Récupère **toutes les rues de Paris** depuis OpenStreetMap et génère `paris_segments.geojson`.

### Prérequis

```bash
pip install requests
```

### Usage

```bash
python scripts/fetch_paris_streets.py
```

Le fichier est écrit dans `app/src/main/assets/paris_segments.geojson`.

### Détails

- Utilise l'API Overpass (overpass.kumi.systems)
- 16 tuiles pour couvrir Paris intra-muros
- ~160 000 segments, ~30–35 Mo
- Délai 8 s entre requêtes pour éviter le rate limit

### En cas d'erreur 429 (rate limit)

Relancez le script plus tard ou utilisez un autre réseau.
