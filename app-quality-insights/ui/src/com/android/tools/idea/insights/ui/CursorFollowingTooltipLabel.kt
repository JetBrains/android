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
package com.android.tools.idea.insights.ui

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JLabel

class CursorFollowingTooltipLabel(parentDisposable: Disposable, icon: Icon, helpText: String) :
  JLabel(icon, LEFT), Disposable {
  init {
    Disposer.register(parentDisposable, this)
    // This mouse listener needs to be added before the HelpTooltip is installed so that
    // the tooltip is hidden before HelpTooltip's mouse listeners act on it.
    addMouseMotionListener(
      object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
          HelpTooltip.hide(this@CursorFollowingTooltipLabel)
        }
      }
    )

    HelpTooltip().setDescription(helpText).installOn(this)
  }

  override fun dispose() {
    HelpTooltip.dispose(this)
  }
}
