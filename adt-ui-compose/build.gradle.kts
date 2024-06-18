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
fun properties(key: String) = project.findProperty(key).toString()

plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij")
  id("org.jetbrains.compose")
}

version = properties("pluginVersion")

repositories {
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  maven("https://packages.jetbrains.team/maven/p/kpm/public/")
  google()
  mavenCentral()
}

kotlin { jvmToolchain(17) }

intellij {
  version.set(properties("platformVersion"))
  type.set(properties("platformType"))

  // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
  plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

dependencies {
  // MUST align version with the Jewel dependency in adt-ui-compose
  // MUST align -XXX suffix in artifact ID to the target IJP version
  // See https://github.com/JetBrains/Jewel/releases for the release notes
  api("org.jetbrains.jewel:jewel-ide-laf-bridge-241:0.19.5") {
    exclude(group = "org.jetbrains.kotlinx")
  }
  api("org.jetbrains.jewel:jewel-markdown-ide-laf-bridge-styling-241:0.19.5") {
    exclude(group = "org.jetbrains.kotlinx")
  }

  // Do not bring in Material (we use Jewel) and Coroutines (the IDE has its own)
  api(compose.desktop.currentOs) {
    exclude(group = "org.jetbrains.compose.material")
    exclude(group = "org.jetbrains.kotlinx")
  }
}

sourceSets {
  main {
    resources { srcDirs("src/resources") }
    kotlin { srcDirs("src") }
  }
}
