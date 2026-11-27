package eu.x45.listen45.mappers

import eu.x45.listen45.models.HistoryItem
import eu.x45.listen45.models.Music

fun HistoryItem.toMusic() = Music(
    title,
    artists,
    cover,
    album,
    label,
    year,
    lyrics
)
