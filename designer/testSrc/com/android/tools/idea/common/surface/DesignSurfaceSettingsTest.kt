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
package com.android.tools.idea.common.surface

import org.jetbrains.android.AndroidTestCase

class DesignSurfaceSettingsTest: AndroidTestCase() {

  private lateinit var defaultFilePathToZoomLevelMap: MutableMap<String, Double>

  override fun setUp() {
    super.setUp()
    val settings = SurfaceState()
    defaultFilePathToZoomLevelMap = settings.filePathToZoomLevelMap
  }

  override fun tearDown() {
    val settings = SurfaceState()
    try {
      settings.filePathToZoomLevelMap = defaultFilePathToZoomLevelMap
    }
    catch (t: Throwable) {
      addSuppressedException(t)
    }
    finally {
      super.tearDown()
    }
  }

  fun testShareGlobalState() {
    val state1 = DesignSurfaceSettings.getInstance().surfaceState
    val state2 = DesignSurfaceSettings.getInstance().surfaceState

    // The references should be same.
    assertEquals(state1, state2)
  }

  fun testSaveLoadSettings() {
    val surfaceState = DesignSurfaceSettings.getInstance().surfaceState

    val filePathToZoomLevelMap = mutableMapOf<String, Double>()
    filePathToZoomLevelMap["path1"] = 0.1
    filePathToZoomLevelMap["path2"] = 0.2

    surfaceState.filePathToZoomLevelMap = filePathToZoomLevelMap

    // Check the values are same after getting another instance.
    val anotherSurfaceState = DesignSurfaceSettings.getInstance().surfaceState
    assertEquals(filePathToZoomLevelMap, anotherSurfaceState.filePathToZoomLevelMap)
  }
}
