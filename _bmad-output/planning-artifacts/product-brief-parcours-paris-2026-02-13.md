---
stepsCompleted: [1, 2, 3, 4, 5, 6]
inputDocuments:
  - _bmad-output/brainstorming/brainstorming-session-2026-02-13.md
date: 2026-02-13
author: Rominche
---

# Product Brief: parcours-paris

## Executive Summary

**parcours-paris** est une application Android qui aide les Parisiens curieux à découvrir leur ville en transformant leurs déplacements quotidiens en opportunités d'exploration. Contrairement à Maps ou Citymapper qui optimisent pour le chemin le plus rapide, parcours-paris propose des itinéraires A→B qui privilégient les rues non encore parcourues, avec un surplus de temps maîtrisé (~15%). L'utilisateur garde une trace visuelle de son avancement sur une carte et peut enrichir sa découverte via des bulles d'information sur les lieux intéressants.

---

## Core Vision

### Problem Statement

Les personnes qui veulent découvrir Paris de façon systématique (rues, quartiers, chasse aux Invaders) n'ont pas d'outil adapté. La mémoire seule ne suffit pas pour savoir quelles rues ont déjà été parcourues. Les apps de navigation (Maps, Citymapper) optimisent uniquement pour la rapidité et ne proposent pas de parcours favorisant la découverte de nouvelles rues.

### Problem Impact

Sans solution dédiée, l'utilisateur ne peut pas : savoir quelles rues il a déjà parcourues ; planifier des trajets qui maximisent les nouvelles rues ; suivre sa progression dans la découverte de Paris ; bénéficier d'informations contextuelles sur les lieux traversés.

### Why Existing Solutions Fall Short

Maps et Citymapper optimisent pour le chemin le plus court ou le plus rapide. Ils ne prennent pas en compte l'historique de parcours ni l'objectif de découvrir de nouvelles rues. Il n'existe pas d'alternative qui combine navigation A→B et préférence pour les rues non parcourues.

### Proposed Solution

Une application Android (Kotlin) qui : (1) **Navigation avec préférence découverte** — itinéraires A→B qui privilégient les rues non parcourues, avec un surplus de temps limité (~15%) ; (2) **Carte de progression** — visualisation des rues parcourues vs non parcourues ; (3) **Bulles d'enrichissement** — suggestions discrètes sur la carte pour cliquer et en savoir plus sur un lieu quand du contenu existe (Wikipedia, OSM, etc.).

### Key Differentiators

- **Algorithme de routing orienté découverte** : priorité aux nouvelles rues plutôt qu'à la vitesse
- **Paramètre de tolérance** : l'utilisateur accepte un surplus de temps (~15%) pour découvrir
- **Enrichissement contextuel** : bulles d'information sur les lieux traversés, sans surcharger l'interface
- **Open source et gratuit** : données OSM, pas de dépendance à des APIs propriétaires

---

## Target Users

### Primary Users

**Utilisateur unique : le créateur (Rominche)** — Application personnelle, pas de cible marché. Parisien curieux qui souhaite découvrir Paris de façon systématique (rues, chasse aux Invaders), se déplace à pied, et accepte des trajets légèrement plus longs pour explorer de nouvelles rues.

### Secondary Users

N/A — Application à usage personnel uniquement.

### User Journey

**Flux 1 — Navigation A→B :** Ouvrir l'app → saisir une adresse de destination → recevoir un itinéraire proposé (préférence découverte) → suivre le trajet.

**Flux 2 — Marquage manuel :** Ouvrir l'app sans saisir d'adresse → afficher la carte → sélectionner des tronçons → marquer comme parcourus (correction manuelle).

**Flux 3 — Suivi de progression :** Ouvrir l'app → accéder au profil → consulter l'avancement dans la découverte de Paris (stats, % parcouru, etc.).

---

## Success Metrics

### User Success

- **Qualité visuelle** : l'interface est agréable et soignée (design cohérent, lisibilité, carte claire).
- **Facilité d'usage** : les actions principales (navigation, marquage manuel, consultation du profil) sont rapides et évidentes.
- **Validation sociale** : des proches demandent à utiliser l'app ou à en avoir une version — signe que la valeur perçue dépasse l'usage personnel.

### Business Objectives

N/A — Projet personnel. Objectif possible : démontrer une solution utilisable et partageable (portfolio, open source).

### Key Performance Indicators

| Indicateur | Cible | Mesure |
|------------|-------|--------|
| Adoption spontanée | ≥ 1 personne proche qui souhaite l'utiliser | Feedback direct |
| Facilité d'usage | Tâches principales réalisables sans documentation | Test utilisateur informel |
| Qualité visuelle | Interface jugée agréable | Auto-évaluation / retours proches |

---

## MVP Scope

### Core Features (V1 — périmètre complet)

**Cœur produit & carte**
- Carte colorée (parcouru / non parcouru)
- Segments entre intersections (y compris rues piétonnes), données OSM
- LOD : artères en dézoom, détails en zoom
- Correction manuelle : marquer / démarquer des tronçons

**Navigation**
- Itinéraires A→B avec préférence découverte (~15% de temps en plus)
- Temps réel si léger à implémenter

**Profil**
- % Paris parcouru, km
- Top 3 jours les plus actifs, meilleur mois, récap mensuel

**Données & stockage**
- Géométrie OSM, sync Google (optionnel)
- Stockage local, max 250 Mo
- Gratuit, open source uniquement

**Enrichissement**
- Bulles sur la carte : clic pour en savoir plus (Wikipedia, OSM, photos anciennes)

### Out of Scope for MVP

N/A — Tout est prévu dans la V1. Pas de contrainte de délai, projet personnel.

### MVP Success Criteria

- Carte affichée avec segments colorés
- Marquage manuel fonctionnel
- Navigation A→B avec préférence découverte opérationnelle
- Profil avec stats de progression
- Interface claire et agréable

### Future Vision

- Extension à d'autres villes (si partage avec proches)
- Améliorations continues selon usage réel
- Possibles évolutions si adoption par l'entourage

---
