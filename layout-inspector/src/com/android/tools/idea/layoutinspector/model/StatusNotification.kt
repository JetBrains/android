/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.EditorNotificationPanel

/**
 * A notification to be shown on top of the Inspector.
 */
interface StatusNotification {
  val status: EditorNotificationPanel.Status
  val message: String
  val sticky: Boolean
  val actions: List<AnAction>
}

class StatusNotificationImpl(
  override val status: EditorNotificationPanel.Status,
  override val message: String,
  override val sticky: Boolean = false,
  override val actions: List<AnAction> = listOf()
): StatusNotification
