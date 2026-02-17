---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish', 'step-12-complete']
classification:
  projectType: mobile_app
  domain: general
  complexity: low
  projectContext: greenfield
inputDocuments:
  - _bmad-output/planning-artifacts/product-brief-parcours-paris-2026-02-13.md
  - _bmad-output/brainstorming/brainstorming-session-2026-02-13.md
briefCount: 1
researchCount: 0
brainstormingCount: 1
projectDocsCount: 0
workflowType: 'prd'
---

# Product Requirements Document - parcours-paris

**Author:** Rominche  
**Date:** 2026-02-13

## Executive Summary

**Vision :** Application Android qui transforme les déplacements quotidiens à Paris en opportunités de découverte systématique de la ville.

**Différenciateur :** Algorithme de routing orienté découverte — itinéraires A→B qui privilégient les rues non parcourues (surplus temps ~15 %), contrairement à Maps/Citymapper qui optimisent uniquement pour la rapidité.

**Cible :** Parisien curieux, à pied, qui accepte des trajets légèrement plus longs pour explorer de nouvelles rues. Usage personnel.

## Success Criteria

### User Success

- **Qualité visuelle** : l'interface est agréable et soignée (design cohérent, lisibilité, carte claire).
- **Facilité d'usage** : les actions principales (navigation, marquage manuel, consultation du profil) sont rapides et évidentes.
- **Validation sociale** : des proches demandent à utiliser l'app ou à en avoir une version — signe que la valeur perçue dépasse l'usage personnel.

### Business Success

N/A — Projet personnel. Objectif possible : démontrer une solution utilisable et partageable (portfolio, open source).

### Technical Success

