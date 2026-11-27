package eu.x45.listen45.repository

import android.app.Application
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eu.x45.listen45.db.HistoryItemDatabase
import eu.x45.listen45.models.HistoryItem
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ImportExportRepository(private val application: Application) {
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val input = application.contentResolver.openInputStream(uri) ?: throw IOException("Missing input")
        val json = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                ensureActive()
                val size = stream.read(buffer)
                if (size == -1) break
                require(output.size() + size <= MAX_IMPORT_BYTES)
                output.write(buffer, 0, size)
            }
            output.toString(Charsets.UTF_16.name())
        }
        val items = gson.fromJson<List<HistoryItem?>>(json, itemList) ?: error("Missing history")
        val validItems = items.map { item ->
            requireNotNull(item)
            require(item.title.orEmpty().isNotBlank())
            item.copy(artists = item.artists.orEmpty(), cover = item.cover.orEmpty())
        }
        HistoryItemDatabase(application).historyItemDao().merge(validItems)
    }

    suspend fun export(uri: Uri, historyItems: List<HistoryItem>) = withContext(Dispatchers.IO) {
        val output = application.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Missing output")
        output.bufferedWriter(Charsets.UTF_16).use { gson.toJson(historyItems, itemList, it) }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024
        private val itemList = object : TypeToken<List<HistoryItem?>>() {}.type
        private val gson = Gson()
    }
}
