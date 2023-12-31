@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SonatypeHost

plugins {
  kotlin("multiplatform")
  kotlin("native.cocoapods")
  id("com.android.library")
  kotlin("plugin.serialization") version "1.9.0"
  id("maven-publish")
  alias(libs.plugins.gradle.maven.publish)
}

kotlin.jvmToolchain(11)

mavenPublishing {
  val isCI = providers.environmentVariable("CI").isPresent
  publishToMavenCentral(host = SonatypeHost.S01, automaticRelease = true)
  pomFromGradleProperties()
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty()))
  if (isCI) {
    signAllPublications()
  }
}

kotlin {
  androidTarget { publishLibraryVariants("debug", "release") }
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()
  macosX64()
  linuxX64()
  jvm()

  cocoapods {
    summary = "Some description for the Shared Module"
    homepage = "Link to the Shared Module homepage"
    version = providers.gradleProperty("VERSION_NAME").get()
    ios.deploymentTarget = "14.1"
    framework { baseName = "dyte_socketio" }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.ktor.client.core)
        api(libs.ktor.client.websockets)
        api(libs.coroutines.core)
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
      }
    }

    val commonTest by getting { dependencies { implementation(libs.kotlin.test) } }
    val androidMain by getting
    val jvmMain by getting {
      androidMain.dependsOn(this)
      dependencies { api(libs.ktor.client.okhttp) }
    }
    val androidUnitTest by getting
    val jvmTest by getting {
      androidUnitTest.dependsOn(this)
      dependencies {
        implementation(libs.kotlin.test.junit)
        implementation(libs.junit)
      }
    }

    val linuxX64Main by getting { dependencies { api(libs.ktor.client.curl) } }

    val iosX64Main by getting
    val iosArm64Main by getting
    val iosSimulatorArm64Main by getting
    val macosX64Main by getting
    val macosArm64Main by getting
    val darwinMain by creating {
      dependsOn(commonMain)
      iosX64Main.dependsOn(this)
      iosArm64Main.dependsOn(this)
      macosX64Main.dependsOn(this)
      macosArm64Main.dependsOn(this)
      iosSimulatorArm64Main.dependsOn(this)
      dependencies { api(libs.ktor.client.darwin) }
    }

    val iosX64Test by getting
    val iosArm64Test by getting
    val iosSimulatorArm64Test by getting
    val iosTest by creating {
      dependsOn(commonTest)
      iosX64Test.dependsOn(this)
      iosArm64Test.dependsOn(this)
      iosSimulatorArm64Test.dependsOn(this)
    }
  }
}

android {
  namespace = "io.dyte.socketio"
  compileSdk = 32
  defaultConfig {
    minSdk = 21
    targetSdk = 32
  }
}
