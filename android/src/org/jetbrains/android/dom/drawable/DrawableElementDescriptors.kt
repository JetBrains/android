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
package org.jetbrains.android.dom.drawable

import com.android.tools.idea.AndroidPsiUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlDocument
import com.intellij.util.xml.DomManager
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.XmlNSDescriptor
import com.intellij.xml.impl.dom.AbstractDomChildrenDescriptor
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl
import org.jetbrains.android.dom.AndroidXmlTagDescriptor
import org.jetbrains.android.dom.xml.XmlResourceNSDescriptor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.TagFromClassDescriptor

object DrawableResourceNSDescriptor : XmlNSDescriptorImpl() {
  override fun getRootElementsDescriptors(doc: XmlDocument?): Array<XmlElementDescriptor> {
    if (doc == null) return emptyArray()
    val facet = AndroidFacet.getInstance(doc) ?: return emptyArray()
    val manager = DomManager.getDomManager(doc.project)

    return CachedValuesManager.getManager(doc.project).getCachedValue(facet) {
      val static =
        AndroidDrawableDomUtil.getPossibleRoots(facet)
          .map {
            object : AbstractDomChildrenDescriptor(manager) {
              override fun getDefaultName() = it

              override fun getDeclaration() = null
            }
          }
          .toTypedArray<XmlElementDescriptor>()
      CachedValueProvider.Result.create(
        static,
        AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(manager.project),
      )
    }
  }
}

// TODO: don't extend AndroidXmlTagDescriptor. Currently it extends AndroidXmlTagDescriptor for
// support inspection behavior.
class CustomDrawableElementDescriptor(
  override val clazz: PsiClass?,
  delegate: XmlElementDescriptor,
) : TagFromClassDescriptor, AndroidXmlTagDescriptor(delegate) {
  override val isContainer = false

  override fun getDeclaration() = clazz

  override fun getNSDescriptor(): XmlNSDescriptor = XmlResourceNSDescriptor
}
