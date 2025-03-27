/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.wear.preview

import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTilePreviewElementTest {
  @Test
  fun twoPreviewElementsWithTheSameValuesShouldBeEqual() {
    val previewElement1 =
      WearTilePreviewElement<Int>(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            "0xffabcd",
          ),
        previewElementDefinition = 1,
        previewBody = 2,
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.Companion.cleanAndGet(device = "id:wearos_small_round"),
      )

    val previewElement2 =
      WearTilePreviewElement(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            "0xffabcd",
          ),
        previewElementDefinition = 1,
        previewBody = 2,
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.Companion.cleanAndGet(device = "id:wearos_small_round"),
      )

    assertEquals(previewElement1, previewElement2)
  }

  @Test
  fun testCreateDerivedInstance() {
    val originalPreviewElement =
      WearTilePreviewElement(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            "0xffabcd",
          ),
        previewElementDefinition = 1,
        previewBody = 2,
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.Companion.cleanAndGet(device = "id:wearos_small_round"),
      )

    val newPreviewDisplaySettings =
      PreviewDisplaySettings(
        "derived name",
        "derived base name",
        "parameter name",
        "derived group",
        true,
        true,
        "0xffffff",
      )
    val newConfig =
      PreviewConfiguration.Companion.cleanAndGet(
        device = "id:wearos_square",
        fontScale = 3f,
        locale = "fr-FR",
      )

    val derivedPreviewElement =
      originalPreviewElement.createDerivedInstance(newPreviewDisplaySettings, newConfig)

    assertEquals(newPreviewDisplaySettings, derivedPreviewElement.displaySettings)
    assertEquals(newConfig, derivedPreviewElement.configuration)
    assertEquals(originalPreviewElement.methodFqn, derivedPreviewElement.methodFqn)
    assertEquals(originalPreviewElement.instanceId, derivedPreviewElement.instanceId)
    assertEquals(
      originalPreviewElement.previewElementDefinition,
      derivedPreviewElement.previewElementDefinition,
    )
    assertEquals(originalPreviewElement.previewBody, derivedPreviewElement.previewBody)
    assertEquals(originalPreviewElement.hasAnimations, derivedPreviewElement.hasAnimations)
  }

  @Test
  fun testToPreviewXml() {
    val previewElement =
      WearTilePreviewElement(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            "0xffabcd",
          ),
        previewElementDefinition = null,
        previewBody = null,
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
      )

    assertEquals(
      """<androidx.wear.tiles.tooling.TileServiceViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="0xffabcd"
    android:minWidth="1px"
    android:minHeight="1px"
    tools:tilePreviewMethodFqn="someMethodFqn" />

"""
        .trimIndent(),
      previewElement.toPreviewXml().buildString(),
    )
  }

  @Test
  fun testToPreviewXmlBackgroundDefaultsToBlack() {
    val previewElement =
      WearTilePreviewElement(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            backgroundColor = null,
          ),
        previewElementDefinition = null,
        previewBody = null,
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
      )

    assertEquals(
      """<androidx.wear.tiles.tooling.TileServiceViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#ff000000"
    android:minWidth="1px"
    android:minHeight="1px"
    tools:tilePreviewMethodFqn="someMethodFqn" />

"""
        .trimIndent(),
      previewElement.toPreviewXml().buildString(),
    )
  }
}
