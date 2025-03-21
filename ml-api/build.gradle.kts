import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
  id("org.jetbrains.intellij.platform.module")
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(libs.versions.idea)
    instrumentationTools()
    bundledPlugin("org.jetbrains.kotlin")
  }
}

sourceSets {
  main {
    resources {
      srcDirs("src/resources")
    }
  }
  test {
    resources {
      srcDirs("src/testSrc/resources")
    }
  }
}

// This is needed so that other tests can depend on ml-api-tests
// i.e. testImplementation(project(":ml-api", configuration="test")) in ij-platform
configurations {
  val test by creating {
    extendsFrom(configurations.testImplementation.get())
  }
}

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")
  from(sourceSets.test.get().output)
}

artifacts {
  add("test", tasks.named<Jar>("testJar"))
}
