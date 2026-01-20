import org.gradle.api.tasks.Delete

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)

    alias(libs.plugins.ksp)
//    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
    alias(libs.plugins.compose.compiler)
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

android {
    namespace = "com.example.solidfit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.solidfit"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "2.1.01"
        compileSdkPreview = "VanillaIceCream"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.example.solidfit"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        @Suppress("DEPRECATION")
        exclude ("META-INF/atomicfu.kotlin_module")
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val deleteFolder by tasks.registering(Delete::class) {
    delete(layout.projectDirectory.dir("build"))
}

tasks.named("preBuild") {
    dependsOn(deleteFolder)
}

val version = "0.0.62-stable"
dependencies {

    // jwt creation
    implementation(libs.nimbus.jose.jwt)

    // http services
    implementation(libs.okhttp)

    // datastore
    implementation(libs.androidx.datastore.preferences)

    // reflection for datastore
    implementation(kotlin("reflect"))

    // code verifier util
    // jwt utils
    implementation(libs.appauth)
    implementation(libs.play.services.location)
    implementation(libs.androidx.navigation.compose)

    ksp("com.squareup:kotlinpoet:1.14.0")
    ksp("com.squareup:kotlinpoet-ksp:1.12.0")

    implementation("org.aesirlab:sksolidannotations:$version")
    ksp("org.aesirlab:skannotationscompiler:$version")
    implementation("org.aesirlab:authlib:$version")

    debugImplementation(libs.androidx.ui.test.manifest)
    ///////////////////////////////////////////////////////////////

    // optional - needed for credentials support from play services, for devices running
    // Android 13 and below.
    implementation(libs.androidx.credentials.play.services.auth)
    ////////////////////////////////////////////////

    implementation (libs.androidx.room.runtime.v260)
    implementation (libs.androidx.room.ktx.v260)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    implementation(libs.firebase.messaging)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)

    // image display
    implementation (libs.coil.compose)

    ksp (libs.androidx.room.compiler.v260)

    implementation(libs.androidx.appcompat)
    //noinspection GradleDependency
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom.v20240600))
    implementation(libs.accompanist.themeadapter.material3)


    implementation(libs.activity.ktx)


    // Dependencies for working with Architecture components
    // You'll probably have to update the version numbers in build.gradle (Project)

    implementation (libs.androidx.graphics.shapes)
    implementation(libs.androidx.coordinatorlayout)

    // Room components
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.runtime.android)
    implementation (libs.androidx.runtime)
    implementation (libs.androidx.room.runtime)


    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.runtime.livedata)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)

    // Lifecycle components
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation (libs.androidx.lifecycle.runtime.ktx.v251)


    implementation(libs.androidx.lifecycle.livedata.ktx)
    //noinspection GradleDependency
    implementation(libs.androidx.lifecycle.common.java8)

    // Kotlin components
    //noinspection GradleDependency
    implementation (libs.kotlin.stdlib)
    ksp(libs.dagger.compiler)
    api(libs.kotlinx.coroutines.core)
    //noinspection GradleDependency
    api(libs.kotlinx.coroutines.android)

    // UI
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.core.testing)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1") {
        exclude(group = "com.android.support", module = "support-annotations")
    }
    androidTestImplementation(libs.androidx.junit.v121)

    // Work manager
    implementation(libs.androidx.work.runtime.ktx)

    // Google Health Connect
    implementation(libs.androidx.connect.client.v110)


}

