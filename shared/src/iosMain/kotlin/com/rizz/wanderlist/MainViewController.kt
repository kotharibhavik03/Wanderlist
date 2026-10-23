package com.rizz.wanderlist

import androidx.compose.ui.window.ComposeUIViewController
import com.rizz.wanderlist.di.initApp
import com.rizz.wanderlist.di.iosPlatformModule
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initApp(iosPlatformModule)
    return ComposeUIViewController { App() }
}