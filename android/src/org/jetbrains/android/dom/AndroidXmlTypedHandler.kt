/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.dom

import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.isResourceFile
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlText
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription
import org.jetbrains.android.facet.AndroidFacet

class AndroidXmlTypedHandler : TypedHandlerDelegate() {
  override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    return if (isSupportedSymbol(charTyped) && file is XmlFile) {
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor) { psiFile ->
        val facet = AndroidFacet.getInstance(file) ?: return@autoPopupMemberLookup false
        val caretOffset = editor.caretModel.offset
        val lastElement =
          psiFile.findElementAt(caretOffset - 1) ?: return@autoPopupMemberLookup false

        if (!isSupportedSymbol(lastElement.text[0])) return@autoPopupMemberLookup false
        if (psiFile !is XmlFile) return@autoPopupMemberLookup false
        if (
          !ManifestDomFileDescription.isManifestFile(psiFile) &&
            !isResourceFile(psiFile.virtualFile, facet)
        )
          return@autoPopupMemberLookup false
        if (lastElement.parent !is XmlAttributeValue && lastElement.parent !is XmlText)
          return@autoPopupMemberLookup false
        if (lastElement.parent is XmlText && getFolderType(psiFile) != ResourceFolderType.VALUES)
          return@autoPopupMemberLookup false
        if (lastElement.text[0] == '?' && getFolderType(psiFile) != ResourceFolderType.LAYOUT)
          return@autoPopupMemberLookup false

        true
      }

      Result.STOP
    } else {
      super.charTyped(charTyped, project, editor, file)
    }
  }

  companion object {
    private fun isSupportedSymbol(charTyped: Char): Boolean {
      return when (charTyped) {
        '@' -> true
        '?' -> true
        else -> false
      }
    }
  }
}
