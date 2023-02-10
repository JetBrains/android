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
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.junit.After
import org.junit.Assert
import org.junit.Test

private class TestPreviewElement(
  fqcn: String,
  displaySettings: PreviewDisplaySettings = simplestDisplaySettings(),
  previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>? = null,
  previewBodyPsi: SmartPsiElementPointer<PsiElement>? = null,
) : WearTilePreviewElement(
  displaySettings,
  previewElementDefinitionPsi,
  previewBodyPsi,
  fqcn
)

private class TestModel(override var dataContext: DataContext) : DataContextHolder {
  override fun dispose() {}
}

private fun simplestDisplaySettings() = PreviewDisplaySettings("", null, false, false, null)

class WearTilePreviewElementModelAdapterTest {
  private val rootDisposable = Disposer.newDisposable()

  @Test
  fun testCalcAffinityPriority() {
    val pe1 = TestPreviewElement("foo")
    val pe2 = TestPreviewElement("foo")
    val pe3 = TestPreviewElement("foo", PreviewDisplaySettings("foo", null, false, false, null))
    val pe4 = TestPreviewElement("bar")

    val adapter = WearTilePreviewElementModelAdapter<TestModel>()

    Assert.assertTrue(adapter.calcAffinity(pe1, pe1) < adapter.calcAffinity(pe1, pe2))
    Assert.assertTrue(adapter.calcAffinity(pe1, pe2) < adapter.calcAffinity(pe1, pe3))
    Assert.assertTrue(adapter.calcAffinity(pe1, pe3) < adapter.calcAffinity(pe1, null))
    Assert.assertTrue(adapter.calcAffinity(pe1, null) < adapter.calcAffinity(pe1, pe4))
  }

  @Test
  fun testModelAndPreviewElementConnection() {
    val adapter = WearTilePreviewElementModelAdapter<TestModel>()

    val element = TestPreviewElement("foo")

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
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    tools:tileServiceName="foo" />

"""
        .trimIndent(),
      WearTilePreviewElementModelAdapter<TestModel>().toXml(
        WearTilePreviewElement(simplestDisplaySettings(), null, null, "foo")
      )
    )
  }

  @After
  fun tearDown() {
    Disposer.dispose(rootDisposable)
  }
}