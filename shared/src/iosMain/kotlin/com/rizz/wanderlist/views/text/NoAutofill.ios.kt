package com.rizz.wanderlist.views.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberNoAutofill(): PlatformImeOptions? = remember {
    // An empty content type tells iOS the field isn't any kind it can AutoFill.
    PlatformImeOptions { textContentType("") }
}
