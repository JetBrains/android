/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.common.model.DataContextHolder
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.junit.After
import org.junit.Assert
import org.junit.Test

private fun wearTilePreviewElement(
  methodFqn: String,
  displaySettings: PreviewDisplaySettings = simplestDisplaySettings(),
  previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>? = null,
  previewBodyPsi: SmartPsiElementPointer<PsiElement>? = null,
) =
  WearTilePreviewElement(
    displaySettings = displaySettings,
    previewElementDefinitionPsi = previewElementDefinitionPsi,
    previewBodyPsi = previewBodyPsi,
    methodFqn = methodFqn,
    configuration = WearTilePreviewConfiguration.forValues(device = "id:wearos_small_round")
  )

private class TestModel(override var dataContext: DataContext) : DataContextHolder {
  override fun dispose() {}
}

private fun simplestDisplaySettings(
  name: String = ""
) = PreviewDisplaySettings(name, null, false, false, null)

class WearTilePreviewElementModelAdapterTest {
  private val rootDisposable = Disposer.newDisposable()

  @Test
  fun testCalcAffinityPriority() {
    val pe1 = wearTilePreviewElement(methodFqn = "foo")
    val pe2 = wearTilePreviewElement(methodFqn = "foo")
    val pe3 = wearTilePreviewElement(methodFqn = "foo", simplestDisplaySettings(name = "foo"))
    val pe4 = wearTilePreviewElement(methodFqn = "bar")

    val adapter = WearTilePreviewElementModelAdapter<TestModel>()

    Assert.assertTrue(adapter.calcAffinity(pe1, pe1) < adapter.calcAffinity(pe1, pe2))
    Assert.assertTrue(adapter.calcAffinity(pe1, pe2) < adapter.calcAffinity(pe1, pe3))
    Assert.assertTrue(adapter.calcAffinity(pe1, pe3) < adapter.calcAffinity(pe1, null))
    Assert.assertTrue(adapter.calcAffinity(pe1, null) < adapter.calcAffinity(pe1, pe4))
  }

  @Test
  fun testModelAndPreviewElementConnection() {
    val adapter = WearTilePreviewElementModelAdapter<TestModel>()

    val element = wearTilePreviewElement(methodFqn = "foo")

    val model = TestModel(adapter.createDataContext(element))
    Disposer.register(rootDisposable, model)

    Assert.assertEquals(element, adapter.modelToElement(model))

    Disposer.dispose(model)

    Assert.assertNull(adapter.modelToElement(model))
  }

  @Test
  fun testWearTilesXml() {
    Assert.assertEquals(
      """<androidx.wear.tiles.tooling.TileServiceViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#ff000000"
    tools:tilePreviewMethodFqn="foo" />

"""
        .trimIndent(),
      WearTilePreviewElementModelAdapter<TestModel>()
        .toXml(
          WearTilePreviewElement(
            displaySettings = simplestDisplaySettings(),
            previewElementDefinitionPsi = null,
            previewBodyPsi = null,
            methodFqn = "foo",
            configuration = WearTilePreviewConfiguration.forValues(device = "id:wearos_small_round")
          )
        )
    )
  }

  @After
  fun tearDown() {
    Disposer.dispose(rootDisposable)
  }
}
