plugins {
    id("com.android.application")
    kotlin("android")
}

apply<ExamplePlugin>()

android {
    compileSdkVersion(31)
    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(31)
    }
}

dependencies {
    implementation(project(mapOf("path" to ":library")))
}
