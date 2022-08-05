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
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.TestOnly

class ZoomActualAction private constructor(): SetZoomAction(ZoomType.ACTUAL) {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = event.getData(ZOOMABLE_KEY)?.canZoomToActual() ?: false
  }

  companion object {
    @JvmStatic
    fun getInstance(): ZoomActualAction {
      return ActionManager.getInstance().getAction("Adtui.ZoomToActualAction") as ZoomActualAction
    }

    /**
     * Create [ZoomActualAction] instance if the test environment doesn't load adt-ui.xml. Do not use this function in production code.
     */
    @TestOnly
    @JvmStatic
    fun createInstance(): ZoomActualAction = ZoomActualAction()
  }
}
