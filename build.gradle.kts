import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // Penting: Repository ini wajib ada di buildscript
        maven("https://jitpack.io") 
    }

    dependencies {
        // AGP 8.4.2 kompatibel dengan Gradle 8.14+
        classpath("com.android.tools.build:gradle:8.4.2")
        
        // PERBAIKAN UTAMA: Menggunakan hash spesifik menggantikan master-SNAPSHOT
        classpath("com.github.recloudstream:gradle:138bb97")
        
        // Kotlin 1.9.24 adalah versi paling stabil untuk ekstensi Cloudstream saat ini
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
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
        // Mengatur info repository otomatis atau fallback ke URL default
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/phisher98/cloudstream-extensions-phisher")
        authors = listOf("Phisher98")
    }

    android {
        namespace = "com.phisher98"

        defaultConfig {
            minSdk = 21
            // Compile dan Target SDK 35 sudah benar untuk Android 15
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                    // Ini penting agar metadata properti terbaca benar oleh Cloudstream
                    "-Xannotation-default-target=param-property" 
                )
            }
        }
    }

    dependencies {
        val implementation by configurations
        val cloudstream by configurations
        
        // Core Cloudstream dependency
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // Standard Kotlin Libs
        implementation(kotlin("stdlib"))
        
        // Networking & Parsing
        implementation("com.github.Blatzar:NiceHttp:0.4.16")
        implementation("org.jsoup:jsoup:1.17.2") // Update ke versi stabil umum
        implementation("androidx.annotation:annotation:1.9.1")
        
        // JSON Handling
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
        implementation("com.google.code.gson:gson:2.10.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        
        // Async & Utils
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
        implementation("org.mozilla:rhino:1.7.14") // Versi Rhino yang lebih umum
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
        
        // Other Utils
        implementation("com.github.vidstige:jadb:v1.2.1")
        implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
