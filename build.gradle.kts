import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        // AGP 8.7.3: Paling tinggi yang stabil & support Gradle 8.14
        // Jangan pakai 9.0.0 dulu, plugin CS3 belum sanggup.
        classpath("com.android.tools.build:gradle:8.7.3")

        // Cloudstream Gradle Plugin (Hash Stabil)
        classpath("com.github.recloudstream:gradle:cce1b8d84d")

        // KOTLIN 2.1.0: Ini solusi error 'metadata' kamu.
        // Ini versi terbaru yang memperbaiki masalah kompatibilitas library CS3 baru.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/phisher98/cloudstream-extensions-phisher")
        authors = listOf("Phisher98")
    }

    android {
        namespace = "com.phisher98"

        defaultConfig {
            minSdk = 21
            // Target SDK Android 15 (Vanilla Ice Cream)
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            // Kita pakai Java 17 untuk compile biar support fitur bahasa baru
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                // JVM Target 17 (Lebih modern daripada 1.8)
                jvmTarget.set(JvmTarget.JVM_17) 
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                    // Wajib ada untuk support Kotlin 2.0+ di Cloudstream
                    "-Xannotation-defaulting=param-property" 
                )
            }
        }
    }

    dependencies {
        val implementation by configurations
        val cloudstream by configurations
        
        // Library Utama Cloudstream
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // --- DEPENDENCIES "MASA DEPAN" (Versi Terbaru) ---
        
        // Kotlin Standard Library (Otomatis ikut versi plugin 2.1.0)
        implementation(kotlin("stdlib")) 

        // Networking
        implementation("com.github.Blatzar:NiceHttp:0.4.16") // Belum ada update baru
        implementation("org.jsoup:jsoup:1.18.3") // Update Jsoup terbaru
        
        // Annotations
        implementation("androidx.annotation:annotation:1.9.1") // Update terbaru
        
        // JSON Parsing (Jackson & Gson - Update Terbaru)
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
        implementation("com.google.code.gson:gson:2.11.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        
        // Coroutines & Async
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
        
        // Utilities
        implementation("org.mozilla:rhino:1.7.15") // Update Rhino
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
        implementation("com.github.vidstige:jadb:v1.2.1")
        implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
