package com.example.cramrescue.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Neutral UI colors
//
// These describe the calm, restrained backdrop of the app itself — not
// product meaning. Reach for these for backgrounds, surfaces, text, and
// borders. Never repurpose a semantic color (below) just to make a screen
// look "on brand"; the light theme is the default day-to-day identity.
// ---------------------------------------------------------------------------

// Light palette — the default/primary CramRescue visual direction.
val AppBackgroundLight = Color(0xFFFDFDFB)
val AppSurfaceLight = Color(0xFFFFFFFF)
val AppSurfaceVariantLight = Color(0xFFF1F1EF)
val AppOutlineLight = Color(0xFFDCDCDA)
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF6E6E6E)

// A single calm, neutral accent for interactive elements (buttons, selected
// chips). Deliberately not red/yellow/green: those are reserved for ER
// Triage meaning and must never become the default button color.
val AccentPrimaryLight = Color(0xFF212121)
val OnAccentPrimaryLight = Color(0xFFFFFFFF)

// Neutral "selected/emphasis" container (e.g. a selected filter chip). Kept
// neutral for the same reason as AccentPrimaryLight above — Material3 would
// otherwise fall back to its own default tonal (purple) container.
val AccentContainerLight = Color(0xFFE7E7E5)
val OnAccentContainerLight = Color(0xFF1A1A1A)

// Dark palette — used when the system is in dark mode (requirements.md §5.4,
// late-night cramming; see CramRescueTheme). Same neutral rules as light:
// the accent is a calm light tone, never a triage color.
val AppBackgroundDark = Color(0xFF0F1419)
val AppSurfaceDark = Color(0xFF1A1F26)
val AppSurfaceVariantDark = Color(0xFF232A33)
val AppOutlineDark = Color(0xFF3A424C)
val TextPrimaryDark = Color(0xFFEEEEEE)
val TextSecondaryDark = Color(0xFF9E9E9E)
val AccentContainerDark = Color(0xFF2A323C)
val OnAccentContainerDark = Color(0xFFEEEEEE)
val AccentPrimaryDark = Color(0xFFE6E6E6)
val OnAccentPrimaryDark = Color(0xFF121212)

// Error/emergency text on dark surfaces. CodeRed (#D32F2F) is too dark to read as body text on
// AppBackgroundDark (~3.7:1); this lighter red of the same hue keeps ~6:1. Badges that draw
// CodeRed as a filled background with OnCodeRed text are unaffected.
val ErrorOnDark = Color(0xFFFF6B6B)

// ---------------------------------------------------------------------------
// Semantic CramRescue colors — ER Triage
//
// These carry product meaning and must stay reserved for it: badges,
// priority indicators, and (later) emergency/urgency emphasis. Do not use
// them as general-purpose decoration, and never let them become a screen's
// dominant background color.
// ---------------------------------------------------------------------------

val CodeRed = Color(0xFFD32F2F)      // Critical / must-master content, urgent gaps
val CodeYellow = Color(0xFFF9A825)   // Important / medium-priority content
val CodeGreen = Color(0xFF2E7D32)    // Safe / optional / completed / positive

val OnCodeRed = Color(0xFFFFFFFF)
val OnCodeYellow = Color(0xFF1A1A1A)
val OnCodeGreen = Color(0xFFFFFFFF)

// Source Grounding highlight (requirements.md UJ-2: a yellow bounding-box frame on the original
// page). A verification signal, not triage — deliberately a separate token from CodeYellow.
val GroundingHighlight = Color(0xFFFFB300)

// Soft semantic containers for chips and status cards
val CodeRedContainer = Color(0xFFFFEBEE)
val OnCodeRedContainer = Color(0xFFB71C1C)
val CodeYellowContainer = Color(0xFFFFF8E1)
val OnCodeYellowContainer = Color(0xFFF57F17)
val CodeGreenContainer = Color(0xFFE8F5E9)
val OnCodeGreenContainer = Color(0xFF1B5E20)

val CodeRedContainerDark = Color(0xFF3B1214)
val CodeYellowContainerDark = Color(0xFF382E0B)
val CodeGreenContainerDark = Color(0xFF132B18)

