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

import com.android.tools.idea.common.model.NlModel
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

private const val PREFIX = "GlancePreview"
internal val GLANCE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<MethodPreviewElement>("$PREFIX.PreviewElement")

// TODO(b/239802877): Split this into an instance of PreviewElementModelAdapter.

/**
 * [NlModel] associated preview data
 * @param project the [Project] used by the current view.
 *
 * @param previewElement the [GlancePreviewElement] associated to this model
 */
internal class GlancePreviewNlModelDataContext<T : MethodPreviewElement>(
  private val project: Project,
  private val previewElement: T
) : DataContext {
  override fun getData(dataId: String): Any? =
    when (dataId) {
      GLANCE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
}

/** A method to get the [MethodPreviewElement] from the [NlModel] */
internal fun <T : MethodPreviewElement> NlModel.toPreviewElement(): T? =
  if (!Disposer.isDisposed(this)) {
    dataContext.getData(GLANCE_PREVIEW_ELEMENT_INSTANCE) as? T?
  } else null
