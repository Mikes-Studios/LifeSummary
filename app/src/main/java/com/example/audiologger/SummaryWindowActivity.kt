package com.example.audiologger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import com.example.audiologger.ui.theme.LifeSummaryTheme

/**
 * Generic screen that shows summaries for an arbitrary time window (in minutes)
 */
@OptIn(ExperimentalMaterial3Api::class)
class SummaryWindowActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 30)
        val entries = SummaryUtils.loadSummaries(this, minutes)

        val titleText = when {
            minutes % 60 == 0 -> {
                val hours = minutes / 60
                if (hours == 1) "1-Hour Summaries" else "${hours}-Hour Summaries"
            }
            else -> "${minutes}-min Summaries"
        }

        setContent {
            LifeSummaryTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(titleText) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { finish() }) {
                            Icon(Icons.Default.Home, contentDescription = "Back to home")
                        }
                    }
                ) { padding ->
                    Surface(modifier = Modifier.padding(padding)) {
                        LogListWithSearch(entries)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MINUTES = "window_minutes"
        fun createIntent(ctx: Context, minutes: Int): Intent =
            Intent(ctx, SummaryWindowActivity::class.java).putExtra(EXTRA_MINUTES, minutes)
    }
} 