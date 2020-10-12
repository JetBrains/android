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
package com.android.tools.idea.uibuilder.visual

import org.jetbrains.android.AndroidTestCase

class VisualizationToolProjectSettingsTest: AndroidTestCase() {

  private var defaultScale: Double = 0.0

  override fun setUp() {
    super.setUp()
    val settings = VisualizationToolProjectSettings.getInstance(project)
    defaultScale = settings.projectState.scale
  }

  override fun tearDown() {
    val settings = VisualizationToolProjectSettings.getInstance(project)
    try {
      settings.projectState.scale = defaultScale
    }
    catch (t: Throwable) {
      addSuppressedException(t)
    }
    finally {
      super.tearDown()
    }
  }

  fun testShareGlobalState() {
    val settings1 = VisualizationToolProjectSettings.getInstance(project)
    val settings2 = VisualizationToolProjectSettings.getInstance(project)

    assertEquals(settings1.projectState, settings2.projectState)
  }

  fun testSaveLoadSettings() {
    val settings = VisualizationToolProjectSettings.getInstance(project)
    val scale = 0.1
    settings.projectState.scale = scale

    // Check the values are same after getting another instance.
    val anotherSettings = VisualizationToolProjectSettings.getInstance(project)
    assertEquals(scale, anotherSettings.projectState.scale)
  }
}
