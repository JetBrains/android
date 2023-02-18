/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.psi.xml

import com.android.SdkConstants
import com.android.resources.ResourceUrl
import com.intellij.psi.PsiElement
import com.intellij.psi.XmlRecursiveElementWalkingVisitor
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.resolve.source.PsiSourceElement
import org.jetbrains.kotlin.resolve.source.getPsi

internal fun XmlFile.findXmlTagById(attrId: String): XmlTag? {
  var resultTag: XmlTag? = null
  val visitor = object : XmlRecursiveElementWalkingVisitor() {
    override fun visitXmlTag(tag: XmlTag) {
      super.visitXmlTag(tag)
      // unique resource id in the same xml file
      if (tag.isTagIdEqualTo(attrId)) {
        resultTag = tag
        stopWalking()
      }
    }
  }
  this.accept(visitor)
  return resultTag
}

internal fun XmlTag.isTagIdEqualTo(id: String): Boolean {
  val tagId = this.getAttributeValue(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI) ?: return false
  return ResourceUrl.parse(tagId)?.name == id
}

internal fun XmlTag.findChildTagElementByNameAttr(tagName: String, nameAttr: String): XmlTag? {
  return this.subTags.firstOrNull {
    it != null && it.localName == tagName && it.hasMatchedNameAttr(nameAttr)
  }
}

internal fun XmlTag.findChildTagElementById(tagName: String, idAttr: String): XmlTag? {
  return this.subTags.firstOrNull {
    it != null && it.localName == tagName && it.hasMatchedIdAttr(idAttr)
  }
}

internal fun XmlTag.findFirstMatchingElementByTraversingUp(tagName: String, idAttr: String): XmlTag? {
  var currentTag: XmlTag? = this
  while (currentTag != null) {
    val found = currentTag.findChildTagElementById(tagName, idAttr)

    if (found != null) return found
    currentTag = currentTag.parentTag
  }
  return null
}

internal fun XmlTag.hasMatchedNameAttr(name: String): Boolean {
  return this.attributes.firstOrNull {
    it != null && it.localName == SdkConstants.ATTR_NAME && it.value == name
  } != null
}

internal fun XmlTag.hasMatchedIdAttr(id: String): Boolean {
  return this.attributes.firstOrNull {
    it != null && it.localName == SdkConstants.ATTR_ID && it.value?.substringAfter("@+id/") == id
  } != null
}

class XmlSourceElement(override val psi: PsiElement) : PsiSourceElement

internal fun SourceElement.withFunctionIcon(name: String, containingClassName: String): SourceElement {
  return (this.getPsi() as? SafeArgsXmlTag)?.let {
    XmlSourceElement(SafeArgsXmlTag(it.getOriginal(), IconManager.getInstance().getPlatformIcon(PlatformIcons.Function), name, containingClassName))
  } ?: this
}