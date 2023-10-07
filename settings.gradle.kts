pluginManagement {
  repositories {
    exclusiveContent {
      forRepository { google() }
      filter {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com.android.*")
        includeGroup("com.google.testing.platform")
      }
    }
    exclusiveContent {
      forRepository { gradlePluginPortal() }
      filter {
        includeModule(
          "org.gradle.toolchains.foojay-resolver-convention",
          "org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
        )
        includeModule("org.gradle.toolchains", "foojay-resolver")
      }
    }
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    exclusiveContent {
      forRepository { google() }
      filter {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com.android.*")
        includeGroup("com.google.android.gms")
        includeGroup("com.google.testing.platform")
        includeModule("com.google.android.material", "material")
      }
    }
    mavenLocal {
      // Only allow Dyte dependencies to come from mavenLocal
      // This doesn't use `exclusiveContent` since they should also be resolvable from Maven Central
      content { includeGroupByRegex("io.dyte.*") }
    }
    mavenCentral()
    // workaround for https://youtrack.jetbrains.com/issue/KT-51379
    exclusiveContent {
      forRepository {
        ivy("https://download.jetbrains.com/kotlin/native/builds") {
          name = "Kotlin Native"
          patternLayout {
            listOf(
                "macos-x86_64",
                "macos-aarch64",
                "osx-x86_64",
                "osx-aarch64",
                "linux-x86_64",
                "windows-x86_64",
              )
              .forEach { os ->
                listOf("dev", "releases").forEach { stage ->
                  artifact("$stage/[revision]/$os/[artifact]-[revision].[ext]")
                }
              }
          }
          metadataSources { artifact() }
        }
      }
      filter { includeModuleByRegex(".*", ".*kotlin-native-prebuilt.*") }
    }
    exclusiveContent {
      forRepository {
        ivy("https://nodejs.org/dist/") {
          name = "Node Distributions at $url"
          patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
          metadataSources { artifact() }
          content { includeModule("org.nodejs", "node") }
        }
      }
      filter { includeGroup("org.nodejs") }
    }
  }
}

rootProject.name = "Socket-Io-KMP"

include(":socketio")
