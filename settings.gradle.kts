pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
    exclusiveContent {
      forRepository { maven("https://s01.oss.sonatype.org/content/repositories/snapshots") }
      filter { includeGroupByRegex("io.dyte.*") }
    }
  }
}

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Socket-Io-KMP"

include(":socketio")
