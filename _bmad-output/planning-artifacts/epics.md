---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad-output/planning-artifacts/ux-design-specification.md
---

# parcours-paris - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for parcours-paris, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: L'utilisateur peut afficher une carte de Paris avec les segments de rues colorés (parcouru / non parcouru)
FR2: L'utilisateur peut zoomer et dézoomer sur la carte avec un niveau de détail adapté (LOD)
FR3: L'utilisateur peut naviguer librement sur la carte (pan, zoom) sans destination définie
FR4: L'utilisateur peut voir sa position actuelle sur la carte lorsque le GPS est activé
FR5: L'utilisateur peut saisir une adresse ou un lieu de destination
FR6: L'utilisateur peut obtenir un itinéraire A→B qui privilégie les rues non parcourues
FR7: L'utilisateur peut ajuster la tolérance de surplus de temps pour l'itinéraire (ex. ~15 %)
FR8: L'utilisateur peut suivre un itinéraire proposé avec indication de progression
FR9: L'utilisateur peut obtenir un itinéraire classique (rapide) si aucun itinéraire découverte n'est satisfaisant
FR10: L'utilisateur peut voir l'itinéraire tracé sur la carte
FR11: L'utilisateur peut sélectionner des segments sur la carte
FR12: L'utilisateur peut marquer des segments comme parcourus
FR13: L'utilisateur peut démarquer des segments (correction d'erreur)
FR14: L'utilisateur peut voir la carte se mettre à jour en temps réel après un marquage
FR15: L'utilisateur peut consulter le pourcentage de Paris parcouru
FR16: L'utilisateur peut consulter la distance totale parcourue (km)
FR17: L'utilisateur peut consulter le top 3 des jours les plus actifs
FR18: L'utilisateur peut consulter le meilleur mois
FR19: L'utilisateur peut consulter un récapitulatif mensuel
FR20: L'utilisateur peut voir des bulles d'information sur des lieux de la carte
FR21: L'utilisateur peut cliquer sur une bulle pour accéder à du contenu enrichi (Wikipedia, OSM, etc.)
FR22: L'utilisateur peut accéder à ces informations sans surcharger l'interface principale
FR23: L'application peut stocker l'historique des segments parcourus localement
FR24: L'application peut fonctionner sans connexion réseau (mode offline)
FR25: L'utilisateur peut synchroniser ses données avec Google Timeline (optionnel)
FR26: L'utilisateur peut configurer le paramètre de tolérance de temps pour les itinéraires
FR27: L'application peut gérer les permissions (localisation, stockage) de manière explicite

### NonFunctional Requirements

NFR-P1: L'affichage de la carte et le rendu des segments doivent rester fluides (pas de freeze perceptible lors du zoom/pan)
NFR-P2: Le calcul d'un itinéraire doit se terminer en moins de 5 secondes pour un trajet Paris typique
NFR-P3: Le marquage manuel d'un segment doit mettre à jour la carte en moins de 1 seconde
NFR-P4: Le stockage local ne doit pas dépasser 250 Mo
NFR-S1: Les données de parcours restent stockées localement sur l'appareil
NFR-S2: Les permissions (localisation, stockage) sont demandées de façon explicite et justifiée
NFR-I1: L'app doit pouvoir fonctionner sans connexion réseau (données OSM préchargées)
NFR-I2: Les appels à des APIs externes (Wikipedia, OSM, Google Timeline) doivent gérer l'absence de réseau sans bloquer l'app

### Additional Requirements

**From Architecture:**
- Starter template: Android Studio Empty Activity (Compose) — créer le projet via File > New > New Project, Min SDK API 24+
- Room 2.8.4 pour la persistance locale
- MapLibre 12.3.1 pour le rendu de la carte
- Données OSM: GeoJSON préchargé + Room pour l'état des segments
- ViewModel + Compose State pour l'état UI
- Compose Navigation pour la bottom nav (Map | Profile | Settings)
- Pattern MVVM
- Pas d'authentification (app personnelle)
- APIs optionnelles (Wikipedia, OSM) avec dégradation gracieuse hors ligne
- Unité de segment: OSM way entre intersections
- Structure packages: data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/
- Conventions: snake_case pour Room, PascalCase pour Composables, XxxViewModel, XxxUiState
- Accessibilité: WCAG 2.1 AA (contraste, touch targets 48dp, TalkBack)

