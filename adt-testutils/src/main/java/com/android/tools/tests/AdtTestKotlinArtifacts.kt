/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.tests

import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File

/**
 * This class is a highly simplified version of the IJ `TestKotlinArtifacts` object, that can only
 * find artifacts bundled with the Kotlin plugin standalone compiler distribution. Android Studio
 * uses a prebuilt version of IntelliJ to build against, and cannot access the network to download
 * Maven artifacts in presubmit tests, so we're limited to only the JARs that ship with the plugin.
 */
object AdtTestKotlinArtifacts {
  private fun getJar(artifactId: String): File =
    findKotlincArtifact(artifactId)

  private fun getSourcesJar(artifactId: String): File =
    findKotlincArtifact(artifactId, suffix = "-sources.jar")

  private fun findKotlincArtifact(artifactId: String, suffix: String = ".jar"): File {
    val fileName = artifactId + suffix
    val file = KotlinPluginLayout.kotlinc.resolve("lib").resolve(fileName)
    check(file.exists()) { "Could not find bundled kotlinc artifact $fileName" }
    return file
  }

  val kotlinStdlib: File by lazy { getJar("kotlin-stdlib") }
  val kotlinStdlibSources: File by lazy { getSourcesJar("kotlin-stdlib") }
  val kotlinStdlibCommonSources: File by lazy { getSourcesJar("kotlin-stdlib-common") }
}

