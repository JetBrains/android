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
package com.android.tools.idea.preview

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/**
 * Ideally, this functionality should be a part of the model (currently only [NlModel]) implementation for the case where the previewable
 * is not a Xml file. However, currently [NlModel] and the rest of the rendering pipeline assumes that only a Xml layout can be previewed.
 * This interface is a way to adapt that rigidity of the system to be able to preview a generic [PreviewElement].
 */
interface PreviewElementModelAdapter<T : PreviewElement, M> {
  /**
   * Returns a number indicating how [el1] [PreviewElement] is to the [el2] [PreviewElement]. 0 meaning they are equal and higher the number
   * the more dissimilar they are. This allows for, when re-using models, the model with the most similar [PreviewElement] is re-used. When
   * the user is just switching groups or selecting a specific model, this allows switching to the existing preview faster.
   */
  fun calcAffinity(el1: T, el2: T?): Int

  fun toXml(previewElement: T): String

  /**
   * Technically it should be applied to the model, however, we have the cases (see PreviewElementRenderer) where we can render without a
   * model instance
   */
  fun applyToConfiguration(previewElement: T, configuration: Configuration)

  fun modelToElement(model: M): T?

  fun createDataContext(previewElement: T): DataContext

  fun toLogString(previewElement: T): String

  /**
   * Creates an in-memory XML file that model can pass to Layoutlib so that the [PreviewElement] can be rendered.
   * @param content text content of an XML file, usually some custom view adapter for the [PreviewElement].
   * @param backedFile the actual file where the [PreviewElement] is located.
   * @param id an identifier used for debugging purposes.
   */
  fun createLightVirtualFile(content: String, backedFile: VirtualFile, id: Long): LightVirtualFile
}

open class DelegatingPreviewElementModelAdapter<T : PreviewElement, M>(private val delegate: PreviewElementModelAdapter<T, M>):
  PreviewElementModelAdapter<T, M> by delegate