**From UX Design:**
- Material Design 3 (Material You) avec Jetpack Compose
- Couleurs sémantiques: vert = parcouru, gris = non parcouru
- Bottom nav: Map | Profile | Settings
- Carte plein écran avec search bar en overlay (16dp padding)
- Bottom sheet pour les détails d'itinéraire en navigation
- Touch targets minimum 48dp
- Mode sombre supporté
- Respect des préférences reduced motion
- Composants custom: MapSegmentLayer, SegmentSelector, EnrichmentBubble, RouteSummaryCard, ProfileStatCard, ToleranceSlider

### FR Coverage Map

FR1: Epic 1 - Carte avec segments colorés (parcouru/non parcouru)
FR2: Epic 1 - Zoom avec LOD adapté
FR3: Epic 1 - Pan/zoom sans destination
FR4: Epic 1 - Position GPS sur la carte
FR5: Epic 2 - Saisie destination
FR6: Epic 2 - Itinéraire privilégiant rues non parcourues
FR7: Epic 2 - Ajustement tolérance surplus temps
FR8: Epic 2 - Suivi itinéraire avec progression
FR9: Epic 2 - Fallback itinéraire classique
FR10: Epic 2 - Itinéraire tracé sur la carte
FR11: Epic 3 - Sélection segments sur la carte
FR12: Epic 3 - Marquage segments parcourus
FR13: Epic 3 - Démarquage segments (correction)
FR14: Epic 3 - Mise à jour temps réel après marquage
FR15: Epic 4 - Pourcentage Paris parcouru
FR16: Epic 4 - Distance totale (km)
FR17: Epic 4 - Top 3 jours les plus actifs
FR18: Epic 4 - Meilleur mois
FR19: Epic 4 - Récapitulatif mensuel
FR20: Epic 5 - Bulles d'information sur lieux
FR21: Epic 5 - Accès contenu enrichi (Wikipedia, OSM)
FR22: Epic 5 - Accès sans surcharger l'interface
FR23: Epic 1 - Stockage local historique segments
FR24: Epic 1 - Mode offline
FR25: Epic 6 - Sync Google Timeline (optionnel, post-MVP)
FR26: Epic 2 - Configuration tolérance temps
FR27: Epic 1 - Gestion permissions (localisation, stockage)

## Epic List

### Epic 1: Carte de Paris et visualisation de la progression
L'utilisateur peut afficher une carte de Paris, naviguer dessus (pan, zoom), voir sa position GPS et les segments déjà parcourus (vert) vs non parcourus (gris). Stockage local et mode offline. Gestion des permissions.
**FRs covered:** FR1, FR2, FR3, FR4, FR23, FR24, FR27

### Epic 2: Navigation orientée découverte
L'utilisateur peut saisir une destination et obtenir un itinéraire qui privilégie les rues non parcourues, avec tolérance de temps et fallback classique.
**FRs covered:** FR5, FR6, FR7, FR8, FR9, FR10, FR26

### Epic 3: Marquage manuel des segments
L'utilisateur peut corriger sa progression en marquant ou démarquant des segments manuellement.
**FRs covered:** FR11, FR12, FR13, FR14

### Epic 4: Profil et statistiques de progression
L'utilisateur peut consulter sa progression : % Paris parcouru, km, top 3 jours, meilleur mois, récap mensuel.
**FRs covered:** FR15, FR16, FR17, FR18, FR19

### Epic 5: Enrichissement contextuel (bulles POI)
L'utilisateur peut voir des bulles d'information sur des lieux et accéder à du contenu enrichi (Wikipedia, OSM) sans surcharger la carte.
**FRs covered:** FR20, FR21, FR22

### Epic 6: Sync et paramètres avancés (post-MVP)
L'utilisateur peut synchroniser ses données avec Google Timeline (optionnel).
**FRs covered:** FR25

---

## Epic 1: Carte de Paris et visualisation de la progression

