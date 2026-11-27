// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.ui

import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import eu.x45.listen45.R
import eu.x45.listen45.databinding.ActivityCatalogBinding
import eu.x45.listen45.recognition.ReferenceTrack
import kotlinx.coroutines.launch

class CatalogActivity : AppCompatActivity() {
    private val viewModel by viewModels<CatalogViewModel>()
    private val chooseReference = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) showTrackDetails(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.addReference.setOnClickListener { chooseReference.launch(arrayOf("audio/*", "application/octet-stream")) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.addReference.isEnabled = !state.busy
                    binding.libraryProgress.isVisible = state.busy
                    binding.emptyCatalog.isVisible = !state.busy && state.tracks.isEmpty()
                    binding.referenceList.isEnabled = !state.busy
                    binding.referenceList.adapter = ArrayAdapter(
                        this@CatalogActivity, android.R.layout.simple_list_item_1,
                        state.tracks.map { track ->
                            listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" — ")
                        },
                    )
                    binding.referenceList.setOnItemClickListener { _, _, position, _ ->
                        confirmDelete(state.tracks[position])
                    }
                    state.message?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    private fun confirmDelete(track: ReferenceTrack) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.catalog_delete)
            .setMessage(getString(R.string.catalog_delete_message, track.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.catalog_delete) { _, _ -> viewModel.delete(track.id) }
            .show()
    }

    private fun showTrackDetails(uri: Uri) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        fun field(label: Int): TextInputEditText {
            val layout = TextInputLayout(this).apply { hint = getString(label) }
            val input = TextInputEditText(layout.context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                filters = arrayOf(InputFilter.LengthFilter(160))
                id = View.generateViewId()
            }
            layout.addView(input)
            fields.addView(layout)
            return input
        }
        val title = field(R.string.catalog_track_title)
        val artist = field(R.string.catalog_track_artist)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.catalog_add)
            .setView(fields)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = title.text.toString().trim()
            if (name.isBlank()) {
                title.error = getString(R.string.catalog_track_title)
            } else {
                viewModel.add(uri, name, artist.text.toString().trim())
                dialog.dismiss()
            }
        }
    }
}
