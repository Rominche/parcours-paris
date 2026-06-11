# AGENTS.md

## Cursor Cloud specific instructions

Application Android mono-module (`:app`), `parcours-paris`. Outils : Gradle wrapper 8.9, AGP 8.7.2, Kotlin 2.0.21, **JDK 17**, `compileSdk`/`targetSdk` 35, `minSdk` 24.

### Commandes (voir aussi `README.md` et `.github/workflows/build-apk.yml`)
- Build (porte CI) : `./gradlew assembleDebug` → APK dans `app/build/outputs/apk/debug/app-debug.apk`.
- Tests unitaires JVM : `./gradlew testDebugUnitTest`.

### Caveats non évidents
- **JDK 17 requis.** La VM contient aussi un JDK 21 ; Gradle est épinglé sur le JDK 17 via `~/.gradle/gradle.properties` (`org.gradle.java.home`). Ne pas mettre `org.gradle.java.home` dans le `gradle.properties` du repo.
- **Android SDK** : installé sous `~/android-sdk`, câblé par `local.properties` (`sdk.dir`, fichier git-ignoré). `JAVA_HOME`/`ANDROID_HOME` sont aussi exportés dans `~/.bashrc`.
- **Pas de `/dev/kvm`** dans la VM cloud : aucun émulateur Android ni test instrumenté (`androidTest`/`connectedCheck`) ne peut s'exécuter. Valider uniquement via `assembleDebug` + tests unitaires.
- **`testDebugUnitTest` sort en échec (18/50) — pré-existant, non lié à l'environnement.** Plusieurs tests appellent `org.json` (via `GraphBuilder`) sans runner Robolectric → `RuntimeException: ... org.json.JSONArray not mocked`; quelques échecs d'assets/NPE en aval. 32 tests passent (ex. `SegmentRepositoryTest`, `RouteProgressUtilsTest`, `SegmentGeoJsonConverterTest`).
- **`./gradlew lintDebug` plante** sur un `IncompatibleClassChangeError` interne au détecteur `RememberInComposition` d'AGP 8.7.2 (bug outil, pas le code) ; lint n'est pas dans le CI.
- L'asset `app/src/main/assets/paris_segments.geojson` (~35 Mo) est déjà commité ; `scripts/fetch_paris_streets.py` sert seulement à le régénérer.
