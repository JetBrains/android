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
package com.android.tools.idea.logcat

import com.android.ddmlib.Log
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.template.emmet.generators.LoremGenerator
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.text.UniqueNameGenerator
import java.time.Instant
import java.util.Locale.ROOT
import javax.swing.JComponent
import kotlin.random.Random

internal class LogcatToolWindowFactory : SplittingTabsToolWindowFactory(), DumbAware {

  private val logcatColors: LogcatColors = LogcatColors()

  override fun shouldBeAvailable(project: Project): Boolean = StudioFlags.LOGCAT_V2_ENABLE.get()

  override fun generateTabName(tabNames: Set<String>) =
    UniqueNameGenerator.generateUniqueName("Logcat", "", "", " (", ")") { !tabNames.contains(it) }

  override fun createChildComponent(project: Project, popupActionGroup: ActionGroup, clientState: String?): JComponent =
    LogcatMainPanel(project, popupActionGroup, logcatColors, LogcatPanelConfig.fromJson(clientState)).also(::printFakeLogs)
}

// Use a LoremGenerator to generate random tags, app names and messages to be used in fake LogCatMessage's to demonstrate the behavior.
// TODO(aalbert): Remove when we start reading real logs from ADB.
private fun printFakeLogs(it: LogcatMainPanel) {
  val random = Random(0)
  val loremGenerator = LoremGenerator()
  for (logLevel in Log.LogLevel.values()) {
    val messages = mutableListOf<LogCatMessage>()
    for (t in 1..10) {
      val tag = loremGenerator.generateTag(random.nextInt(1, 3))
      val appName = loremGenerator.generateAppName(random.nextInt(2, 3))
      for (line in 1..random.nextInt(5)) {
        val message = loremGenerator.generate(random.nextInt(5, 12), false)
        messages.add(LogCatMessage(LogCatHeader(logLevel, 1324, 5454, appName.take(appName.length - 1), tag, Instant.now()), message))
      }
    }
    it.appendMessages(messages)
  }
}

/**
 * Generate a logcat tag of a certain length.
 */
private fun LoremGenerator.generateTag(wordCount: Int): String =
  generate(wordCount, false).replace(",", "").dropLast(1).split(" ").joinToString(transform = String::capitalize, separator = "")

/**
 * Generate an app name of a certain length.
 */
private fun LoremGenerator.generateAppName(wordCount: Int): String =
  "com." + generate(wordCount, false).toLowerCase(ROOT).replace(",", "").replace(" ", ".").dropLast(1)
