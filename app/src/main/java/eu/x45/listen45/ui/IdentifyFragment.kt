package eu.x45.listen45.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eu.x45.listen45.R
import eu.x45.listen45.databinding.FragmentIdentifyBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class IdentifyFragment : Fragment(R.layout.fragment_identify) {
    private val viewModel: IdentifyViewModel by activityViewModels()
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.start()
        } else {
            view?.let { Snackbar.make(it, R.string.identify_permission_help, Snackbar.LENGTH_LONG).show() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentIdentifyBinding.bind(view)
        binding.wordmark.text = SpannableString(getString(R.string.app_name)).apply {
            setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.brand_blue)),
                length - 2, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.recordButton.setOnClickListener { startOrRequestPermission() }
        binding.stopButton.setOnClickListener { viewModel.stop() }
        binding.catalogButton.setOnClickListener { startActivity(Intent(requireContext(), CatalogActivity::class.java)) }
        binding.primaryMaterialToolbar.setOnMenuItemClickListener {
            val activity = when (it.itemId) {
                R.id.settings -> SettingsActivity::class.java
                R.id.about -> AboutActivity::class.java
                else -> return@setOnMenuItemClickListener false
            }
            startActivity(Intent(requireContext(), activity))
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.startRequested.collect { requested ->
                        if (requested) {
                            viewModel.clearStartRequest()
                            startOrRequestPermission()
                        }
                    }
                }
                launch {
                    viewModel.state.collect { state ->
                        val busy = state is IdentifyViewModel.State.Recording || state == IdentifyViewModel.State.Matching
                        binding.recordButton.isVisible = !busy
                        binding.stopButton.isVisible = busy
                        binding.catalogButton.isEnabled = !busy
                        binding.recognitionStatus.text = when (state) {
                            is IdentifyViewModel.State.Recording -> getString(R.string.identify_listening, state.seconds)
                            IdentifyViewModel.State.Matching -> getString(R.string.identify_matching)
                            else -> ""
                        }
                        when (state) {
                            is IdentifyViewModel.State.Message -> {
                                Snackbar.make(view, state.resource, Snackbar.LENGTH_LONG).apply {
                                    anchorView = requireActivity().findViewById(R.id.bottomNavigationView)
                                }.show()
                                viewModel.acknowledge(state)
                            }
                            is IdentifyViewModel.State.Found -> {
                                viewModel.acknowledge(state)
                                startActivity(Intent(requireContext(), MusicActivity::class.java)
                                    .putExtra(MusicActivity.MUSIC, state.music))
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun startOrRequestPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
            viewModel.start()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        if (activity?.isChangingConfigurations != true) viewModel.stop()
        super.onStop()
    }
}
