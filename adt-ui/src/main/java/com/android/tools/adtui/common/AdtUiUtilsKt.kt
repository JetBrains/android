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
package com.android.tools.adtui.common

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import javax.swing.JComponent

class AdtUiUtilsKt {
  companion object {
    /**
     * Shows the [JBPopup] above the given [component].
     */
    @JvmStatic
    fun JBPopup.showAbove(component: JComponent) {
      val northWest = RelativePoint(component, Point())

      addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          val popup = event.asPopup()
          val location = Point(popup.locationOnScreen).apply { y = northWest.screenPoint.y - popup.size.height }

          popup.setLocation(location)
        }
      })
      show(northWest)
    }
  }
}