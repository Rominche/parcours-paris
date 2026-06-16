# parcours-paris

Application Android pour la découverte systématique de Paris à pied.

## Prérequis

- Android Studio Ladybug (2024.2.1) ou plus récent
- **JDK 17+** (requis par Android Gradle Plugin)
- Android SDK (API 24+)

## Configuration

1. Ouvrir le projet dans Android Studio
2. Laisser Android Studio synchroniser les dépendances Gradle
3. Si le wrapper Gradle est manquant : `File > Sync Project with Gradle Files` ou exécuter `gradle wrapper` si Gradle est installé

## Build

```bash
./gradlew assembleDebug
```

Sous Windows :
```cmd
gradlew.bat assembleDebug
```

### Dépannage Java (Windows)

Si le build échoue avec un message du type “**Dependency requires at least JVM runtime version 11**”, c’est que Gradle tourne avec une JVM trop ancienne (souvent Java 8).

- Vérifie:

```cmd
java -version
```

- Puis configure **JAVA_HOME** vers un JDK 17 et rouvre le terminal.

Dans Android Studio, tu peux aussi régler: **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** sur un JDK 17 (ou “Embedded JDK”).

## Lancement

Lancer l'app sur un émulateur ou appareil connecté via Android Studio (Run ▶️).

## Installation de l'APK sur téléphone

1. Télécharger l'APK depuis les **Artifacts** GitHub Actions (workflow *Build APK*) ou le construire localement :
   ```bash
   ./gradlew assembleDebug
   ```
   Fichier produit : `app/build/outputs/apk/debug/app-debug.apk` (~75 Mo).

2. **Si l'installation échoue** avec un message du type *« conflit avec un paquet existant »* ou *« signature incompatible »* :
   - Désinstallez d'abord l'ancienne version de **parcours-paris** sur le téléphone
   - Réinstallez ensuite le nouvel APK

   Ce conflit arrive quand une version précédente a été signée avec une autre clé (Android Studio local vs CI GitHub). À partir de la v0.0.4, tous les builds utilisent une **clé debug partagée** dans `keystore/debug.keystore`.

3. Vérifiez d'avoir au moins **200 Mo** d'espace libre (l'APK est volumineux à cause des données de rues de Paris).

4. Confirmez la version installée dans l'onglet **Paramètres** de l'app.

## Structure du projet

- `app/src/main/java/com/parcoursparis/`
  - `data/` - Room, repositories
  - `map/` - Écran carte
  - `navigation/` - Compose Navigation, bottom nav
  - `profile/` - Écran profil
  - `routing/` - Moteur de routing
  - `enrichment/` - Enrichissement POI
  - `settings/` - Paramètres
  - `ui/theme/` - Material 3 theme
  - `util/` - Utilitaires