L'utilisateur peut afficher une carte de Paris, naviguer dessus (pan, zoom), voir sa position GPS et les segments déjà parcourus (vert) vs non parcourus (gris). Stockage local et mode offline. Gestion des permissions.

### Story 1.1: Création du projet Android et structure de base

As a développeur,
I want créer le projet parcours-paris avec le template Android Studio Empty Activity (Compose),
So that j'ai une base fonctionnelle pour construire l'application.

**Acceptance Criteria:**

**Given** Android Studio est installé
**When** je crée un nouveau projet avec le template "Empty Activity"
**Then** le projet est configuré avec : Name (parcours-paris), Kotlin, Min SDK API 24+
**And** les dépendances Room 2.8.4, MapLibre 12.3.1, Material 3 et Compose BOM sont ajoutées
**And** la structure des packages (data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/) est créée
**And** Compose Navigation avec bottom nav (Map | Profile | Settings) ; Map par défaut, Profile et Settings en placeholder
**And** l'app compile et affiche l'écran carte (vide)

### Story 1.2: Pipeline OSM et couche données

As a utilisateur,
I want que l'application charge les segments de rues de Paris depuis des données OSM,
So que la carte puisse afficher la géométrie des rues.

**Acceptance Criteria:**

**Given** un fichier GeoJSON des segments Paris est disponible dans assets/
**When** l'app démarre
**Then** les segments sont chargés et stockés (Room : entities Segment, SegmentVisit ; DAOs)
**And** SegmentRepository expose les segments et l'état exploré/non exploré
**And** le stockage reste ≤ 250 Mo (NFR-P4)
**And** aucune connexion réseau n'est requise (FR24, NFR-I1)

### Story 1.3: Affichage de la carte avec pan et zoom

As a utilisateur,
I want afficher une carte de Paris en plein écran avec pan et zoom,
So que je puisse naviguer librement sur la ville (FR3).

**Acceptance Criteria:**

**Given** l'app est ouverte sur l'écran carte
**When** je fais un geste de pan ou de pinch-to-zoom
**Then** la carte se déplace ou change de niveau de zoom de manière fluide (NFR-P1)
**And** la carte couvre Paris et ses environs
**And** MapLibre est intégré avec le style approprié

### Story 1.4: Couche de segments colorés avec LOD

As a utilisateur,
I want voir les segments de rues colorés (vert = parcouru, gris = non parcouru) sur la carte,
So que je visualise ma progression de découverte (FR1, FR2).

**Acceptance Criteria:**

**Given** la carte est affichée et les données segments sont chargées
**When** je regarde la carte
**Then** les segments sont affichés avec vert pour parcouru, gris pour non parcouru
**And** le niveau de détail (LOD) s'adapte au niveau de zoom (artères en dézoom, détails en zoom)
**And** le rendu reste fluide lors du zoom/pan (NFR-P1)
**And** les segments non encore parcourus sont tous en gris par défaut

### Story 1.5: Position GPS et gestion des permissions

As a utilisateur,
I want voir ma position actuelle sur la carte lorsque le GPS est activé,
So que je sache où je me trouve pendant mes déplacements (FR4).

**Acceptance Criteria:**

**Given** l'app demande la permission de localisation
**When** l'utilisateur accorde la permission
**Then** la position GPS est affichée sur la carte en temps réel
**And** les permissions (localisation, stockage) sont demandées de façon explicite et justifiée (FR27, NFR-S2)
**And** si la permission est refusée, un message clair indique que la position ne sera pas affichée

---

## Epic 2: Navigation orientée découverte

L'utilisateur peut saisir une destination et obtenir un itinéraire qui privilégie les rues non parcourues, avec tolérance de temps et fallback classique.

### Story 2.1: Barre de recherche et géocodage

As a utilisateur,
I want saisir une adresse ou un lieu de destination dans une barre de recherche,
So que je puisse définir ma destination (FR5).

**Acceptance Criteria:**

**Given** je suis sur l'écran carte
**When** je tape dans la barre de recherche en overlay (16dp padding)
**Then** un autocomplete propose des suggestions de lieux (géocodage local ou API)
**And** en sélectionnant une suggestion, la destination est définie
**And** la barre utilise OutlinedTextField Material 3

