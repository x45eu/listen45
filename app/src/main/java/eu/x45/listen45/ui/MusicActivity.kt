package eu.x45.listen45.ui

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import eu.x45.listen45.R
import eu.x45.listen45.databinding.ActivityMusicBinding
import eu.x45.listen45.mappers.toSearchQuery
import eu.x45.listen45.mappers.toShareText
import eu.x45.listen45.models.Music

class MusicActivity : AppCompatActivity() {
    companion object {
        const val MUSIC = "MUSIC"
    }

    private lateinit var music: Music
    private lateinit var binding: ActivityMusicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        binding = ActivityMusicBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonGroup) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = 16,
                left = 16 + insets.left,
                right = 16 + insets.right,
                bottom = 16 + insets.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        music = IntentCompat.getSerializableExtra(intent, MUSIC, Music::class.java) ?: run {
            finish()
            return
        }

        binding.titleTextView.text = music.title
        if (music.artists.isNotEmpty()) {
            binding.artistsTextView.text = music.artists
        } else {
            binding.artistsMaterialCardView.visibility = View.GONE
        }
        if (music.album != null) {
            binding.albumTextView.text = music.album
        } else {
            binding.albumMaterialCardView.visibility = View.GONE
        }
        if (music.label != null) {
            binding.labelTextView.text = music.label
        } else {
            binding.labelMaterialCardView.visibility = View.GONE
        }
        if (music.year != null) {
            binding.yearTextView.text = music.year
        } else {
            binding.yearMaterialCardView.visibility = View.GONE
        }
        binding.coverImageView.setImageResource(R.drawable.baseline_album_24)
        binding.titleTextView.setTextIsSelectable(true)
        binding.artistsTextView.setTextIsSelectable(true)

        binding.searchButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, music.toSearchQuery()))
            } catch (_: ActivityNotFoundException) {
                Snackbar.make(view, R.string.action_view_error, Snackbar.LENGTH_LONG).show()
            }
        }
        binding.shareButton.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, music.toShareText())
            startActivity(Intent.createChooser(send, music.title))
        }
    }
}
