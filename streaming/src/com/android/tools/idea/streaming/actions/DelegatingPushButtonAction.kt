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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.streaming.core.PushButtonAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** A push button action that delegates to one of its delegate actions depending on context. */
internal abstract class DelegatingPushButtonAction(vararg delegates: AnAction) : DelegatingAction(*delegates), PushButtonAction {

  override fun buttonPressed(event: AnActionEvent) {
    getPushButtonDelegate(event).buttonPressed(event)
  }

  override fun buttonReleased(event: AnActionEvent) {
    getPushButtonDelegate(event).buttonReleased(event)
  }

  override fun buttonPressedAndReleased(event: AnActionEvent) {
    getPushButtonDelegate(event).buttonPressedAndReleased(event)
  }

  private fun getPushButtonDelegate(event: AnActionEvent): PushButtonAction =
      getLeafDelegate(event) as PushButtonAction
}