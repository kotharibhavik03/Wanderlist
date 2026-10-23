package com.rizz.wanderlist.views.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * IME options that keep the system's AutoFill away from a text field that asks for someone
 * else's details, where suggesting the user's own would be wrong. Pass the result to the
 * field's `KeyboardOptions(platformImeOptions = ...)`.
 *
 * iOS clears the field's content type. Android returns null: Compose doesn't let an app opt a
 * text field out of Android Autofill (see the Android actual).
 */
@Composable
expect fun rememberNoAutofill(): PlatformImeOptions?
