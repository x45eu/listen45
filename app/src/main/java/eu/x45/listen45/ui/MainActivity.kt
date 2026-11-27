package eu.x45.listen45.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import eu.x45.listen45.R
import eu.x45.listen45.databinding.ActivityMainBinding
import eu.x45.listen45.services.FindMusicTileService

class MainActivity : AppCompatActivity() {
    val identifyViewModel by viewModels<IdentifyViewModel>()
    val settingsViewModel by viewModels<SettingsViewModel>()

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        // https://stackoverflow.com/a/50537193/12825435
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.content) as NavHostFragment
        binding.bottomNavigationView.setupWithNavController(navHostFragment.navController)
    }

    override fun onResume() {
        super.onResume()
        val fromTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !FindMusicTileService.handled
        val autoStart = !SettingsViewModel.autoStartHandled && settingsViewModel.autoStart.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) FindMusicTileService.handled = true
        SettingsViewModel.autoStartHandled = true
        if (fromTile || autoStart) {
            binding.bottomNavigationView.selectedItemId = R.id.identifyFragment
            identifyViewModel.requestStart()
        }
    }
}
