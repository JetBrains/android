/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.semantics

import java.lang.IllegalArgumentException

/**
 * A class representing a specific version of the Android Gradle Plugin.  Unlike GradleVersion in
 * android.sdktools.sdk-common, this makes no attempt to represent declarations of "required versions"
 * or "prefix version ranges" of AGP (in the sense of Gradle's "Simple version declaration semantics").
 */
data class AndroidGradlePluginVersion(
  val major: Int,
  val minor: Int,
  val micro: Int,
  val previewKind: PreviewKind,
  val preview: Int?
): Comparable<AndroidGradlePluginVersion> {
  override fun compareTo(other: AndroidGradlePluginVersion): Int =
    compareValuesBy(this, other, { it.major }, { it.minor }, { it.micro }, { it.previewKind }, { it.preview })

  override fun toString() = "$major.$minor.$micro${previewKind.string}${preview?.toString()?.padStart(2, '0') ?: ""}"

  enum class PreviewKind(val string: String) {
    // The order is significant for comparisons of AGPVersion objects
    ALPHA("-alpha"),
    BETA("-beta"),
    RC("-rc"),
    DEV("-dev"),
    NONE(""),
  }

  companion object {
    fun tryParse(input: String): AndroidGradlePluginVersion? {
      // The (regular) language we recognize is: NUM "." NUM "." NUM (PREVIEW-KIND digit digit | "-dev")?
      // where
      //   NUM = "0" | [1-9] digit*
      // and
      //   PREVIEW-KIND = "-" ("alpha" | "beta" | "rc")
      // (technically this lets through the potentially-invalid -alpha00 which we could fix if necessary)
      val pattern = let {
        val digit = "[0-9]"
        val num = "(?:0|[1-9]$digit*)"
        val previewKind = "-(?:alpha|beta|rc)"
        val dot = Regex.escape(".")
        val dev = "-dev"
        "($num)$dot($num)$dot($num)(?:($previewKind)($digit$digit)|($dev))?"
      }
      val regex = Regex(pattern)
      val matchResult = regex.matchEntire(input) ?: return null
      val matchList = matchResult.destructured.toList()
      val major = matchList[0].toIntOrNull() ?: return null
      val minor = matchList[1].toIntOrNull() ?: return null
      val micro = matchList[2].toIntOrNull() ?: return null
      val previewKind = when {
        matchList[3] == "-alpha" -> PreviewKind.ALPHA
        matchList[3] == "-beta" -> PreviewKind.BETA
        matchList[3] == "-rc" -> PreviewKind.RC
        matchList[5] == "-dev" -> PreviewKind.DEV
        else -> PreviewKind.NONE
      }
      val preview = when(previewKind) {
        PreviewKind.ALPHA, PreviewKind.BETA, PreviewKind.RC -> matchList[4].toIntOrNull() ?: return null
        else -> null
      }
      return AndroidGradlePluginVersion(major, minor, micro, previewKind, preview)
    }

    fun parse(input: String) = tryParse(input) ?: throw IllegalArgumentException("`$input` is not a legal AGP version")
  }
}