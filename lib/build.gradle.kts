import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.mavenPublish)
}

android {
  namespace = "com.sd.lib.compose.wheelpicker"
  compileSdk = libs.versions.androidCompileSdk.get().toInt()
  defaultConfig {
    minSdk = 21
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
    moduleName = "com.sd.lib.compose.wheelpicker"
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  implementation(libs.androidx.compose.foundation)

  testImplementation(libs.junit)

  androidTestImplementation(composeBom)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.compose.material3)
  androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}

mavenPublishing {
  configure(
    AndroidSingleVariantLibrary(
      variant = "release",
      sourcesJar = true,
      publishJavadocJar = true,
    )
  )
}