- Carte affichée avec segments colorés (parcouru / non parcouru)
- Marquage manuel fonctionnel
- Navigation A→B avec préférence découverte opérationnelle
- Profil avec statistiques de progression
- Stockage local ≤ 250 Mo
- Stack 100 % open source (OSM, pas d'APIs propriétaires)

### Measurable Outcomes

| Indicateur | Cible | Mesure |
|------------|-------|--------|
| Adoption spontanée | ≥ 1 personne proche qui souhaite l'utiliser | Feedback direct |
| Facilité d'usage | Tâches principales réalisables sans documentation | Test utilisateur informel |
| Qualité visuelle | Interface jugée agréable | Auto-évaluation / retours proches |

## Product Scope

### MVP - Minimum Viable Product

- **Cœur produit & carte** : carte colorée, segments OSM, LOD, correction manuelle
- **Navigation** : itinéraires A→B avec préférence découverte (~15 % temps en plus)
- **Profil** : % Paris parcouru, km, top 3 jours, meilleur mois, récap mensuel
- **Données** : géométrie OSM, stockage local, sync Google optionnelle
- **Enrichissement** : bulles sur la carte (Wikipedia, OSM, photos anciennes)

### Growth Features (Post-MVP)

- Extension à d'autres villes
- Améliorations continues selon usage réel

### Vision (Future)

- Évolutions si adoption par l'entourage
- Fonctionnalités additionnelles selon retours

## User Journeys

### Journey 1 — Navigation A→B (parcours principal)

**Ouverture :** Rominche sort du métro à Bastille. Il veut aller à un rendez-vous place des Vosges. Plutôt que le chemin le plus court, il veut découvrir des rues qu'il n'a jamais prises.

**Montée :** Il ouvre l'app, saisit « Place des Vosges ». L'app propose un itinéraire qui privilégie les rues non parcourues, avec un surplus de temps estimé à ~12 %. Il accepte et suit le trajet. Sur la carte, les segments déjà parcourus sont en vert, les nouveaux en gris. Il traverse une rue qu'il ne connaissait pas.

**Climax :** Une bulle apparaît sur un bâtiment historique. Il clique, lit une courte anecdote Wikipedia, puis repart. Il arrive à destination en ayant découvert 3 nouvelles rues.

**Résolution :** Il se sent content d'avoir progressé dans sa découverte de Paris sans sacrifier son objectif de destination.

### Journey 2 — Marquage manuel (correction)

**Ouverture :** Rominche réalise qu'hier il a oublié d'activer l'app pendant une balade dans le Marais. Des rues qu'il a parcourues apparaissent encore comme « non parcourues ».

**Montée :** Il ouvre l'app sans saisir de destination. La carte s'affiche. Il zoome sur le Marais, sélectionne les tronçons parcourus hier.

**Climax :** Il marque les segments un par un. La carte se met à jour en temps réel. Il voit son pourcentage de Paris parcouru augmenter légèrement.

**Résolution :** Sa carte reflète à nouveau sa vraie progression. Il peut reprendre la navigation en toute confiance.

### Journey 3 — Suivi de progression (motivation)

**Ouverture :** Un dimanche soir, Rominche se demande où il en est dans sa découverte de Paris.

**Montée :** Il ouvre l'app, va dans Profil. Il voit : 23 % de Paris parcouru, 187 km, top 3 jours les plus actifs (dont une grande balade le mois dernier), meilleur mois : mars.

**Climax :** Il consulte le récap mensuel. Il réalise qu'il a découvert 12 nouveaux quartiers ce mois-ci. Il se fixe un objectif pour le mois prochain.

**Résolution :** Il a une vision claire de sa progression et une motivation pour continuer.

### Journey 4 — Cas limite : itinéraire impossible

**Ouverture :** Rominche est à la Défense et veut aller à Montmartre en privilégiant les nouvelles rues. L'app ne trouve pas d'itinéraire satisfaisant (trop de rues déjà parcourues sur le chemin direct).

**Montée :** L'app propose soit un itinéraire « classique » (rapide), soit d'augmenter la tolérance de temps. Il choisit 20 % de surplus. Un itinéraire alternatif apparaît, plus long mais avec des rues inédites.

**Climax :** Il suit le trajet. À un moment, le GPS perd le signal dans une cour. L'app continue d'afficher la carte et le trajet prévu. Quand le GPS revient, la position se recalcule.

**Résolution :** Il arrive à Montmartre en ayant découvert de nouvelles rues, malgré les contraintes du parcours.

### Journey Requirements Summary

| Parcours | Capacités révélées |
|----------|--------------------|
| Navigation A→B | Saisie destination, calcul itinéraire avec préférence découverte, paramètre tolérance, affichage carte segments colorés, bulles enrichissement, suivi GPS |
| Marquage manuel | Mode carte sans destination, sélection segments, marquage/démarquage, mise à jour temps réel, stats progression |
| Suivi progression | Écran Profil, % Paris, km, top jours, meilleur mois, récap mensuel |
| Cas limite | Fallback itinéraire classique, ajustement tolérance, gestion perte GPS, recalcule à la réception |

## Innovation & Novel Patterns

### Detected Innovation Areas

- **Routing orienté découverte** : itinéraires A→B qui privilégient les rues non parcourues, avec surplus de temps maîtrisé (~15 %)
- **Combinaison unique** : navigation + suivi de progression + enrichissement contextuel dans une seule app
- **Unité de base** : segment entre intersections (OSM) comme unité de suivi, incluant rues piétonnes

### Market Context & Competitive Landscape

- **Maps / Citymapper** : optimisent uniquement pour rapidité, pas d'historique de parcours
- **Apps de découverte** : souvent sans navigation A→B
- **Positionnement** : parcours-paris combine les deux

### Validation Approach

- Usage personnel comme premier test
- Validation sociale : intérêt de proches = signe de valeur
- Cible MVP : carte + marquage manuel + navigation découverte opérationnelle

### Risk Mitigation

- **Fallback** : itinéraire classique si aucun chemin « découverte » satisfaisant
- **Paramètre de tolérance** : ajustable si l'algorithme est trop strict
- **Données OSM** : stack open source pour limiter les dépendances

## Mobile App Specific Requirements

### Project-Type Overview

Application Android native (Kotlin) pour la découverte de Paris à pied. Offline-first, données OSM, stockage local, sans backend cloud obligatoire.

### Technical Architecture Considerations

- **Plateforme** : Android uniquement (usage personnel)
- **Stack** : Kotlin, données OSM (géométrie), MapLibre ou équivalent pour la carte
- **Stockage** : Room ou SQLite, limite 250 Mo
- **Données** : géométrie OSM (segments entre intersections), sync Google Timeline optionnelle

### Platform Requirements

- **Android** : cible API récente (à définir)
- **Permissions** : localisation (GPS), stockage
- **Pas de cross-platform** : Android natif uniquement

### Device Permissions

- **Localisation** : continue pour suivi temps réel pendant la navigation
- **Stockage** : lecture/écriture pour données OSM et historique
- **Réseau** : optionnel (téléchargement données, enrichissement Wikipedia)

### Offline Mode

- **Offline-first** : carte et navigation sans connexion
- **Données OSM** : préchargées localement
- **Enrichissement** : chargé à la demande si connexion disponible

### Push Strategy

- **Aucune** : app personnelle, pas de notifications push prévues

### Store Compliance

- **Play Store** : politique de confidentialité, permissions justifiées
- **Pas de monétisation** : app gratuite et open source

### Implementation Considerations

- **LOD** : niveau de détail selon zoom (artères en dézoom, détails en zoom)
- **Segments** : unité = tronçon entre 2 intersections (y compris rues piétonnes)
- **Routing** : algorithme custom avec préférence découverte (~15 % surplus temps)

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

- **Approche MVP** : Experience MVP — prouver que la découverte systématique de Paris apporte une vraie valeur
- **Ressources** : développement solo (Rominche), pas de contrainte de délai

### MVP Feature Set (Phase 1)

**Parcours utilisateur couverts :**
- Navigation A→B avec préférence découverte
- Marquage manuel des segments
- Suivi de progression (profil)

**Capacités indispensables :**
- Carte avec segments colorés (parcouru / non parcouru)
- Marquage / démarquage manuel
- Itinéraires A→B avec préférence découverte (~15 % surplus)
- Profil : % Paris, km, top jours, meilleur mois, récap mensuel
- Stockage local (≤ 250 Mo)
- Bulles d'enrichissement (Wikipedia, OSM)
- LOD selon niveau de zoom

### Post-MVP Features

**Phase 2 (Post-MVP) :**
- Sync Google Timeline (optionnelle)
- Extension à d'autres villes
- Améliorations continues selon usage

**Phase 3 (Expansion) :**
- Évolutions si adoption par l'entourage
- Fonctionnalités additionnelles selon retours

### Risk Mitigation Strategy

- **Technique** : fallback itinéraire classique si routing découverte impossible ; paramètre tolérance ajustable
- **Marché** : validation par usage personnel ; intérêt proches = signal de valeur
- **Ressources** : projet solo sans deadline ; périmètre MVP clair pour rester livrable

## Functional Requirements

### Carte & Visualisation

- FR1: L'utilisateur peut afficher une carte de Paris avec les segments de rues colorés (parcouru / non parcouru)
- FR2: L'utilisateur peut zoomer et dézoomer sur la carte avec un niveau de détail adapté (LOD)
- FR3: L'utilisateur peut naviguer librement sur la carte (pan, zoom) sans destination définie
- FR4: L'utilisateur peut voir sa position actuelle sur la carte lorsque le GPS est activé

### Navigation

- FR5: L'utilisateur peut saisir une adresse ou un lieu de destination
- FR6: L'utilisateur peut obtenir un itinéraire A→B qui privilégie les rues non parcourues
- FR7: L'utilisateur peut ajuster la tolérance de surplus de temps pour l'itinéraire (ex. ~15 %)
- FR8: L'utilisateur peut suivre un itinéraire proposé avec indication de progression
- FR9: L'utilisateur peut obtenir un itinéraire classique (rapide) si aucun itinéraire découverte n'est satisfaisant
- FR10: L'utilisateur peut voir l'itinéraire tracé sur la carte

### Marquage manuel

- FR11: L'utilisateur peut sélectionner des segments sur la carte
- FR12: L'utilisateur peut marquer des segments comme parcourus
- FR13: L'utilisateur peut démarquer des segments (correction d'erreur)
- FR14: L'utilisateur peut voir la carte se mettre à jour en temps réel après un marquage

