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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property.NlIdPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.intellij.psi.xml.XmlAttributeValue
import com.android.tools.dom.attrs.AttributeDefinition

/**
 * Property item for an ID.
 */
class MotionIdPropertyItem(
  model: NlPropertiesModel,
  definition: AttributeDefinition?,
  componentName: String,
  components: List<NlComponent>,
  optionalValue1: Any? = null,
  optionalValue2: Any? = null
): NlIdPropertyItem(model, definition, componentName, components, optionalValue1, optionalValue2) {

  /**
   * Override the default get method and delegate to the model.
   */
  override val rawValue: String?
    get() = model.getPropertyValue(this)

  override fun renameRefactoring(value: XmlAttributeValue?, oldId: String, newId: String, newValue: String?): Boolean {
    if (!super.renameRefactoring(value, oldId, newId, newValue)) {
      return false
    }
    updateMTagAttribute(newValue)
    return true
  }

  /**
   * Update the attribute values in the selected motion scene tag.
   *
   * This is a special case where the rename processor is updating the XmlTag directly.
   * The MTag layer is not used for writing which causes the cached values to be out of sync,
   * which later can cause selection problems.
   * see https://issuetracker.google.com/issues/147511820
   *
   * Update the in memory MTag attribute here to reflect the value just written to the PSI.
   * A subsequent MTag rebuild will then be able to keep the current selection.
   */
  private fun updateMTagAttribute(newValue: String?) {
    val selection = MotionLayoutAttributesModel.getMotionSelection(this)
    val tag = selection?.motionSceneTag ?: return
    val writer = tag.tagWriter
    writer.setAttribute(namespace, name, newValue)
    writer.attrList.forEach { (key, value) -> tag.attrList[key] = value }
    // Do NOT commit the writer, as the PSI was already updated
  }
}
