/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.common.model.DataContextHolder
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.LightVirtualFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestPreviewElement(
  override val methodFqcn: String,
  override val displaySettings: PreviewDisplaySettings = simplestDisplaySettings(),
  override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>? = null,
  override val previewBodyPsi: SmartPsiElementPointer<PsiElement>? = null,
) : MethodPreviewElement

internal class TestModel(override var dataContext: DataContext) : DataContextHolder {
  override fun dispose() {}
}

private fun simplestDisplaySettings() = PreviewDisplaySettings("", null, false, false, null)

private class TestAdapter : GlancePreviewElementModelAdapter<TestPreviewElement, TestModel>() {
  override fun toXml(previewElement: TestPreviewElement) = ""

  override fun createLightVirtualFile(content: String, backedFile: VirtualFile, id: Long) =
    LightVirtualFile()
}

class GlancePreviewElementModelAdapterTest {
  private val rootDisposable = Disposer.newDisposable()

  @Test
  fun testCalcAffinityPriority() {
    val pe1 = TestPreviewElement("foo")
    val pe2 = TestPreviewElement("foo")
    val pe3 = TestPreviewElement("foo", PreviewDisplaySettings("foo", null, false, false, null))
    val pe4 = TestPreviewElement("bar")

    val adapter = TestAdapter()

    assertTrue(adapter.calcAffinity(pe1, pe1) < adapter.calcAffinity(pe1, pe2))
    assertTrue(adapter.calcAffinity(pe1, pe2) < adapter.calcAffinity(pe1, pe3))
    assertTrue(adapter.calcAffinity(pe1, pe3) < adapter.calcAffinity(pe1, null))
    assertTrue(adapter.calcAffinity(pe1, null) < adapter.calcAffinity(pe1, pe4))
  }

  @Test
  fun testModelAndPreviewElementConnection() {
    val adapter = TestAdapter()

    val element = TestPreviewElement("foo")

    val model = TestModel(adapter.createDataContext(element))
    Disposer.register(rootDisposable, model)

    assertEquals(element, adapter.modelToElement(model))

    Disposer.dispose(model)

    assertNull(adapter.modelToElement(model))
  }

  @Test
  fun testAppWidgetXml() {
    assertEquals(
      """<androidx.glance.appwidget.preview.GlanceAppWidgetViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    tools:composableName="foo" />

"""
        .trimIndent(),
      AppWidgetModelAdapter.toXml(
        GlancePreviewElement(simplestDisplaySettings(), null, null, "foo")
      )
    )
  }

  @Test
  fun testWearTilesXml() {
    assertEquals(
      """<androidx.glance.wear.tiles.preview.GlanceTileServiceViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    tools:composableName="foo" />

"""
        .trimIndent(),
      WearTilesModelAdapter.toXml(
        GlancePreviewElement(simplestDisplaySettings(), null, null, "foo")
      )
    )
  }

  @After
  fun tearDown() {
    Disposer.dispose(rootDisposable)
  }
}
