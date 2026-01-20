package com.sidharthify.breathe.data

import androidx.compose.runtime.compositionLocalOf

/**
 * Useful for low-end devices or users who prefer reduced motion.
 */
data class AnimationSettings(
    val animationsEnabled: Boolean = true,
    val screenTransitions: Boolean = true,
    val colorTransitions: Boolean = true,
    val numberAnimations: Boolean = true,
    val pulseEffects: Boolean = true,
    val morphingPill: Boolean = true,
    val pressFeedback: Boolean = true,
    val listAnimations: Boolean = true,
) {
    companion object {
        val Disabled =
            AnimationSettings(
                animationsEnabled = false,
                screenTransitions = false,
                colorTransitions = false,
                numberAnimations = false,
                pulseEffects = false,
                morphingPill = false,
                pressFeedback = false,
                listAnimations = false,
            )

        val Reduced =
            AnimationSettings(
                animationsEnabled = true,
                screenTransitions = true,
                colorTransitions = false,
                numberAnimations = false,
                pulseEffects = false,
                morphingPill = false,
                pressFeedback = true,
                listAnimations = false,
            )
    }
}

val LocalAnimationSettings = compositionLocalOf { AnimationSettings() }
