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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.Version
import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.ide.common.repository.GradleVersion
import java.util.regex.Pattern

/**
 * Marker interface for all exceptions that are triggered via Android specific errors in project import.
 */
open class AndroidSyncException : RuntimeException {
  constructor(message: String) : super(message)
  constructor() : super()
}

class AgpVersionTooOld(agpVersion: GradleVersion) : AndroidSyncException(generateMessage(agpVersion)) {
  companion object {
    private const val LEFT = "The project is using an incompatible version (AGP "
    private const val RIGHT = ") of the Android Gradle plugin. Minimum supported version is AGP $GRADLE_PLUGIN_MINIMUM_VERSION."
    private fun generateMessage(agpVersion: GradleVersion) = "$LEFT$agpVersion$RIGHT"
    val PATTERN: Pattern = Pattern.compile("${Pattern.quote(LEFT)}(.+)${Pattern.quote(RIGHT)}")
    val ALWAYS_PRESENT_STRINGS = listOf(LEFT, RIGHT)
  }
}

class AgpVersionIncompatible(agpVersion: GradleVersion) : AndroidSyncException(generateMessage(agpVersion)) {
  companion object {
    private const val A = "The project is using an incompatible preview version (AGP "
    private const val B = ") of the Android Gradle plugin. Current compatible "
    private const val PREVIEW = "preview "
    private val C = "version is AGP $ANDROID_GRADLE_PLUGIN_VERSION."
    private fun generateMessage(agpVersion: GradleVersion): String {
      val latestKnown = GradleVersion.parseAndroidGradlePluginVersion(ANDROID_GRADLE_PLUGIN_VERSION)
      return "$A$agpVersion$B${if (latestKnown.isPreview) PREVIEW else ""}$C"
    }

    val PATTERN: Pattern = GradleVersion.parseAndroidGradlePluginVersion(ANDROID_GRADLE_PLUGIN_VERSION).let { latestKnown ->
      Pattern.compile(
        "${Pattern.quote(A)}(.+)${Pattern.quote(B)}${if (latestKnown.isPreview) Pattern.quote(PREVIEW) else ""}${Pattern.quote(C)}")
    }
    val ALWAYS_PRESENT_STRINGS = listOf(A, B, C)
  }
}