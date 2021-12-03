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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.getResourceVariations
import com.android.tools.idea.res.isInResourceSubdirectory
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.UIController
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil

/**
 * Custom [TrafficLightRenderer] to be used by resource files.
 * It aggregates all the errors, warnings... found in the [file] and its other qualifier variants.
 */
class ResourceFileTrafficLightRender(val file: PsiFile, val editor: Editor) : TrafficLightRenderer(file.project, editor.document) {
  private val errorCountArray = IntArray(severityRegistrar.allSeverities.size)
  private val variantModels = mutableMapOf<VirtualFile, MarkupModelEx>()

  init {
    val variants = getResourceVariations(file.virtualFile, true)
    variants.forEach { updateTrafficLightWithFile(it) }
  }

  override fun refresh(editorMarkupModel: EditorMarkupModelImpl?) {
    super.refresh(editorMarkupModel)
    if (editorMarkupModel == null) {
      return
    }
    val variantFiles = getResourceVariations(file.virtualFile, false)
    variantModels.keys.filterNot { variantFiles.contains(it) }.forEach { variantModels.remove(it)?.removeAllHighlighters() }
    variantFiles.filterNot { variantModels.containsKey(it) }.forEach { updateTrafficLightWithFile(it) }
  }

  /**
   * Adds listeners to [variantFile] so that, when analysis is performed on it,
   * the results are used to update the error count for this [file].
   */
  private fun updateTrafficLightWithFile(variantFile: VirtualFile) {
    FileDocumentManager.getInstance().getDocument(variantFile)?.let {
      val model = DocumentMarkupModel.forDocument(it, project, true) as MarkupModelEx
      model.addMarkupModelListener(this, object : MarkupModelListener {
        override fun afterAdded(highlighter: RangeHighlighterEx) {
          updateErrorCount(highlighter, 1)
        }

        override fun beforeRemoved(highlighter: RangeHighlighterEx) {
          updateErrorCount(highlighter, -1)
        }
      })
      variantModels[variantFile] = model
      UIUtil.invokeLaterIfNeeded {
        model.allHighlighters.forEach { highlighter ->
          updateErrorCount(highlighter, 1)
        }
      }
    }
  }

  private fun updateErrorCount(highlighter: RangeHighlighter, delta: Int) {
    val severities = severityRegistrar.allSeverities
    val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return
    val infoSeverity = info.severity
    if (infoSeverity.myVal <= HighlightSeverity.INFORMATION.myVal) return
    val index = severities.indexOf(infoSeverity)
    if (index > -1) {
      errorCountArray[index] += delta
    }
  }

  override fun getErrorCount(): IntArray {
    return errorCountArray
  }

  override fun createUIController(): UIController {
    return DefaultUIController()
  }
}

class ResourceFileTrafficLightRendererContributor : TrafficLightRendererContributor {
  override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
    if (!StudioFlags.NELE_INCLUDE_QUALIFIERS_FOR_TRAFFIC_LIGHTS.get()) {
      return null
    }
    // Use this customized renderer only for resource files, returning null means that the default renderer will be used.
    return file?.let {
      if (isInResourceSubdirectory(it)) {
        ResourceFileTrafficLightRender(it, editor)
      }
      else {
        null
      }
    }
  }
}