### Story 2.2: Moteur de routing orienté découverte

As a utilisateur,
I want obtenir un itinéraire A→B qui privilégie les rues non parcourues,
So que je découvre de nouvelles rues tout en allant à destination (FR6).

**Acceptance Criteria:**

**Given** j'ai saisi une destination et ma position est connue
**When** je demande un itinéraire
**Then** DiscoveryRoutingEngine calcule un chemin privilégiant les segments non explorés
**And** le surplus de temps par rapport au chemin le plus court est maîtrisé (~15 % par défaut)
**And** le calcul se termine en moins de 5 secondes pour un trajet Paris typique (NFR-P2)
**And** l'itinéraire est retourné avec géométrie et ETA

### Story 2.3: Affichage de l'itinéraire et paramètre de tolérance

As a utilisateur,
I want voir l'itinéraire tracé sur la carte et ajuster la tolérance de surplus de temps,
So que je puisse obtenir un itinéraire adapté si le premier ne me convient pas (FR7, FR10, FR26).

**Acceptance Criteria:**

**Given** un itinéraire a été calculé
**When** il est affiché
**Then** le tracé apparaît sur la carte (couleur accent)
**And** un bottom sheet affiche ETA et un ToleranceSlider (10–25 %)
**And** en ajustant la tolérance, je peux relancer le calcul
**And** le paramètre est persisté (UserPreference)

### Story 2.4: Suivi de l'itinéraire et fallback classique

As a utilisateur,
I want suivre l'itinéraire proposé avec indication de progression et avoir un fallback classique si besoin,
So que j'arrive à destination même si aucun itinéraire découverte n'est satisfaisant (FR8, FR9).

**Acceptance Criteria:**

**Given** un itinéraire est affiché
**When** je me déplace, le GPS suit ma position
**Then** la progression le long de l'itinéraire est indiquée (position, segments franchis)
**And** si aucun itinéraire découverte n'est trouvé, l'app propose un itinéraire classique (rapide)
**And** je peux choisir entre itinéraire découverte (si trouvé) ou classique
**And** en cas de perte GPS, la carte et le trajet restent affichés ; au retour du signal, la position se recalcule

---

## Epic 3: Marquage manuel des segments

L'utilisateur peut corriger sa progression en marquant ou démarquant des segments manuellement.

### Story 3.1: Sélection de segments sur la carte

As a utilisateur,
I want sélectionner des segments sur la carte par tap,
So que je puisse les marquer ou démarquer (FR11).

**Acceptance Criteria:**

**Given** je suis sur l'écran carte sans destination active
**When** je tape sur un segment
**Then** le segment est mis en évidence (highlight)
**And** les zones tactiles respectent 48dp minimum (accessibilité)
**And** SegmentSelector gère l'interaction

### Story 3.2: Marquage et démarquage de segments

As a utilisateur,
I want marquer des segments comme parcourus ou les démarquer pour corriger une erreur,
So que ma carte reflète ma vraie progression (FR12, FR13).

**Acceptance Criteria:**

**Given** j'ai sélectionné un ou plusieurs segments
**When** je choisis "Marquer parcouru" ou "Marquer non parcouru"
**Then** l'état du segment est mis à jour dans Room (SegmentVisit)
**And** la carte se met à jour en moins de 1 seconde (NFR-P3)
**And** les statistiques de progression sont recalculées

### Story 3.3: Mise à jour temps réel de la carte

As a utilisateur,
I want voir la carte se mettre à jour immédiatement après un marquage,
So que je constate visuellement ma correction (FR14).

**Acceptance Criteria:**

**Given** j'ai marqué ou démarqué des segments
**When** l'action est confirmée
**Then** les segments changent de couleur (vert ↔ gris) sans rechargement
**And** MapViewModel reçoit la mise à jour via SegmentRepository
**And** MapSegmentLayer re-rend les segments modifiés

---

## Epic 4: Profil et statistiques de progression

L'utilisateur peut consulter sa progression : % Paris parcouru, km, top 3 jours, meilleur mois, récap mensuel.

### Story 4.1: Navigation bottom nav et écran Profil

