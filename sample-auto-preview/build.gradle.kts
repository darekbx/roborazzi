import io.github.takahirom.roborazzi.RoborazziExtension.BaseSetupConfig.AndroidAutomaticPreviewScreenshots

plugins {
  id("com.android.application")
//  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("io.github.takahirom.roborazzi")
}

roborazzi {
  baseSetupConfig(
    AndroidAutomaticPreviewScreenshots(listOf("com.github.takahirom.sample"))
  )
  advancedAndroidSetup {
    libraryDependencies.junitVersion = "4.13.2"
  }
}

android {
  namespace = "com.github.takahirom.sample"
  compileSdk = 34

  defaultConfig {
    minSdk = 24

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
}

dependencies {
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
}