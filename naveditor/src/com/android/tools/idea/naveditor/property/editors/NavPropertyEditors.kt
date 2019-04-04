/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.editors

import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.common.property.editors.NonEditableEditor
import com.android.tools.idea.common.property.editors.PropertyEditors
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isInclude
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.property.TYPE_EDITOR_PROPERTY_LABEL
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import com.android.tools.idea.uibuilder.property.editors.NlBooleanEditor
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor
import com.intellij.openapi.project.Project
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DESTINATION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_EXIT_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_EXIT_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO

class NavPropertyEditors : PropertyEditors() {

  override fun resetCachedEditors() {}

  override fun create(property: NlProperty): NlComponentEditor {
    val project = property.model.project
    val component = property.components.firstOrNull() ?: throw IllegalStateException()

    when {
      property.name == TYPE_EDITOR_PROPERTY_LABEL -> return NonEditableEditor(project)
      component.isInclude ->
        when (property.name) {
          ATTR_GRAPH -> return SourceGraphEditor()
        }
      component.isNavigation ->
        when (property.name) {
          ATTR_START_DESTINATION -> return ChildDestinationsEditor()
        }
      component.isDestination ->
        when (property.name) {
          ATTR_NAME -> return DestinationClassEditor()
          ATTR_LAYOUT -> return NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER)
        }
      component.isAction && component.parent?.isDestination == true ->
        when (property.name) {
          ATTR_ENTER_ANIM, ATTR_EXIT_ANIM, ATTR_POP_ENTER_ANIM, ATTR_POP_EXIT_ANIM -> return AnimationEditor()
          ATTR_POP_UP_TO -> return AllDestinationsEditor()
          ATTR_DESTINATION -> return VisibleDestinationsEditor()
        }
    }

    return when {
      property.definition?.formats?.contains(AttributeFormat.BOOLEAN) == true -> NlBooleanEditor.createForInspector(DEFAULT_LISTENER)
      // SimpleProperty doesn't allow editing
      property is SimpleProperty -> NonEditableEditor(project)
      else -> TextEditor(project, true, DEFAULT_LISTENER)
    }
    // TODO: handle other types
  }

  companion object Factory {
    fun getInstance(project: Project): NavPropertyEditors {
      return project.getComponent(NavPropertyEditors::class.java)
    }
  }
}