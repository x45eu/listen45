# Musikerkennung

`AudioRecorder` erfasst eine kurze Mono-Aufnahme. Eine gemeinsame Normalisierung bringt Mikrofon und importierte Referenzen auf 11.025 Hz. `RecognitionEngine` nimmt diese PCM-Proben entgegen und liefert einen Treffer oder kein Ergebnis. Die lokale Implementierung bildet Fingerabdrücke aus zeitlich benachbarten spektralen Spitzen und vergleicht die Zeitabstände mit den Referenzen. Treffer brauchen übereinstimmende Anker über mehrere Zeitfenster und ausreichend Abstand zum nächstbesten Ergebnis.

Die Referenzdatenbank speichert Fingerabdrücke und Metadaten in SQLite. Der Vergleich liest jeweils den Fingerabdruck eines Titels ein. PCM16-WAV-Dateien werden direkt gelesen. Weitere Audioformate werden mit Androids Medien-Decodern verarbeitet. Beide Pfade begrenzen die PCM-Ausgabe auf zwei Minuten und unterstützen Mono oder Stereo. Die aktuelle lineare Suche ist für maximal 100 Referenzen dimensioniert. Das Fingerabdruckformat gehört zur lokalen Datenbank und lässt sich bei einer späteren Formatänderung über eine Datenbankmigration umstellen.

| Alternative | Bewertung |
| --- | --- |
| Lokal erzeugte Landmark-Fingerabdrücke | Für kurze Mikrofonproben und einen kleinen persönlichen Katalog integriert. Läuft mit Kotlin und Android-Bordmitteln. |
| [Chromaprint / AcoustID](https://acoustid.org/chromaprint) | Chromaprint eignet sich für ähnliche vollständige Dateien und längere Streams. Der [AcoustID-Dienst](https://acoustid.org/webservice) verlangt einen Anwendungsschlüssel und überträgt Fingerabdrücke an einen Server. |
| [audfprint](https://github.com/dpwe/audfprint) | Unterstützt die Suche nach verrauschten Ausschnitten in einem eigenen Index. Die Python-Anwendung eignet sich für einen selbst betriebenen Dienst oder ein Desktop-Werkzeug. |
| [Panako](https://github.com/JorenSix/Panako) | Offenes Fingerprinting-Projekt für lokale oder selbst gehostete Kataloge, auch bei Änderungen von Tempo und Tonhöhe. Der vorhandene CLI- und Datenbankaufbau würde eine eigene Android-Portierung oder einen Dienst erfordern. |

Shazam, proprietäre Erkennungsendpunkte, anbieterspezifische IDs, Netzwerk-Cover und die heruntergeladenen JNI-Bibliotheken wurden aus dem Build entfernt. Der alte Verlauf bleibt per JSON importierbar. Teilen verwendet Text, sodass ein fehlendes Cover keinen Absturz auslösen kann ([Audire #57](https://github.com/alexmercerind/audire/issues/57)). Bei einer Bildschirmdrehung bleibt ein laufender Versuch im Activity-ViewModel erhalten ([Audire #44](https://github.com/alexmercerind/audire/issues/44)). Verlässt die App den Vordergrund oder öffnet der Nutzer eine andere Ansicht, wird der Versuch abgebrochen.

Unit-Tests verwenden lokal erzeugte Signale für einen versetzten und verrauschten Treffer, fremde Aufnahmen, Stille und WAV-Dekodierung. Android-Instrumentierungstests prüfen den App-Start und einen tatsächlich decodierten MP3-Ausschnitt mit lokalem Treffer und gespeichertem Verlauf. Mikrofonerkennung mit externer Wiedergabe, Drehung und Abbruch lassen sich ergänzend auf physischen Geräten prüfen.
