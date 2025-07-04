package com.mikestudios.lifesummary

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import java.io.File
import com.mikestudios.lifesummary.ui.theme.LifeSummaryTheme
import androidx.compose.foundation.layout.padding
import com.mikestudios.lifesummary.SummaryUtils
import androidx.compose.foundation.background
import com.mikestudios.lifesummary.ui.theme.primaryGradient

@OptIn(ExperimentalMaterial3Api::class)
class Summary30Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = SummaryUtils.loadSummaries(this, 30)
        setContent {
            LifeSummaryTheme {
                Scaffold(
                    modifier = Modifier.background(primaryGradient()),
                    topBar = {
                        TopAppBar(
                            title = { Text("30-min Summaries") },
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
} 