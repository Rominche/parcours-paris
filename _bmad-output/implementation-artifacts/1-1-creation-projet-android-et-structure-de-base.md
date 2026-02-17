# Story 1.1: Création du projet Android et structure de base

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a développeur,
I want créer le projet parcours-paris avec le template Android Studio Empty Activity (Compose),
So that j'ai une base fonctionnelle pour construire l'application.

## Acceptance Criteria

1. **Given** Android Studio est installé
2. **When** je crée un nouveau projet avec le template "Empty Activity"
3. **Then** le projet est configuré avec : Name (parcours-paris), Kotlin, Min SDK API 24+
4. **And** les dépendances Room 2.8.4, MapLibre 12.3.1, Material 3 et Compose BOM sont ajoutées
5. **And** la structure des packages (data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/) est créée
6. **And** Compose Navigation avec bottom nav (Map | Profile | Settings) ; Map par défaut, Profile et Settings en placeholder
7. **And** l'app compile et affiche l'écran carte (vide)

## Tasks / Subtasks

- [x] Créer le projet Android via Android Studio (AC: #3)
  - [x] File > New > New Project > Empty Activity
  - [x] Configurer : Name (parcours-paris), Package (com.parcoursparis), Kotlin, Min SDK API 24+
- [x] Ajouter les dépendances dans app/build.gradle.kts (AC: #4)
  - [x] Room 2.8.4 (runtime, ktx, compiler via KSP)
  - [x] MapLibre 12.3.1 (org.maplibre.gl:android-sdk)
  - [x] Material 3 et Compose BOM (2025.12.00 ou équivalent)
- [x] Créer la structure des packages (AC: #5)
  - [x] data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/
- [x] Configurer Compose Navigation avec bottom nav (AC: #6)
  - [x] NavHost avec routes Map, Profile, Settings
  - [x] MapScreen par défaut (écran carte vide)
  - [x] ProfileScreen et SettingsScreen en placeholder
- [x] Vérifier compilation et affichage (AC: #7)
  - [x] L'app compile sans erreur (`gradlew.bat assembleDebug`)
  - [x] L'écran carte s'affiche au lancement (Map = startDestination)

## Dev Notes

### Developer Context

**Contexte Epic 1 :** Carte de Paris et visualisation de la progression. Cette story pose les fondations : projet Android, structure de packages, navigation, écran carte vide. Les stories suivantes (1.2 à 1.5) ajouteront les données OSM, le rendu MapLibre, les segments colorés et le GPS.

**Pas de story précédente** — C'est la première story du projet. Aucun code existant à réutiliser.

**Points critiques à ne pas manquer :**
- Utiliser le template **Empty Activity** (Compose), pas Basic Activity ou autre
- Min SDK **API 24+** (Android 7.0) — requis par l'architecture
- Package name : `com.parcoursparis`
- Pas de modularisation pour le MVP — single-module app

### Technical Requirements

| Exigence | Spécification |
|----------|---------------|
| Template | Android Studio Empty Activity (Compose) |
| Langage | Kotlin |
| Min SDK | API 24+ |
| Room | 2.8.4 (runtime, ktx, compiler KSP) |
| MapLibre | 12.3.1 (org.maplibre.gl:android-sdk) |
| Material 3 | Via Compose BOM |
| Compose BOM | 2025.12.00 ou version stable récente |
| Navigation | Compose Navigation |
| Pattern | MVVM (ViewModel + Compose State) |

### Architecture Compliance

**Structure packages obligatoire** (architecture.md) :
```
app/src/main/java/com/parcoursparis/
├── data/           # Room, repositories (vide pour l'instant)
├── map/            # MapScreen, MapViewModel (écran carte vide)
├── navigation/     # ParcoursNavHost, NavRoutes, BottomNavBar
├── profile/        # ProfileScreen placeholder
├── routing/        # (vide pour l'instant)
├── enrichment/     # (vide pour l'instant)
├── ui/             # theme/ (Color, Theme, Type)
└── util/           # (vide pour l'instant)
```

**Conventions naming** (architecture.md) :
- Packages : lowercase, feature-based
- Composables : PascalCase (MapScreen, ProfileScreen)
- ViewModels : XxxViewModel (MapViewModel)
- Fichiers : un type principal par fichier

**Bottom nav** : Map | Profile | Settings — Map par défaut. Profile et Settings = écrans placeholder (Scaffold + Text).

### Library & Framework Requirements

**Room 2.8.4** — Ajouter dans app/build.gradle.kts :
```kotlin
val room_version = "2.8.4"
implementation("androidx.room:room-runtime:$room_version")
ksp("androidx.room:room-compiler:$room_version")
implementation("androidx.room:room-ktx:$room_version")
```
*Note :* Activer le plugin KSP dans le module app.

**MapLibre 12.3.1** — Maven Central requis :
```kotlin
implementation("org.maplibre.gl:android-sdk:12.3.1")
```

**Material 3 + Compose BOM** :
```kotlin
implementation(platform("androidx.compose:compose-bom:2025.12.00"))
implementation("androidx.compose.material3:material3")
// + autres dépendances Compose (ui, foundation, etc.)
```

### File Structure Requirements

| Fichier/ dossier | Action |
|------------------|--------|
| `MainActivity.kt` | Conserver, configurer pour ParcoursParisApp + NavHost |
| `ParcoursParisApp.kt` | Créer si absent (Composable root) |
| `navigation/ParcoursNavHost.kt` | NavHost avec 3 routes |
| `navigation/NavRoutes.kt` | Constantes "map", "profile", "settings" |
| `navigation/BottomNavBar.kt` | BottomNavigationBar 3 items |
| `map/MapScreen.kt` | Écran carte vide (Box ou placeholder) |
| `profile/ProfileScreen.kt` | Placeholder (Text "Profil") |
| `settings/SettingsScreen.kt` | Placeholder (Text "Paramètres") — ou dans profile/ selon structure |
| `ui/theme/` | Color.kt, Theme.kt, Type.kt (Material 3) |

*Note :* L'architecture mentionne `settings/` comme package séparé. Créer `settings/SettingsScreen.kt` pour cohérence.

### Testing Requirements

- **Unit tests** : Non requis pour cette story (structure uniquement)
- **Instrumented tests** : Non requis pour cette story
- **Validation manuelle** : L'app doit compiler et afficher l'écran Map au lancement

### Latest Tech Information (Web Research)

- **Room 2.8.4** : Version stable actuelle (nov. 2025). Utiliser KSP pour le compiler (remplace kapt).
- **MapLibre 12.3.1** : Version stable actuelle. Artifact `org.maplibre.gl:android-sdk`. S'assurer que `mavenCentral()` est dans les repositories.
- **Compose BOM** : Utiliser une version récente (ex. 2025.12.00) pour aligner les versions Compose.

### Project Context Reference

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 1]
- [Source: _bmad-output/planning-artifacts/architecture.md#Starter Template, Project Structure]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Design System, Bottom Navigation]
- [Source: _bmad-output/planning-artifacts/prd.md#Mobile App Specific Requirements]

### References

- Architecture : `_bmad-output/planning-artifacts/architecture.md` — sections Starter Template, Project Structure, Naming Patterns
- UX : `_bmad-output/planning-artifacts/ux-design-specification.md` — Bottom nav Map | Profile | Settings, Material 3
- Epics : `_bmad-output/planning-artifacts/epics.md` — Story 1.1 acceptance criteria

## Dev Agent Record

### Agent Model Used

gpt-5.3-codex

### Debug Log References

### Completion Notes List

- Projet Android créé avec structure Empty Activity (Compose)
- Dépendances : Room 2.8.4, MapLibre 12.3.1, Material 3, Compose BOM 2025.08.01
- Structure packages : data/, map/, navigation/, profile/, routing/, enrichment/, ui/, util/, settings/
- Compose Navigation avec bottom nav (Map | Profile | Settings), Map par défaut
- Écrans : MapScreen (vide), ProfileScreen et SettingsScreen (placeholders)
- Thème Material 3 (Color, Theme, Type)
- **Validation :** Ouvrir le projet dans Android Studio et exécuter `./gradlew assembleDebug` ou `gradlew.bat assembleDebug`. S'assurer d'utiliser **JDK 17+** (sinon l'AGP échoue).
- **Code review (AI) :** incohérences corrigées dans la documentation (File List complétée, modèle agent renseigné). Wrapper Gradle réparé (jar ajouté). Compilation encore bloquée si la JVM utilisée est < 11 (Java 8).

### File List

- settings.gradle.kts
- build.gradle.kts
- gradle.properties
- gradle/libs.versions.toml
- gradle/wrapper/gradle-wrapper.properties
- gradle/wrapper/gradle-wrapper.jar
- gradlew
- gradlew.bat
- app/build.gradle.kts
- app/proguard-rules.pro
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/parcoursparis/MainActivity.kt
- app/src/main/java/com/parcoursparis/ParcoursParisApp.kt
- app/src/main/java/com/parcoursparis/data/.gitkeep
- app/src/main/java/com/parcoursparis/enrichment/.gitkeep
- app/src/main/java/com/parcoursparis/navigation/NavRoutes.kt
- app/src/main/java/com/parcoursparis/navigation/BottomNavBar.kt
- app/src/main/java/com/parcoursparis/navigation/ParcoursNavHost.kt
- app/src/main/java/com/parcoursparis/map/MapScreen.kt
- app/src/main/java/com/parcoursparis/profile/ProfileScreen.kt
- app/src/main/java/com/parcoursparis/routing/.gitkeep
- app/src/main/java/com/parcoursparis/settings/SettingsScreen.kt
- app/src/main/java/com/parcoursparis/ui/theme/Color.kt
- app/src/main/java/com/parcoursparis/ui/theme/Theme.kt
- app/src/main/java/com/parcoursparis/ui/theme/Type.kt
- app/src/main/java/com/parcoursparis/util/.gitkeep
- app/src/main/res/values/strings.xml
- app/src/main/res/values/colors.xml
- app/src/main/res/values/themes.xml
- app/src/main/res/drawable/ic_launcher_foreground.xml
- app/src/main/res/drawable/ic_launcher_background.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
- .gitignore
- README.md

### Change Log

- 2026-02-17 : Story 1.1 implémentée — projet Android parcours-paris créé avec Empty Activity (Compose), dépendances Room/MapLibre/Material 3, structure packages, navigation bottom nav (Map|Profile|Settings)
- 2026-02-17 : Code review AI — corrections de traçabilité (File List + Agent Model), statut remis à in-progress; wrapper Gradle réparé; compilation bloquée tant que Gradle tourne en Java 8
- 2026-02-17 : Fix build — import `androidx.compose.runtime.Composable` ajouté; build OK sous JBR 21 (`gradlew.bat assembleDebug`)

## Senior Developer Review (AI)

### Résultat

- **High résolu :** build validé avec JBR 21 (Android Studio JBR) + wrapper réparé.
- **Medium corrigés :** File List alignée avec les fichiers réellement présents (`.gitkeep`) et métadonnée Agent Model renseignée.

### Actions appliquées

- Tâche de compilation repassée en non terminée (`[ ]`) pour refléter l'état réel.
- Story status repassé de `review` à `in-progress`.
- Ajout de `gradle/wrapper/gradle-wrapper.jar` pour rendre le wrapper exécutable.
