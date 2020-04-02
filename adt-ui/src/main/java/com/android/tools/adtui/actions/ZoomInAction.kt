/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.actions

import com.android.tools.adtui.ZOOMABLE_KEY
import com.intellij.ReviseWhenPortedToJDK
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Delayed between 2 continuous performing.
 * TODO (b/150939617): Remove this and related code when switching to JDK 11.
 */
@ReviseWhenPortedToJDK("11")
private const val PERFORM_DELAY_MS: Long = 50

/**
 * TODO(b/149212539): make constructor private after resolving failed test cases.
 */
class ZoomInAction : SetZoomAction(ZoomType.IN) {

  private var lastZoomInMs: Long = 0

  companion object {
    @JvmStatic
    fun getInstance() = ActionManager.getInstance().getAction(AdtuiActions.ZOOM_IN_ACTION) as ZoomInAction
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = event.getData(ZOOMABLE_KEY)?.canZoomIn() ?: false
    if (event.place.contains("Surface")) {
      // Use different icon when it is in floating action bar.
      event.presentation.icon = AllIcons.General.Add
    }
  }

  @ReviseWhenPortedToJDK("11")
  override fun actionPerformed(event: AnActionEvent) {
    // In JDK 8, there is a bug that the input event "meta =" (which is the default shortcut on Mac) is dispatched twice in only one pressing.
    // This bug is not exist in JDK 11, we workaround here before we switch to JDK 11.
    // TODO (b/150939617): Remove this and related code when switching to JDK 11.
    val eventTimeMs = event.inputEvent?.`when`
    if (eventTimeMs != null) {
      if (eventTimeMs - lastZoomInMs < PERFORM_DELAY_MS) {
        return
      }
      lastZoomInMs = eventTimeMs
    }
    super.actionPerformed(event)
  }
}
