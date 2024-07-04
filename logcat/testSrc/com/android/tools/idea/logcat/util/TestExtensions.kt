/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.util

import com.android.tools.adtui.TreeWalker
import com.android.tools.analytics.UsageTrackerRule
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.LOGCAT_USAGE
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JButton
import javax.swing.JCheckBox
import kotlin.test.fail

/** Convenient extension functions used by tests */
internal fun DialogWrapper.getCheckBox(text: String): JCheckBox = getTextComponent(text) { it.text }

internal fun DialogWrapper.getButton(text: String): JButton = getTextComponent(text) { it.text }

private inline fun <reified T> DialogWrapper.getTextComponent(
  text: String,
  getText: (T) -> String,
): T =
  TreeWalker(rootPane).descendants().filterIsInstance<T>().firstOrNull { getText(it) == text }
    ?: fail("${T::class.simpleName} '$text' not found")

internal fun UsageTrackerRule.logcatEvents(): List<LogcatUsageEvent> =
  usages.filter { it.studioEvent.kind == LOGCAT_USAGE }.map { it.studioEvent.logcatUsageEvent }
