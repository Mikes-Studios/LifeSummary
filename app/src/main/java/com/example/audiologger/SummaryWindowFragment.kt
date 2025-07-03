package com.example.audiologger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import com.example.audiologger.ui.theme.LifeSummaryTheme

class SummaryWindowFragment : Fragment() {

    companion object {
        private const val ARG_MINUTES = "minutes"
        fun create(minutes: Int): SummaryWindowFragment = SummaryWindowFragment().apply {
            arguments = Bundle().apply { putInt(ARG_MINUTES, minutes) }
        }
    }

    private val minutes: Int by lazy { arguments?.getInt(ARG_MINUTES, 30) ?: 30 }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val entries = SummaryUtils.loadSummaries(requireContext(), minutes)
        return ComposeView(requireContext()).apply {
            setContent {
                LifeSummaryTheme {
                    SummaryWindowScreen(entries, minutes)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SummaryWindowScreen(entries: List<LogEntry>, mins: Int) {
        // Determine title for Activity toolbar
        val titleText = when {
            mins % 60 == 0 -> {
                val hours = mins / 60
                if (hours == 1) "1-Hour Summaries" else "${hours}-Hour Summaries"
            }
            else -> "${mins}-min Summaries"
        }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    (requireActivity() as MainActivity).loadFragment(HomeFragment())
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Back to home")
                }
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                LogListWithSearch(entries)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val mins = minutes
        val title = if (mins % 60 == 0) {
            val hours = mins / 60
            if (hours == 1) "1-Hour Summaries" else "${hours}-Hour Summaries"
        } else "${mins}-min Summaries"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = title
    }
} 