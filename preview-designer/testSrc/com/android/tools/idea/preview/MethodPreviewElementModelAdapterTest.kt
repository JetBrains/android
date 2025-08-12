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
package com.android.tools.idea.preview

import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlDataProviderHolder
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private class TestModel(override var dataProvider: NlDataProvider?) : NlDataProviderHolder {
  override fun dispose() {}
}

private val PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<TestMethodPreviewElement>("TestMethodPreviewElement")

private class TestAdapter :
  MethodPreviewElementModelAdapter<TestMethodPreviewElement, TestModel>(PREVIEW_ELEMENT_INSTANCE) {
  override fun toXml(previewElement: TestMethodPreviewElement) = ""

  override fun applyToConfiguration(
    previewElement: TestMethodPreviewElement,
    configuration: Configuration,
  ) {}

  override fun createLightVirtualFile(content: String, backedFile: VirtualFile, id: Long) =
    LightVirtualFile()
}

class MethodPreviewElementModelAdapterTest {
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val applicationRule = ApplicationRule()

  private val adapter = TestAdapter()

  @Test
  fun testModelAndPreviewElementConnection() {
    val adapter = TestAdapter()

    val element = TestMethodPreviewElement(methodFqn = "foo")

    val model = TestModel(adapter.createDataProvider(element))
    Disposer.register(disposableRule.disposable, model)

    Assert.assertEquals(element, adapter.modelToElement(model))

    Disposer.dispose(model)

    Assert.assertNull(adapter.modelToElement(model))
  }

  @Test
  fun testLogString() {
    val previewElement =
      TestMethodPreviewElement(
        methodFqn = "someMethodFqn",
        displaySettings = someDisplaySettings(name = "preview settings name"),
      )

    assertEquals(
      """
        displayName=preview settings name
        methodName=someMethodFqn
    """
        .trimIndent(),
      adapter.toLogString(previewElement),
    )
  }
}
