/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.save

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import java.util.Locale

/** Provides methods operating on save location and filename template. */
@Service(Service.Level.PROJECT)
internal class SaveConfiguration(private val project: Project) {

  private val userHome: @NonNls String = System.getProperty("user.home")

  fun expandFilenamePattern(
      saveLocation: String, filenameTemplate: String, fileExtension: String, timestamp: Instant, sequentialNumber: Int): String {
    try {
      val dir = Paths.get(userHome).resolve(expandSaveLocation(saveLocation))
      val time = timestamp.atZone(ZoneId.systemDefault())
      val filename = filenameTemplate
          .replace("%Y", time.get(ChronoField.YEAR).toString())
          .replace("%y", time.get(ChronoField.YEAR).toString().takeLast(2))
          .replace("%M", String.Companion.format(Locale.US, "%02d", time.get(ChronoField.MONTH_OF_YEAR)))
          .replace("%D", String.Companion.format(Locale.US, "%02d", time.get(ChronoField.DAY_OF_MONTH)))
          .replace("%H", String.Companion.format(Locale.US, "%02d", time.get(ChronoField.HOUR_OF_DAY)))
          .replace("%m", String.Companion.format(Locale.US, "%02d", time.get(ChronoField.MINUTE_OF_HOUR)))
          .replace("%S", String.Companion.format(Locale.US, "%02d", time.get(ChronoField.SECOND_OF_MINUTE)))
          .replace("%d", sequentialNumber.toString())
          .replace(Regex("%(\\d+)d")) { match -> String.Companion.format(Locale.ROOT, "%0${match.groupValues[1]}d", sequentialNumber) }
          .replace("%p", project.name)
      return dir.resolve("$filename.$fileExtension").normalize().toString().replace('/', File.separatorChar)
    } catch (_: InvalidPathException) {
      return ""
    }
  }

  fun generalizeSaveLocation(saveLocation: String): String {
    val dir = Paths.get(userHome).resolve(saveLocation).normalize()
    val projectDir = project.guessProjectDir()?.toNioPath()
    return when {
      projectDir != null && dir.startsWith(projectDir) ->
          dir.toString().replaceRange(0, projectDir.toString().length, PROJECT_DIR_MACRO).replace(File.separatorChar, '/')
      dir.startsWith(Paths.get(userHome)) ->
          dir.toString().replaceRange(0, userHome.length, USER_HOME_MACRO).replace(File.separatorChar, '/')
      else -> saveLocation.replace(File.separatorChar, '/')
    }
  }

  fun expandSaveLocation(saveLocation: String): String {
    val dir = saveLocation.replace(File.separatorChar, '/')
    return when {
      saveLocation.startsWithFollowedBySeparator(PROJECT_DIR_MACRO) ->
          project.guessProjectDir()?.toNioPath()?.resolve(dir.substringAfter('/', ""))?.toString() ?:
          "$userHome/Desktop"
      saveLocation.startsWithFollowedBySeparator(USER_HOME_MACRO) -> "$userHome/${dir.substringAfter('/', "")}"
      else -> saveLocation
    }
  }

  companion object {
    const val USER_HOME_MACRO: @NonNls String = "${'$'}USER_HOME$"
    const val DEFAULT_SAVE_LOCATION = "${USER_HOME_MACRO}/Desktop"
    const val PROJECT_DIR_MACRO: @NonNls String = "${'$'}PROJECT_DIR$"

    private fun String.startsWithFollowedBySeparator(prefix: String): Boolean {
      return startsWith(prefix) && (length == prefix.length || this[prefix.length] == '/')
    }
  }
}
