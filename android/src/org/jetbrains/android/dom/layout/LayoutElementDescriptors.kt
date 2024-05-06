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
package org.jetbrains.android.dom.layout

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.tools.idea.AndroidPsiUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.meta.PsiPresentableMetaData
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlDocument
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ArrayUtil
import com.intellij.util.xml.DomManager
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.impl.dom.AbstractDomChildrenDescriptor
import com.intellij.xml.impl.dom.DomElementXmlDescriptor
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl
import javax.swing.Icon
import org.jetbrains.android.dom.AndroidAnyAttributeDescriptor
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider.getIconForViewTag
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.TagFromClassDescriptor
import org.jetbrains.annotations.NonNls

/**
 * XmlElementDescriptor for all View tags (tags that are inheritors of [SdkConstants.CLASS_VIEW])
 * and <view></view> tag.
 *
 * [LayoutViewElementDescriptor] is returned by [AndroidDomElementDescriptorProvider].
 */
class LayoutViewElementDescriptor(override val clazz: PsiClass?, delegate: XmlElementDescriptor) :
  TagFromClassDescriptor, LayoutElementDescriptor(delegate) {

  constructor(
    viewClass: PsiClass?,
    element: LayoutViewElement
  ) : this(viewClass, DomElementXmlDescriptor(element))

  override fun getIcon(): Icon? {
    if (clazz?.name == defaultName) {
      return getIconForViewTag(clazz!!.name!!)
    }
    return null
  }

  override fun getDeclaration() = clazz

  override val isContainer by lazy {
    InheritanceUtil.isInheritor(clazz, SdkConstants.CLASS_VIEWGROUP)
  }
}

/**
 * Base descriptor for all inheritors of [LayoutElement].
 *
 * [LayoutElementDescriptor] is returned by [AndroidDomElementDescriptorProvider].
 */
open class LayoutElementDescriptor(private val delegate: XmlElementDescriptor) :
  PsiPresentableMetaData, XmlElementDescriptor by delegate {

  protected open val isContainer: Boolean = true

  override fun getNSDescriptor() = AndroidLayoutNSDescriptor

  override fun getElementsDescriptors(context: XmlTag?): Array<LayoutElementDescriptor> {
    if (context == null) return emptyArray()
    return delegate
      .getElementsDescriptors(context)
      .map { LayoutElementDescriptor(it) }
      .toTypedArray()
  }

  override fun getElementDescriptor(childTag: XmlTag?, contextTag: XmlTag?): XmlElementDescriptor? {
    val fromDelegate = delegate.getElementDescriptor(childTag, contextTag) ?: return null
    return LayoutElementDescriptor(fromDelegate)
  }

  override fun getAttributesDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> {
    val descriptors = delegate.getAttributesDescriptors(context) ?: return emptyArray()

    // The rest of the function below ensures that layout_width attribute descriptor comes before
    // layout_height descriptor. Order of these is significant for automatic attribute insertion on
    // tag autocompletion (commenting out ArrayUtil.swap below breaks a bunch of unit tests).

    // Discussion of that on JetBrains issue tracker:
    // https://youtrack.jetbrains.com/issue/IDEA-89857
    var layoutWidthIndex = -1
    var layoutHeightIndex = -1
    for (i in descriptors.indices) {
      val name = descriptors[i].name
      if (ATTR_LAYOUT_WIDTH == name) {
        layoutWidthIndex = i
      } else if (ATTR_LAYOUT_HEIGHT == name) {
        layoutHeightIndex = i
      }
    }
    if (layoutWidthIndex >= 0 && layoutHeightIndex >= 0 && layoutWidthIndex > layoutHeightIndex) {
      val result = descriptors.clone()
      ArrayUtil.swap(result, layoutWidthIndex, layoutHeightIndex)
      return result
    }
    return descriptors
  }

  override fun getAttributeDescriptor(
    @NonNls attributeName: String?,
    context: XmlTag?
  ): XmlAttributeDescriptor? {
    return delegate.getAttributeDescriptor(attributeName, context)
      ?: AndroidAnyAttributeDescriptor(attributeName!!)
  }

  override fun getAttributeDescriptor(attribute: XmlAttribute): XmlAttributeDescriptor? {
    return delegate.getAttributeDescriptor(attribute)
      ?: AndroidAnyAttributeDescriptor(attribute.name)
  }

  override fun getIcon() = getIconForViewTag(name)

  override fun getTypeName() = null
}

object AndroidLayoutNSDescriptor : XmlNSDescriptorImpl() {
  private val staticLayoutFileRootDescriptors =
    arrayOf(
      ViewTagDomFileDescription(),
      FragmentLayoutDomFileDescription(),
      MergeDomFileDescription(),
      DataBindingDomFileDescription()
    )

  override fun getRootElementsDescriptors(doc: XmlDocument?): Array<LayoutElementDescriptor> {
    if (doc == null) return emptyArray()
    val facet = AndroidFacet.getInstance(doc) ?: return emptyArray()
    val manager = DomManager.getDomManager(doc.project)

    return CachedValuesManager.getManager(doc.project).getCachedValue(facet) {
      val static =
        staticLayoutFileRootDescriptors
          .map {
            val delegate =
              object : AbstractDomChildrenDescriptor(manager) {
                override fun getDefaultName() = it.rootTagName

                override fun getDeclaration() = null
              }
            LayoutElementDescriptor(delegate)
          }
          .toTypedArray()
      CachedValueProvider.Result.create(
        static,
        AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(manager.project)
      )
    }
  }
}
