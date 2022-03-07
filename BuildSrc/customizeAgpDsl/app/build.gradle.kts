plugins {
    id("com.android.application")
    kotlin("android")
}

apply<ExamplePlugin>()

android {
    compileSdkVersion(29)
    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(29)
    }
    buildTypes {
        debug {
            the<BuildTypeExtension>().invocationParameters = "-debug -log"
        }
    }
}

apply<SitePlugin>()

configure<SiteExtension> {
    outputDir?.set(layout.buildDirectory.file("mysite"))
    customData?.websiteUrl?.set("https://gradle.org")
    customData?.vcsUrl?.set("https://github.com/gradle/gradle-site-plugin")
}
// single
// the<SiteExtension>().customData?.vcsUrl?.set("https://github.com/gradle/gradle-site-plugin")
