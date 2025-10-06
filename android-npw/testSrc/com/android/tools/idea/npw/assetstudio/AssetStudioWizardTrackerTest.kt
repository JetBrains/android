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
package com.android.tools.idea.npw.assetstudio

import com.android.tools.idea.npw.assetstudio.assets.ImageAsset
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import junit.framework.TestCase.assertFalse
import kotlin.test.assertTrue
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AssetStudioWizardTrackerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private var iconGenerator: LauncherIconGenerator? = null

  @After
  fun tearDown() {
    Disposer.dispose(iconGenerator!!)
  }

  @Test
  fun `test track monochrome when icon created`() {
    var isMonochromeTracked = false
    val iconGenerator =
      setUpLauncherIconGenerator(
        object : AssetStudioWizardTracker {
          override fun logMonochromeIconGenerated() {
            isMonochromeTracked = true
          }
        }
      )
    iconGenerator.createOptions(false)
    assertTrue(isMonochromeTracked)
  }

  @Test
  fun `test don't track monochrome icon when no monochrome icon generated`() {
    var isMonochromeTracked = false
    val iconGenerator =
      setUpLauncherIconGenerator(
        shouldCreateMonochrome = false,
        trackingTest =
          object : AssetStudioWizardTracker {
            override fun logMonochromeIconGenerated() {
              isMonochromeTracked = true
            }
          },
      )
    iconGenerator.createOptions(false)
    assertFalse(isMonochromeTracked)
  }

  @Test
  fun `test don't track monochrome icon when creating for preview`() {
    var isMonochromeTracked = false
    val iconGenerator =
      setUpLauncherIconGenerator(
        object : AssetStudioWizardTracker {
          override fun logMonochromeIconGenerated() {
            isMonochromeTracked = true
          }
        }
      )
    iconGenerator.createOptions(true)
    assertFalse(isMonochromeTracked)
  }

  private fun setUpLauncherIconGenerator(
    trackingTest: AssetStudioWizardTracker,
    shouldCreateMonochrome: Boolean = true,
  ): LauncherIconGenerator {
    iconGenerator = LauncherIconGenerator(projectRule.project, 15, null, trackingTest)
    iconGenerator?.backgroundImageAsset()?.setNullableValue(ImageAsset())
    iconGenerator?.outputName()?.set("ic_launcher")
    iconGenerator?.foregroundLayerName()?.set("ic_launcher_foreground")
    iconGenerator?.backgroundLayerName()?.set("ic_launcher_background")
    if (shouldCreateMonochrome) {
      iconGenerator?.monochromeImageAsset()?.setNullableValue(ImageAsset())
      iconGenerator?.backgroundLayerName()?.set("ic_launcher_monochrome")
    }
    return iconGenerator!!
  }

  private fun createVirtualFile(): VirtualFile {
    @Language("xml")
    val drawableContent =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <shape xmlns:android="http://schemas.android.com/apk/res/android"
            android:shape="rectangle"
            android:tint="#FF0000">
        </shape>
        """
        .trimIndent()
    return LightVirtualFile("test_resource.xml", drawableContent)
  }
}
