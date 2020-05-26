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
package org.jetbrains.android.dom

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.EvaluatedXmlName
import com.intellij.util.xml.EvaluatedXmlNameImpl
import com.intellij.util.xml.reflect.CustomDomChildrenDescription
import com.intellij.util.xml.reflect.DomExtender
import com.intellij.util.xml.reflect.DomExtensionsRegistrar
import org.jetbrains.android.dom.layout.Fragment
import org.jetbrains.android.dom.layout.Layout
import org.jetbrains.android.dom.layout.LayoutElement
import org.jetbrains.android.dom.layout.LayoutViewElement
import org.jetbrains.android.dom.layout.Merge

/**
 * [DomExtender] that registers a [CustomDomChildrenDescription] on tags corresponding to layouts, with completion variants for all
 * known view classes.
 *
 * Note that [CustomDomChildrenDescription.TagNameDescriptor.getCompletionVariants] runs on a background thread, as opposed to
 * [DomExtender.registerExtensions], so this way we avoid blocking the UI for completion.
 */
class AndroidLayoutDomExtender : DomExtender<LayoutElement>() {

  override fun registerExtensions(domElement: LayoutElement, registrar: DomExtensionsRegistrar) {
    if (StudioFlags.LAYOUT_XML_MODE.get() == StudioFlags.LayoutXmlMode.CUSTOM_CHILDREN) {
      registerCustomChildren(domElement, registrar)
    }
  }

  private fun registerCustomChildren(layoutElement: LayoutElement, registrar: DomExtensionsRegistrar) {
    val xmlTag = layoutElement.xmlElement as? XmlTag ?: return
    when (layoutElement) {
      is LayoutViewElement -> {
        val descriptor = xmlTag.descriptor as? AndroidXmlLayoutViewDescriptor ?: return
        if (descriptor.isViewGroup) {
          registrar.registerCustomChildrenExtension(LayoutViewElement::class.java, ChildViewNameDescriptor)
        }
      }
      is Fragment, is Merge, is Layout -> registrar.registerCustomChildrenExtension(LayoutViewElement::class.java, ChildViewNameDescriptor)
      else -> return
    }
  }

  private object ChildViewNameDescriptor : CustomDomChildrenDescription.TagNameDescriptor() {
    override fun getCompletionVariants(parent: DomElement): Set<EvaluatedXmlName> {
      if (ApplicationManager.getApplication().isDispatchThread) {
        error("completion variants on the UI thread")
      }

      val result = mutableSetOf<EvaluatedXmlName>()
      SubtagsProcessingUtil.processSubTags(
        parent.androidFacet ?: return emptySet(),
        parent as? AndroidDomElement ?: return emptySet(),
        false
      ) { name, _ -> result.add(EvaluatedXmlNameImpl.createEvaluatedXmlName(name, null, true)) }
      return result
    }
  }
}