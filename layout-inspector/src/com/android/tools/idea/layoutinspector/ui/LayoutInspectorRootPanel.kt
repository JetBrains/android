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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
val LAYOUT_INSPECTOR_DATA_KEY = DataKey.create<LayoutInspector>(LayoutInspector::class.java.name)

/**
 * Panel that should always be at the root of Layout Inspector hierarchy. It is responsible for
 * providing [LayoutInspector] instance through [com.intellij.ide.DataManager]
 */
class LayoutInspectorRootPanel(content: Component, private val layoutInspector: LayoutInspector) :
  BorderLayoutPanel(), UiDataProvider {
  companion object {
    fun get(event: AnActionEvent) = event.getData(LAYOUT_INSPECTOR_DATA_KEY)
  }

  init {
    addToCenter(content)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[LAYOUT_INSPECTOR_DATA_KEY] = layoutInspector
  }
}
