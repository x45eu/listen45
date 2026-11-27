# Android-Freigabe

Die öffentliche APK wird mit dem bestehenden Listen45-Schlüssel signiert. Android prüft den Herausgeber bei Updates anhand des Signaturzertifikats. Der Keystore und seine Passwörter liegen beim Projekteigentümer.

1. Prüfe den GitHub-Actions-Lauf für den Commit der geplanten Veröffentlichung. Verwende daraus das Artefakt `listen45-release-unsigned` oder baue am selben Commit mit `./gradlew assembleRelease`.
2. Signiere die APK mit Android SDK Build-Tools 35.0.0. Das Skript fragt das Keystore-Passwort interaktiv ab und gibt die Prüfsumme der APK und den SHA-256-Fingerabdruck des Zertifikats aus.

   ```sh
   ./scripts/sign-release.sh ./listen45.p12 listen45 Listen45-0.1.0.apk ./app-release-unsigned.apk
   ```

3. Prüfe den Zertifikat-Fingerabdruck gegen den vorhandenen Schlüssel. Installiere die signierte APK und prüfe Start, Referenzimport, Erkennung und Teilen. Eine zuvor installierte Debug-APK hat einen anderen Schlüssel. Ein direkter Wechsel zur offiziellen APK ist damit nicht möglich. Ein zuvor exportierter Verlauf lässt sich wieder per JSON importieren.
4. Veröffentliche auf GitHub den Tag `v0.1.0` am geprüften Commit mit `Listen45-0.1.0.apk`, APK-Prüfsumme und Zertifikat-Fingerabdruck. GitHub stellt den zugehörigen GPL-Quellstand über den Tag bereit.
