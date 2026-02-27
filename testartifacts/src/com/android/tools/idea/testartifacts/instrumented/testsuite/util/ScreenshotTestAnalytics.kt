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
package com.android.tools.idea.testartifacts.instrumented.testsuite.util

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

private val LOG = Logger.getInstance(ScreenshotToolbarAnalytics::class.java)

/** Logs a screenshot test event with the specified type. */
fun logScreenshotTestEvent(type: ScreenshotTestComposePreviewEvent.Type, project: Project?) {
  LOG.debug("Logging screenshot test event: kind=SCREENSHOT_TEST_COMPOSE_PREVIEW, type=$type, project=${project?.name ?: "unknown"}")
  val event = ScreenshotTestComposePreviewEvent.newBuilder().setType(type).build()

  val studioEvent =
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW)
      .setScreenshotTestComposePreviewEvent(event)
      .withProjectId(project)

  UsageTracker.log(studioEvent)
}

/** A helper class to handle toolbar action logging once per session. */
class ScreenshotToolbarAnalytics(private val project: Project?) {
  private var hasLogged = false

  fun logAction() {
    if (!hasLogged) {
      logScreenshotTestEvent(ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_TOOLBAR_ACTION, project)
      hasLogged = true
    }
  }
}

/** Wraps an [AnAction] to log analytics before delegating the action. */
class LoggedAction(private val delegate: AnAction, private val toolbarAnalytics: ScreenshotToolbarAnalytics) : AnAction() {
  init {
    copyFrom(delegate)
  }

  override fun actionPerformed(e: AnActionEvent) {
    toolbarAnalytics.logAction()
    delegate.actionPerformed(e)
  }

  override fun update(e: AnActionEvent) {
    delegate.update(e)
  }
}

/** Wraps a [ToggleAction] to log analytics before delegating the toggle. */
class LoggedToggleAction(private val delegate: ToggleAction, private val toolbarAnalytics: ScreenshotToolbarAnalytics) :
  ToggleAction(delegate.templatePresentation.text, delegate.templatePresentation.description, delegate.templatePresentation.icon) {

  override fun isSelected(e: AnActionEvent): Boolean = delegate.isSelected(e)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    toolbarAnalytics.logAction()
    delegate.setSelected(e, state)
  }

  override fun update(e: AnActionEvent) {
    delegate.update(e)
  }
}
