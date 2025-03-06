/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij.platform.module")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

repositories {
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  maven("https://packages.jetbrains.team/maven/p/kpm/public/")
  google()
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(libs.versions.idea)
    bundledPlugin("org.jetbrains.kotlin")
  }
}

dependencies {
  // MUST align version with the Jewel dependency in adt-ui-compose
  // MUST align -XXX suffix in artifact ID to the target IJP version
  // See https://github.com/JetBrains/Jewel/releases for the release notes
  api("org.jetbrains.jewel:jewel-ide-laf-bridge-243:0.27.0") {
    exclude(group = "org.jetbrains.kotlinx")
  }
  api("org.jetbrains.jewel:jewel-markdown-ide-laf-bridge-styling-243:0.27.0") {
    exclude(group = "org.jetbrains.kotlinx")
  }

  // Do not bring in Material (we use Jewel) and Coroutines (the IDE has its own)
  api(compose.desktop.currentOs) {
    exclude(group = "org.jetbrains.compose.material")
    exclude(group = "org.jetbrains.kotlinx")
  }

  api("androidx.lifecycle:lifecycle-runtime:2.8.5")
  testApi(compose.desktop.uiTestJUnit4)
  testApi("org.jetbrains.jewel:jewel-int-ui-standalone-243:0.27.0")
  testApi("org.jetbrains.jewel:jewel-markdown-int-ui-standalone-styling-243:0.27.0")
}

sourceSets {
  main {
    resources { srcDirs("src/resources") }
    kotlin { srcDirs("src") }
  }
  test {
    resources { srcDirs("testResources") }
    kotlin { srcDirs("testSrc") }
  }
}

tasks {
  withType<KotlinCompile> {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
      apiVersion.set(KotlinVersion.KOTLIN_1_9)
      languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
  }
}

configurations {
  val test by creating {
    // Copy from testImplementation
    extendsFrom(configurations.testImplementation.get())
  }
}

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")
  from(sourceSets.test.get().output)
}

artifacts {
  // Used by other modules' tests
  add("test", tasks.named<Jar>("testJar"))
}
