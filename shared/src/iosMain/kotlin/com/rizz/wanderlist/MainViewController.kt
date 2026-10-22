package com.rizz.wanderlist

import androidx.compose.ui.window.ComposeUIViewController
import com.rizz.wanderlist.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}