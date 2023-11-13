buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.5")
    }
}
plugins {
    id("com.android.application").version("8.1.0").apply(false)
    id("org.jetbrains.kotlin.android").version("1.9.20").apply(false)
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}