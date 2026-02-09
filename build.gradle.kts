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
        // AGP 8.8.0: Versi stabil tinggi (Lebih aman daripada 9.0 alpha untuk saat ini)
        classpath("com.android.tools.build:gradle:8.8.0")

        // Cloudstream Plugin (Hash Stabil)
        classpath("com.github.recloudstream:gradle:cce1b8d84d")

        // SOLUSI ERROR KAMU: Upgrade ke Kotlin 2.3.0
        // Agar bisa membaca metadata version 2.3.0 dari library Cloudstream
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
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
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            // Kita pakai Java 17 (Standar masa depan)
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                    // Flag wajib untuk Kotlin 2.x
                    "-Xannotation-defaulting=param-property" 
                )
            }
        }
    }

    dependencies {
        val implementation by configurations
        val cloudstream by configurations
        
        // Library Utama
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // --- DEPENDENCIES (Versi Disesuaikan) ---
        
        implementation(kotlin("stdlib")) // Akan ikut versi 2.3.0
        
        implementation("com.github.Blatzar:NiceHttp:0.4.16")
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("androidx.annotation:annotation:1.9.1")
        
        // Update Library JSON agar support Kotlin 2.3
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
        implementation("com.google.code.gson:gson:2.12.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
        implementation("org.mozilla:rhino:1.7.15")
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
        implementation("com.github.vidstige:jadb:v1.2.1")
        implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
