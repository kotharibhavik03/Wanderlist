import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)

    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "com.rizz.wanderlist.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_17
       }
       androidResources {
           enable = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // Location permission prompt (Activity result API) and the device's position.
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.services.location)
            // Coil's network layer on Android.
            implementation(libs.ktor.client.okhttp)
            // Reads which way up a photo was taken, so compressed photos aren't sideways.
            implementation(libs.androidx.exifinterface)
        }
        iosMain.dependencies {
            // Coil's network layer on iOS.
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {

            implementation(compose.materialIconsExtended)

            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
            implementation(libs.androidx.savedstate)
            implementation(libs.kotlinx.serialization.core)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewModel)

            implementation(libs.kmpauth.google)
            implementation(libs.kmpauth.firebase)
            implementation(libs.kmpauth.uihelper)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.storage)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.kmp.maps.compose)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}