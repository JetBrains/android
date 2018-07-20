/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.android.SdkConstants
import com.android.resources.ResourceUrl
import com.android.tools.idea.resourceExplorer.view.RESOURCE_URL_FLAVOR
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.util.dependsOnAppCompat
import com.intellij.ide.PasteProvider
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag

/**
 * Extension [PasteProvider] to handle paste of [java.awt.datatransfer.Transferable] providing [RESOURCE_URL_FLAVOR] in
 * intelliJ editors.
 */
class ResourcePasteProvider : PasteProvider {

  override fun performPaste(dataContext: DataContext) {
    val caret = CommonDataKeys.CARET.getData(dataContext)!!
    val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
    val psiElement = psiFile.findElementAt(caret.offset)

    when (psiFile.fileType) {
      XmlFileType.INSTANCE -> performForXml(psiElement, dataContext, caret)
    }
  }

  /**
   * Perform the paste in an XML file context.
   * The paste operation will be different depending on the psiElement under the caret.
   *
   * For example, if the caret is within an ImageView tag, the `src` attribute will be populated with the
   * pasted [ResourceUrl].
   */
  private fun performForXml(psiElement: PsiElement?,
                            dataContext: DataContext,
                            caret: Caret) {
    if (psiElement is XmlElement) {
      val resourceReference = getResourceUrl(dataContext)?.toString() ?: return
      val xmlTag = findNearestXmlTag(psiElement) ?: return
      when (xmlTag.name) {
        SdkConstants.IMAGE_VIEW -> performForImageView(xmlTag, resourceReference)
        else -> pasteAtCaret(caret, resourceReference)
      }
    }
  }

  private fun pasteAtCaret(caret: Caret, resourceReference: String) {
    runWriteAction {
      caret.editor.document.insertString(caret.offset, resourceReference)
    }
    caret.setSelection(caret.offset, caret.offset + resourceReference.length)
    caret.moveToOffset(caret.offset + resourceReference.length)
  }

  /**
   * If the psiElement is descendant of an [XmlTag] this method return the [XmlTag] or
   * returns null otherwise.
   */
  private fun findNearestXmlTag(psiElement: PsiElement?): XmlTag? {
    var currentElement = psiElement
    while (currentElement != null && currentElement !is XmlTag) {
      currentElement = currentElement.parent
    }
    return currentElement as? XmlTag
  }

  /**
   * Set the src attribute for an ImageView [xmlTag] with the provided [resourceReference].
   * This method will use the app compat attributes if the module is using AppCompat
   */
  private fun performForImageView(xmlTag: XmlTag, resourceReference: String) {
    val dependsOnAppCompat = ModuleUtilCore.findModuleForPsiElement(xmlTag)?.dependsOnAppCompat() == true
    runWriteAction {
      if (dependsOnAppCompat) {
        xmlTag.setAttribute(SdkConstants.ATTR_SRC_COMPAT, SdkConstants.AUTO_URI, resourceReference)
        xmlTag.setAttribute(SdkConstants.ATTR_SRC, SdkConstants.ANDROID_URI, null)
      }
      else {
        xmlTag.setAttribute(SdkConstants.ATTR_SRC_COMPAT, SdkConstants.AUTO_URI, null)
        xmlTag.setAttribute(SdkConstants.ATTR_SRC, SdkConstants.ANDROID_URI, resourceReference)
      }

      TemplateUtils.reformatAndRearrange(xmlTag.project, xmlTag)
    }
  }

  private fun getResourceUrl(dataContext: DataContext): ResourceUrl? =
    PasteAction.TRANSFERABLE_PROVIDER.getData(dataContext)
      ?.produce()
      ?.getTransferData(RESOURCE_URL_FLAVOR) as ResourceUrl?

  override fun isPastePossible(dataContext: DataContext) = CommonDataKeys.PSI_FILE.getData(dataContext)?.fileType == XmlFileType.INSTANCE

  override fun isPasteEnabled(dataContext: DataContext) = true

}