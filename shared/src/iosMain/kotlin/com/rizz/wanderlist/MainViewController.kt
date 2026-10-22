package com.rizz.wanderlist

import androidx.compose.ui.window.ComposeUIViewController
import com.rizz.wanderlist.di.initApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initApp()
    return ComposeUIViewController { App() }
}