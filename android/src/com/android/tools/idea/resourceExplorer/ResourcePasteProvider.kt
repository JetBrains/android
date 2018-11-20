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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.resourceExplorer.viewmodel.RESOURCE_URL_FLAVOR
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.util.dependsOnAppCompat
import com.intellij.ide.PasteProvider
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
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
    val psiElement = runReadAction { psiFile.findElementAt(caret.offset) }

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
    val resourceReference = getResourceUrl(dataContext)?.toString() ?: return

    if (psiElement !is XmlElement) {
      pasteAtCaret(caret, resourceReference)
      return
    }

    val xmlAttributeValue = psiElement.parentOfType<XmlAttributeValue>()
    if (processForValue(xmlAttributeValue, caret, resourceReference)) {
      return
    }

    val xmlAttribute = psiElement.parentOfType<XmlAttribute>()
    if (processForAttribute(xmlAttribute, caret, resourceReference)) {
      return
    }

    val xmlTag = psiElement.parentOfType<XmlTag>()
    if (processForTag(xmlTag, caret, resourceReference)) {
      return
    }

    pasteAtCaret(caret, resourceReference)
  }

      }
    }
  }

  private fun processForTag(xmlTag: XmlTag?,
                            caret: Caret,
                            resourceReference: String): Boolean {
    return when (xmlTag?.name) {
      SdkConstants.IMAGE_VIEW -> performForImageView(xmlTag, resourceReference)
      else -> false
    }
  }

  private fun processForAttribute(xmlAttribute: XmlAttribute?,
                                  caret: Caret,
                                  resourceReference: String): Boolean {
    if (xmlAttribute == null) return false
    runWriteAction {
      xmlAttribute.value = resourceReference
    }
    xmlAttribute.valueElement?.valueTextRange?.startOffset?.let {
      caret.selectStringFromOffset(resourceReference, it)
    }
    return true
  }

  private fun processForValue(xmlAttributeValue: XmlAttributeValue?,
                              caret: Caret,
                              resourceReference: String): Boolean {
    if (xmlAttributeValue == null) return false
    processForAttribute(xmlAttributeValue.parent as? XmlAttribute, caret, resourceReference)
    return true
  }

  private fun pasteAtCaret(caret: Caret, resourceReference: String) {
    runWriteAction {
      caret.editor.document.insertString(caret.offset, resourceReference)
    }
    caret.selectStringFromOffset(resourceReference, caret.offset)
  }

  private fun replaceAtCaret(caret: Caret, psiElement: PsiElement, resourceReference: String) {
    runWriteAction {
      caret.editor.document.replaceString(psiElement.textRange.startOffset, psiElement.textRange.endOffset, resourceReference)
    }
    caret.selectStringFromOffset(resourceReference, psiElement.textRange.startOffset)
  }

  private fun Caret.selectStringFromOffset(resourceReference: String, offset: Int) {
    setSelection(offset, offset + resourceReference.length)
    moveToOffset(offset + resourceReference.length)
  }

  /**
   * Set the src attribute for an ImageView [xmlTag] with the provided [resourceReference].
   * This method will use the app compat attributes if the module is using AppCompat
   */
  private fun performForImageView(xmlTag: XmlTag, resourceReference: String): Boolean {
    val dependsOnAppCompat = dependsOnAppCompat(xmlTag)
    runWriteAction {
      setSrcAttribute(dependsOnAppCompat, xmlTag, resourceReference)
      TemplateUtils.reformatAndRearrange(xmlTag.project, xmlTag)
    }
    return true
  }

  private fun setSrcAttribute(dependsOnAppCompat: Boolean, xmlTag: XmlTag, resourceReference: String) {
    if (dependsOnAppCompat) {
      xmlTag.setAttribute(SdkConstants.ATTR_SRC_COMPAT, SdkConstants.AUTO_URI, resourceReference)
      xmlTag.setAttribute(SdkConstants.ATTR_SRC, SdkConstants.ANDROID_URI, null)
    }
    else {
      xmlTag.setAttribute(SdkConstants.ATTR_SRC_COMPAT, SdkConstants.AUTO_URI, null)
      xmlTag.setAttribute(SdkConstants.ATTR_SRC, SdkConstants.ANDROID_URI, resourceReference)
    }
  }

  private fun dependsOnAppCompat(xmlTag: XmlTag) =
    runReadAction { ModuleUtilCore.findModuleForPsiElement(xmlTag)?.dependsOnAppCompat() } == true

  private fun getResourceUrl(dataContext: DataContext): ResourceUrl? =
    PasteAction.TRANSFERABLE_PROVIDER.getData(dataContext)
      ?.produce()
      ?.getTransferData(RESOURCE_URL_FLAVOR) as ResourceUrl?

  override fun isPastePossible(dataContext: DataContext): Boolean {
    if (!StudioFlags.RESOURCE_MANAGER_ENABLED.get()) return false
    return PasteAction.TRANSFERABLE_PROVIDER.getData(dataContext)
             ?.produce()
             ?.isDataFlavorSupported(RESOURCE_URL_FLAVOR) ?: false
  }

  override fun isPasteEnabled(dataContext: DataContext) = isPastePossible(dataContext)
}