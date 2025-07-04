package com.mikestudios.lifesummary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

@Composable
fun primaryGradient(height: Dp = 600.dp): Brush {
    val dark = isSystemInDarkTheme()
    val end = Offset.Infinite
    return if (dark) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF2A2A2A), Color.Black),
            start = Offset.Zero,
            end = Offset(1000f, 1000f)
        )
    } else {
        val cs = MaterialTheme.colorScheme
        Brush.linearGradient(
            colors = listOf(
                cs.primary.copy(alpha = 0.3f),
                cs.primaryContainer.copy(alpha = 0.25f),
                cs.background
            ),
            start = Offset.Zero,
            end = Offset(1000f, 1000f)
        )
    }
} 