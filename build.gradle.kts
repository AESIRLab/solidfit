// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false

    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false

    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
    id("com.google.firebase.firebase-perf") version "1.4.2" apply false
    application
}

buildscript {
    extra["kotlin_version"] = "1.9.10"
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.9.10"))
    }
}

extra.apply {
    set("activityVersion", "1.7.0")
    set("appCompatVersion", "1.6.1")
    set("constraintLayoutVersion", "2.1.4")
    set("coreTestingVersion", "2.1.0")
    set("coroutines", "1.7.1")
    set("lifecycleVersion", "2.8.3")
    set("materialVersion", "1.9.0")
    set("roomVersion", "2.6.0")
    // testing
    set("junitVersion", "4.13.2")
    set("espressoVersion", "3.5.1")
    set("androidxJunitVersion", "1.1.3")
}