### Profil & Statistiques

- FR15: L'utilisateur peut consulter le pourcentage de Paris parcouru
- FR16: L'utilisateur peut consulter la distance totale parcourue (km)
- FR17: L'utilisateur peut consulter le top 3 des jours les plus actifs
- FR18: L'utilisateur peut consulter le meilleur mois
- FR19: L'utilisateur peut consulter un récapitulatif mensuel

### Enrichissement

- FR20: L'utilisateur peut voir des bulles d'information sur des lieux de la carte
- FR21: L'utilisateur peut cliquer sur une bulle pour accéder à du contenu enrichi (Wikipedia, OSM, etc.)
- FR22: L'utilisateur peut accéder à ces informations sans surcharger l'interface principale

### Données & Stockage

- FR23: L'application peut stocker l'historique des segments parcourus localement
- FR24: L'application peut fonctionner sans connexion réseau (mode offline)
- FR25: L'utilisateur peut synchroniser ses données avec Google Timeline (optionnel)

### Paramètres & Configuration

- FR26: L'utilisateur peut configurer le paramètre de tolérance de temps pour les itinéraires
- FR27: L'application peut gérer les permissions (localisation, stockage) de manière explicite

## Non-Functional Requirements

### Performance

- **NFR-P1** : L'affichage de la carte et le rendu des segments doivent rester fluides (pas de freeze perceptible lors du zoom/pan)
- **NFR-P2** : Le calcul d'un itinéraire doit se terminer en moins de 5 secondes pour un trajet Paris typique
- **NFR-P3** : Le marquage manuel d'un segment doit mettre à jour la carte en moins de 1 seconde
- **NFR-P4** : Le stockage local ne doit pas dépasser 250 Mo

### Security

- **NFR-S1** : Les données de parcours restent stockées localement sur l'appareil
- **NFR-S2** : Les permissions (localisation, stockage) sont demandées de façon explicite et justifiée

### Integration

- **NFR-I1** : L'app doit pouvoir fonctionner sans connexion réseau (données OSM préchargées)
- **NFR-I2** : Les appels à des APIs externes (Wikipedia, OSM, Google Timeline) doivent gérer l'absence de réseau sans bloquer l'app
