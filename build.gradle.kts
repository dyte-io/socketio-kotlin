plugins {
  // trick: for the same plugin versions in all sub-modules
  id("com.android.library").version("8.1.2").apply(false)
  kotlin("multiplatform").version("1.9.0").apply(false)
  id("io.dyte.gradle.plugin.base") version "0.0.4"
}

tasks.register("cleanBuildDir", Delete::class) { delete(rootProject.buildDir) }
