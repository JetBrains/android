/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.SdkConstants.ATTR_NAME
import com.android.tools.idea.wear.dwf.WFFConstants.REFERENCE_PREFIX
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_REFERENCE
import com.android.tools.idea.wear.dwf.dom.raw.createDataSourceLookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * A [PsiReference] to a `<Reference>` tag within the given [watchFaceFile].
 *
 * Reference tags are identified by their `name` attribute.
 *
 * @see <a
 *   href="https://developer.android.com/reference/wear-os/wff/common/reference/reference">Reference</a>
 */
class ReferenceTagReference(element: PsiElement, private val watchFaceFile: XmlFile) :
  PsiReferenceBase<PsiElement>(element) {
  override fun resolve(): PsiElement? {
    if (!element.text.startsWith("[$REFERENCE_PREFIX")) return null
    if (!element.text.endsWith("]")) return null
    val referenceName = element.text.removeSurrounding(prefix = "[$REFERENCE_PREFIX", suffix = "]")
    if (referenceName.isBlank()) return null
    return extractReferenceTagsByName()[referenceName]?.element
  }

  override fun getVariants(): Array<out Any?> {
    val referenceNames = extractReferenceTagsByName().keys
    return referenceNames
      .map { createDataSourceLookupElement("$REFERENCE_PREFIX$it") }
      .toTypedArray()
  }

  private fun extractReferenceTagsByName(): Map<String, SmartPsiElementPointer<XmlTag>> {
    val watchFaceFilePointer = SmartPointerManager.createPointer(watchFaceFile)
    return CachedValuesManager.getManager(element.project).getCachedValue(watchFaceFile) {
      val referenceTagsByName = mutableMapOf<String, SmartPsiElementPointer<XmlTag>>()
      watchFaceFilePointer.element?.accept(
        object : XmlRecursiveElementVisitor() {
          override fun visitXmlTag(tag: XmlTag) {
            super.visitXmlTag(tag)
            if (tag.name != TAG_REFERENCE) return
            val referenceName = tag.getAttribute(ATTR_NAME)?.value ?: return
            if (referenceName.isNotEmpty()) {
              referenceTagsByName[referenceName] = SmartPointerManager.createPointer(tag)
            }
          }
        }
      )
      CachedValueProvider.Result.create(referenceTagsByName, watchFaceFilePointer.element)
    }
  }
}
