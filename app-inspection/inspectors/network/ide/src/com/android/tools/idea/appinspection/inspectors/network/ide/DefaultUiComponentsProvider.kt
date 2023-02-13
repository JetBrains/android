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
package com.android.tools.idea.appinspection.inspectors.network.ide

import com.android.tools.adtui.stdui.ContentType
import com.android.tools.idea.appinspection.inspectors.network.view.UiComponentsProvider
import com.android.tools.inspectors.common.api.ide.stacktrace.IntelliJStackTraceGroup
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer
import com.android.tools.inspectors.common.ui.dataviewer.IntellijDataViewer
import com.android.tools.inspectors.common.ui.dataviewer.IntellijImageDataViewer
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup
import com.intellij.openapi.project.Project

class DefaultUiComponentsProvider(private val project: Project) : UiComponentsProvider {
  override fun createDataViewer(bytes: ByteArray, contentType: ContentType, styleHint: DataViewer.Style, formatted: Boolean): DataViewer {
    return when {
      contentType.isSupportedImageType -> {
        IntellijImageDataViewer.createImageViewer(bytes) ?: IntellijDataViewer.createInvalidViewer()
      }
      styleHint == DataViewer.Style.RAW -> {
        if (contentType.isSupportedTextType) IntellijDataViewer.createRawTextViewer(bytes) else IntellijDataViewer.createInvalidViewer()
      }
      styleHint == DataViewer.Style.PRETTY -> {
        IntellijDataViewer.createPrettyViewerIfPossible(project, bytes, contentType.fileType, formatted)
      }
      else -> {
        // This shouldn't ever happen.
        throw RuntimeException("DataViewer style is invalid.")
      }
    }
  }

  override fun createStackGroup(): StackTraceGroup {
    return IntelliJStackTraceGroup(project)
  }
}