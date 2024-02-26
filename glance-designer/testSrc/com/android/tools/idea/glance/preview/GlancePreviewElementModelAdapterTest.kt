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
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TestModel(override var dataContext: DataContext) : DataContextHolder {
  override fun dispose() {}
}

private fun simplestDisplaySettings(name: String = "") =
  PreviewDisplaySettings(name, null, false, false, null)

private class TestAdapter : GlancePreviewElementModelAdapter<TestModel>() {
  override fun toXml(previewElement: PsiGlancePreviewElement) = ""

  override fun createLightVirtualFile(content: String, backedFile: VirtualFile, id: Long) =
    LightVirtualFile()
}

private fun glancePreviewElement(
  methodFqn: String,
  displaySettings: PreviewDisplaySettings = simplestDisplaySettings(),
) =
  PsiGlancePreviewElement(
    displaySettings = displaySettings,
    previewElementDefinition = null,
    previewBody = null,
    methodFqn = methodFqn,
    configuration = PreviewConfiguration.cleanAndGet(),
  )

class GlancePreviewElementModelAdapterTest {
  private val rootDisposable = Disposer.newDisposable()

  @Test
  fun testCalcAffinityPriority() {
    val pe1 = glancePreviewElement(methodFqn = "foo")
    val pe2 = glancePreviewElement(methodFqn = "foo")
    val pe3 = glancePreviewElement(methodFqn = "foo", simplestDisplaySettings(name = "foo"))
    val pe4 = glancePreviewElement(methodFqn = "bar")

    val adapter = TestAdapter()

    assertTrue(adapter.calcAffinity(pe1, pe1) == adapter.calcAffinity(pe1, pe2))
    assertTrue(adapter.calcAffinity(pe1, pe2) < adapter.calcAffinity(pe1, pe3))
    assertTrue(adapter.calcAffinity(pe1, pe3) < adapter.calcAffinity(pe1, null))
    assertTrue(adapter.calcAffinity(pe1, null) < adapter.calcAffinity(pe1, pe4))
  }

  @Test
  fun testModelAndPreviewElementConnection() {
    val adapter = TestAdapter()

    val element = glancePreviewElement(methodFqn = "foo")

    val model = TestModel(adapter.createDataContext(element))
    Disposer.register(rootDisposable, model)

    assertEquals(element, adapter.modelToElement(model))

    Disposer.dispose(model)

    assertNull(adapter.modelToElement(model))
  }

  @Test
  fun testAppWidgetXml_defaultSize() {
    assertEquals(
      """<androidx.glance.appwidget.preview.GlanceAppWidgetViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:minWidth="1px"
    android:minHeight="1px"
    tools:composableName="foo" />

"""
        .trimIndent(),
      AppWidgetModelAdapter.toXml(
        GlancePreviewElement(
          simplestDisplaySettings(),
          null,
          null,
          "foo",
          PreviewConfiguration.cleanAndGet(),
        )
      ),
    )
  }

  @Test
  fun testAppWidgetXml_withSize() {
    assertEquals(
      """<androidx.glance.appwidget.preview.GlanceAppWidgetViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="1234dp"
    android:layout_height="2000dp"
    android:minWidth="1px"
    android:minHeight="1px"
    tools:composableName="foo" />

"""
        .trimIndent(),
      AppWidgetModelAdapter.toXml(
        GlancePreviewElement(
          simplestDisplaySettings(),
          null,
          null,
          "foo",
          // height cannot be higher than 2000
          PreviewConfiguration.cleanAndGet(width = 1234, height = 5678),
        )
      ),
    )
  }

  @After
  fun tearDown() {
    Disposer.dispose(rootDisposable)
  }
}
