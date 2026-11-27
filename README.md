# Listen45

**Recognize music. Not users.**

Listen45 ist eine native Android-App zur Offline-Erkennung von Musik mit einem eigenen Referenzkatalog. Die App verarbeitet Mikrofonaufnahmen auf dem Gerät und speichert den Erkennungsverlauf und Favoriten lokal.

## Musik erkennen

1. Öffne **Referenzbibliothek** und wähle **WAV-Referenz hinzufügen**. Wähle eine 16-Bit-PCM-WAV-Datei (Mono oder Stereo, 11.025–192 kHz), gib einen Titel ein und speichere den Eintrag. Die Datei muss mindestens fünf Sekunden unterscheidbaren Ton enthalten.
2. Kehre zur Erkennungsansicht zurück und tippe auf **Musik erkennen**. Erlaube den Mikrofonzugriff und spiele einen Ausschnitt derselben Aufnahme ab. Listen45 erfasst bis zu acht Sekunden Umgebungston.
3. Treffer erscheinen im Verlauf. Dort kannst du sie favorisieren oder löschen. In den Einstellungen kannst du den Verlauf als JSON exportieren und Audire- oder Listen45-Verlaufsexporte importieren.

Listen45 indexiert die ersten zwei Minuten je Referenz. Der Katalog startet leer und fasst bis zu 100 Titel. Treffer setzen eine passende Referenz voraus. Coverversionen, Live-Aufnahmen und stark verrauschter Ton können zu einem fehlenden Treffer führen. Genauigkeit und Laufzeit bei unterschiedlichen Geräten und echten Musikaufnahmen sind noch nicht systematisch vermessen.

## Bauen und testen

Benötigt werden ein JDK 21, Android SDK Platform 36 und Build-Tools 35.0.0. Setze `ANDROID_HOME` oder konfiguriere das SDK in Android Studio.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease
```

Die installierbare Entwicklungs-APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Der Release-Build ist unsigniert. [GitHub Actions](.github/workflows/android.yml) baut beide Varianten und stellt die Debug-APK als Workflow-Artefakt bereit.

## Architektur und Datenschutz

Die App verwendet Kotlin, native Android-Views, ViewModels, Room für den Verlauf und SQLite für die Referenzdatenbank. [Architektur und untersuchte Erkennungsalternativen](docs/RECOGNITION.md) · [Datenschutz](docs/PRIVACY.md)

## Herkunft und Lizenz

Listen45 basiert auf [Audire von Hitesh Kumar Saini (alexmercerind) und Mitwirkenden](https://github.com/alexmercerind/audire/tree/410b816ea129df06b7c3c7b3f147165f15ac0b6a). Ausgangspunkt ist Upstream-Commit `410b816ea129df06b7c3c7b3f147165f15ac0b6a`. Listen45 enthält Änderungen an Branding, Erkennung, Berechtigungen und Verlauf. Die Upstream-Veröffentlichung und die Beitragsgeschichte sind im verlinkten Projekt einsehbar.

Listen45 steht unter der [GNU General Public License, Version 3](LICENSE). Der vollständige Quellcode zum Build steht in diesem Repository. Die Farben orientieren sich an [Media45](https://github.com/x45eu/media45).