As a utilisateur,
I want accéder à l'écran Profil via la barre de navigation en bas,
So que je puisse consulter mes statistiques (FR15–19).

**Acceptance Criteria:**

**Given** l'app est ouverte
**When** je tape sur l'onglet "Profil" de la bottom nav
**Then** l'écran Profil s'affiche (Compose Navigation)
**And** la bottom nav affiche Map | Profile | Settings
**And** l'écran Profil est accessible en 1 tap

### Story 4.2: Statistiques principales (% Paris, km)

As a utilisateur,
I want consulter le pourcentage de Paris parcouru et la distance totale en km,
So que je mesure ma progression (FR15, FR16).

**Acceptance Criteria:**

**Given** je suis sur l'écran Profil
**When** les données sont chargées
**Then** le pourcentage de Paris parcouru est affiché (ProfileStatCard)
**And** la distance totale parcourue en km est affichée
**And** ProfileRepository agrège les données depuis SegmentVisit
**And** à 0 %, un message "Commencer à explorer" est affiché

### Story 4.3: Top jours et meilleur mois

As a utilisateur,
I want consulter le top 3 des jours les plus actifs et le meilleur mois,
So que je vois mes moments forts d'exploration (FR17, FR18).

**Acceptance Criteria:**

**Given** j'ai des données de parcours
**When** je consulte le Profil
**Then** le top 3 des jours (par km ou segments) est affiché
**And** le meilleur mois est affiché
**And** les données sont triées et agrégées correctement

### Story 4.4: Récapitulatif mensuel

As a utilisateur,
I want consulter un récapitulatif mensuel de ma découverte,
So que j'ai une vision claire de ma progression sur la période (FR19).

**Acceptance Criteria:**

**Given** je suis sur l'écran Profil
**When** je consulte le récap mensuel
**Then** MonthlyRecapCard affiche les stats du mois (nouveaux quartiers, km, etc.)
**And** je peux naviguer entre les mois si pertinent
**And** le récap est lisible et motivant

---

## Epic 5: Enrichissement contextuel (bulles POI)

L'utilisateur peut voir des bulles d'information sur des lieux et accéder à du contenu enrichi (Wikipedia, OSM) sans surcharger la carte.

### Story 5.1: Affichage des bulles POI sur la carte

As a utilisateur,
I want voir des bulles d'information sur des lieux remarquables de la carte,
So que je puisse découvrir du contexte sur mon parcours (FR20).

**Acceptance Criteria:**

**Given** je suis sur la carte avec un niveau de zoom suffisant
**When** des POI sont à proximité
**Then** des icônes/bulles discrètes apparaissent sur la carte
**And** la carte reste lisible (pas de surcharge)
**And** EnrichmentBubble est utilisé pour l'affichage

### Story 5.2: Accès au contenu enrichi (Wikipedia, OSM)

As a utilisateur,
I want cliquer sur une bulle pour accéder au contenu enrichi (Wikipedia, OSM),
So que j'en sache plus sur un lieu (FR21, FR22).

**Acceptance Criteria:**

**Given** une bulle POI est visible
**When** je tape dessus
**Then** un bottom sheet ou dialog affiche le contenu (extrait Wikipedia, infos OSM)
**And** si hors ligne, un message indique que le contenu n'est pas disponible (NFR-I2)
**And** l'interface principale n'est pas surchargée
**And** EnrichmentService gère les appels API avec dégradation gracieuse

---

## Epic 6: Sync et paramètres avancés (post-MVP)

L'utilisateur peut synchroniser ses données avec Google Timeline (optionnel).

### Story 6.1: Synchronisation Google Timeline (optionnelle)

As a utilisateur,
I want synchroniser mes données de parcours avec Google Timeline,
So que je puisse importer des trajets enregistrés automatiquement (FR25).

**Acceptance Criteria:**

**Given** Google Timeline est activé et l'intégration est configurée
**When** je lance une synchronisation
**Then** les trajets pertinents sont importés et les segments correspondants sont marqués
**And** l'app gère l'absence de réseau sans blocage (NFR-I2)
**And** la sync est optionnelle et peut être désactivée
