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
package com.android.tools.idea.common.property2.impl.model

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.property2.api.InspectorLineModel
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.text.Matcher
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon
import javax.swing.UIManager

// Prefix to the keys used to store/restore the state of expandable groups in the inspector
@VisibleForTesting
const val KEY_PREFIX = "inspector.open."

/**
 * Model for a label in the property inspector.
 *
 * This model supports collapsing of child lines.
 * @property name A name used to identify an editor and/or a section of editors.
 * @property editorModel The model of an optional editor for this label.
 * @property properties A place to store the expansion state of this label such that it can be restored later.
 * @property expandable True if this label is expandable.
 * @property expanded True if this label is currently expanded.
 * @property icon Shows the expansion state f this is an expandable label, otherwise it may show the namespace of the property.
 */
class CollapsibleLabelModel(
  val name: String,
  val editorModel: PropertyEditorModel? = null,
  @TestOnly val properties: PropertiesComponent = PropertiesComponent.getInstance()
) : GenericInspectorLineModel() {
  private var children: MutableList<InspectorLineModel>? = null
  private var defaultExpansionValue = true

  var parent: CollapsibleLabelModel? = null
    private set

  val hasChildren: Boolean
    get() = children?.isNotEmpty() == true

  var expandable = false
    set(value) {
      field = value
      fireValueChanged()
    }

  var expanded = true
    set(value) {
      field = value
      children?.forEach { it.visible = value }
      properties.setValue(KEY_PREFIX + name, value, defaultExpansionValue)
      fireValueChanged()
    }

  val icon: Icon?
    get() {
      if (expandable) {
        return if (expanded) EXPANDED_ICON else COLLAPSED_ICON
      }
      return editorModel?.property?.namespaceIcon
    }

  val tooltip: String
    get() = editorModel?.property?.tooltip ?: ""

  override var hidden
    get() = super.hidden
    set(value) {
      super.hidden = value
      children?.forEach { it.hidden = value }
      editorModel?.refresh()
    }

  override var visible
    get() = super.visible
    set(value) {
      super.visible = value
      editorModel?.visible = value
      if (expandable) {
        children?.forEach { it.visible = expanded && value }
      }
    }

  var showEllipses = true
    set(value) {
      field = value
      fireValueChanged()
    }

  override val focusable: Boolean
    get() = editorModel != null

  override fun requestFocus() {
    editorModel?.requestFocus()
  }

  companion object {
    @JvmField @VisibleForTesting
    val EXPANDED_ICON = UIManager.get("Tree.expandedIcon") as Icon

    @JvmField @VisibleForTesting
    val COLLAPSED_ICON = UIManager.get("Tree.collapsedIcon") as Icon
  }

  override fun isMatch(matcher: Matcher) = editorModel != null && matcher.matches(name)

  override fun refresh() {
    editorModel?.refresh()
  }

  override fun makeExpandable(initiallyExpanded: Boolean) {
    defaultExpansionValue = initiallyExpanded
    expandable = true
    expanded = properties.getBoolean(KEY_PREFIX + name, defaultExpansionValue)
  }

  override fun addChild(child: InspectorLineModel) {
    if (children == null) {
      children = mutableListOf()
    }
    children?.add(child)
    child.visible = expanded && visible
    val expandableChild = child as? CollapsibleLabelModel
    expandableChild?.parent = this
  }
}
