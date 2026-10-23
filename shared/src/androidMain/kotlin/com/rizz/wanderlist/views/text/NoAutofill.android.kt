package com.rizz.wanderlist.views.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * Nothing to switch off here: Compose's text fields always offer themselves to Android
 * Autofill (they mark themselves autofillable after any modifier we add), and the
 * suggestions show in the keyboard or right under the field.
 */
@Composable
actual fun rememberNoAutofill(): PlatformImeOptions? = null
