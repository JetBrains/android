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
package com.android.tools.property.panel.impl.model

import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.PropertyEditorModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.text.Matcher
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon
import kotlin.properties.Delegates

// Prefix to the keys used to store/restore the state of expandable groups in the inspector
@VisibleForTesting const val KEY_PREFIX = "inspector.open."

/**
 * Model for a label in the property inspector.
 *
 * This model supports collapsing of child lines.
 *
 * @property name A name used to identify an editor and/or a section of editors.
 * @property editorModel The model of an optional editor for this label.
 * @property properties A place to store the expansion state of this label such that it can be
 *   restored later.
 * @property expandable True if this label is expandable.
 * @property expanded True if this label is currently expanded.
 * @property icon Shows the expansion state f this is an expandable label, otherwise it may show the
 *   namespace of the property.
 */
open class CollapsibleLabelModel(
  val name: String,
  val editorModel: PropertyEditorModel? = null,
  override val isSearchable: Boolean = false,
  @TestOnly val properties: PropertiesComponent = PropertiesComponent.getInstance(),
) : GenericInspectorLineModel() {
  @VisibleForTesting var children: MutableList<InspectorLineModel>? = null
  private var defaultExpansionValue = true

  var expandable = false
    private set(value) {
      field = value
      fireValueChanged()
    }

  override var expanded = true
    set(value) {
      if (field != value) {
        field = value
        children?.forEach { it.visible = value }
        properties.setValue(KEY_PREFIX + name, value, defaultExpansionValue)
        fireValueChanged()
      }
    }

  val icon: Icon?
    get() {
      if (expandable) {
        return if (expanded) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon()
      }
      return editorModel?.property?.namespaceIcon
    }

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

  override var enabled by Delegates.observable(true) { _, _, _ -> fireValueChanged() }

  override fun requestFocus() {
    editorModel?.requestFocus()
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

  fun addChild(child: GenericInspectorLineModel) {
    if (children == null) {
      children = mutableListOf()
    }
    children?.add(child)
    child.visible = expanded && visible
    child.parent = this
    val expandableChild = child as? CollapsibleLabelModel
    expandableChild?.parent = this
  }

  fun hideForSearch(isMatch: Boolean) {
    expandable = false
    visible = isMatch
  }

  fun restoreAfterSearch() {
    expandable = children?.isNotEmpty() == true
    if (parent == null) {
      visible = true
    }
  }
}

/**
 * This class exists for the benefit of unit testing only.
 *
 * The model is used for generated separators around title elements in the properties panel.
 */
class TitleLineModel(name: String) : CollapsibleLabelModel(name)
