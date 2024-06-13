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
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription.isManifestFile
import org.jetbrains.android.facet.AndroidFacet

private fun isSupportedSymbol(charTyped: Char) = charTyped == '@' || charTyped == '?'

/**
 * Handler for typed characters in Android XML files.
 *
 * This handler overrides the default behavior for "@" and "?" characters, triggering
 * auto-completion popups for resources and IDs.
 */
class AndroidXmlTypedHandler : TypedHandlerDelegate() {
  /** Overrides the default `charTyped` function to show auto-completion popups. */
  override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!isSupportedSymbol(charTyped) || file !is XmlFile)
      return super.charTyped(charTyped, project, editor, file)

    // Possibly show auto-completion popup for member lookup.
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor) { psiFile ->
      val facet = AndroidFacet.getInstance(file) ?: return@autoPopupMemberLookup false
      // Get element before the caret.
      val lastElement =
        psiFile.findElementAt(editor.caretModel.offset - 1) ?: return@autoPopupMemberLookup false

      // Check conditions for showing the popup:
      // - First character of last element is "@" or "?".
      // - File is an XmlFile.
      // - File is a manifest file or a resource file.
      // - Last element is inside an attribute value or text.
      // - If last element is in text, then file must be a values file.
      // - If last character is "?", then file must be a layout file.
      // All conditions must be true for the popup to appear.
      isSupportedSymbol(lastElement.text[0]) &&
        psiFile is XmlFile &&
        (isManifestFile(psiFile) || isResourceFile(psiFile.virtualFile, facet)) &&
        (lastElement.parent is XmlAttributeValue || lastElement.parent is XmlText) &&
        (lastElement.parent !is XmlText || getFolderType(psiFile) == ResourceFolderType.VALUES) &&
        (lastElement.text[0] != '?' || getFolderType(psiFile) == ResourceFolderType.LAYOUT)
    }

    return Result.STOP
  }
}
