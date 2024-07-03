/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutAttributesModel.getSubTag
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutPropertyProvider.mapToCustomType
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.pom.Navigatable
import com.intellij.psi.xml.XmlTag

object Navigation {

  fun browseToValue(property: NlPropertyItem) {
    findValueNavigation(property)?.navigate(true)
  }

  private fun findValueNavigation(property: NlPropertyItem): Navigatable? {
    val selection = MotionLayoutAttributesModel.getMotionSelection(property) ?: return null
    val motionTag =
      selection.motionSceneTag ?: return findDefaultValueNavigation(property, selection)
    return when (val subTagName = getSubTag(property)) {
      null -> findValueNavigationOfMotionTag(property, motionTag)
      CUSTOM_ATTRIBUTE -> findValueFromCustomAttribute(property, selection, motionTag)
      else -> findValueFromSubTag(property, selection, motionTag, subTagName)
    }
  }

  private fun findValueFromSubTag(
    property: NlPropertyItem,
    selection: MotionSelection,
    motionTag: MotionSceneTag,
    subTagName: String,
  ): Navigatable? {
    val subTag =
      getSubTag(motionTag, subTagName) ?: return findDefaultValueNavigation(property, selection)
    return findValueNavigationOfMotionTag(property, subTag)
  }

  private fun findValueFromCustomAttribute(
    property: NlPropertyItem,
    selection: MotionSelection,
    constraint: MotionSceneTag,
  ): Navigatable? {
    val customTag =
      MotionLayoutAttributesModel.findCustomTag(constraint, property.name) as? MotionSceneTag
        ?: return findDefaultValueNavigation(property, selection)
    return customTag.xmlTag?.let {
      findValueNavigationOfXmlTag(it, mapToCustomType(property.type), AUTO_URI)
    }
  }

  private fun findDefaultValueNavigation(
    property: NlPropertyItem,
    selection: MotionSelection,
  ): Navigatable? {
    if (selection.type != MotionEditorSelector.Type.CONSTRAINT) {
      return null
    }
    val attributes = selection.motionAttributes ?: return null
    val defined = attributes.attrMap[property.name] ?: return null
    if (defined.isCustomAttribute != (getSubTag(property) === CUSTOM_ATTRIBUTE)) {
      return null
    }
    val source = defined.sourceId
    if (source == null) {
      return selection.component?.tag?.let {
        findValueNavigationOfXmlTag(it, property.name, property.namespace)
      }
    } else {
      val sets =
        attributes.constraintSet?.parent?.getChildTags(MotionSceneAttrs.Tags.CONSTRAINTSET)
          ?: return null
      val sourceSet =
        sets.firstOrNull { source == Utils.stripID(it.getAttributeValue(ATTR_ID)) } ?: return null
      val constraints = sourceSet.getChildTags(MotionSceneAttrs.Tags.CONSTRAINT) ?: return null
      val constraint =
        constraints.firstOrNull { attributes.id == Utils.stripID(it.getAttributeValue(ATTR_ID)) }
          as? MotionSceneTag ?: return null
      if (defined.isCustomAttribute) {
        return findValueFromCustomAttribute(property, selection, constraint)
      } else {
        findValueNavigationOfMotionTag(property, constraint)?.let {
          return it
        }
        constraint.childTags.forEach { child ->
          findValueNavigationOfMotionTag(property, child as MotionSceneTag)?.let {
            return it
          }
        }
      }
      return null
    }
  }

  private fun findValueNavigationOfMotionTag(
    property: NlPropertyItem,
    tag: MotionSceneTag,
  ): Navigatable? {
    return tag.xmlTag?.let { findValueNavigationOfXmlTag(it, property.name, property.namespace) }
  }

  private fun findValueNavigationOfXmlTag(
    tag: XmlTag,
    attributeName: String,
    namespace: String,
  ): Navigatable? {
    val attribute = tag.getAttribute(attributeName, namespace) ?: return null
    val attributeValue = attribute.valueElement ?: return null
    val file = tag.containingFile ?: return null
    val ref = file.findReferenceAt(attributeValue.textOffset) ?: return null
    var element = ref.resolve()
    if (element == null) {
      element = ref.element
    }
    if (element is ResourceReferencePsiElement) {
      return ResourceRepositoryToPsiResolver.getGotoDeclarationTargets(
          element.resourceReference,
          tag,
        )
        .filterIsInstance<Navigatable>()
        .firstOrNull()
    }
    return element as? Navigatable
  }
}
