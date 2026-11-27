package eu.x45.listen45.mappers

import eu.x45.listen45.models.HistoryItem
import eu.x45.listen45.models.Music
import java.util.Calendar

fun Music.toHistoryItem() = HistoryItem(
    null,
    Calendar.getInstance().time.time,
    title,
    artists,
    cover,
    album,
    label,
    year,
    lyrics,
    false
)

fun Music.toSearchQuery() = listOfNotNull(
    title.ifBlank { null },
    artists.ifBlank { null },
    album?.ifBlank { null },
    year?.ifBlank { null }
)
    .joinToString(" ")
    .trim()

fun Music.toShareText() = buildString {
    append(title)
    if (artists.isNotBlank()) {
        append("\n$artists")
    }
    if (!album.isNullOrBlank()) {
        append("\n$album")
    }
    if (!year.isNullOrBlank()) {
        append(" ($year)")
    }
}
