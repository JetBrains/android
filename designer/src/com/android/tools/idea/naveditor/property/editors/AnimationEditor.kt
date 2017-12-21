// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.property.editors

import com.android.resources.ResourceType
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.EnumEditor
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.jetbrains.android.resourceManagers.ResourceManager

// TODO: ideally this wouldn't be a separate editor, and EnumEditor could just get the EnumSupport from the property itself.
class AnimationEditor(listener: NlEditingListener, comboBox: CustomComboBox) : EnumEditor(listener, comboBox, null, true, false) {

  constructor() : this(NlEditingListener.DEFAULT_LISTENER, CustomComboBox())

  override fun getEnumSupport(property: NlProperty): EnumSupport = AnimationEnumSupport(property)

  private class AnimationEnumSupport(property: NlProperty) : EnumSupport(property) {
    override fun getAllValues(): List<ValueWithDisplayString> {
      val resourceManager = LocalResourceManager.getInstance(myProperty.model.module) ?: return emptyList()
      val isFragment = myProperty.components
          .mapNotNull { it.actionDestination }
          .all { it.destinationType == NavigationSchema.DestinationType.FRAGMENT }
      // TODO: check the type of the start destination if the target is a graph

      return getAnimatorsPopupContent(resourceManager, isFragment)
    }

    override fun createFromResolvedValue(resolvedValue: String, value: String?, hint: String?) =
        ValueWithDisplayString(value?.substringAfter('/'), value)
  }
}

fun getAnimatorsPopupContent(resourceManager: ResourceManager, isFragment: Boolean): List<ValueWithDisplayString> {
  // TODO: filter out interpolators
  val result = resourceManager.getResourceNames(ResourceType.ANIM, true)
      .map { ValueWithDisplayString(it, "@${ResourceType.ANIM.getName()}/$it") }.toMutableList()
  if (isFragment) {
    resourceManager.getResourceNames(ResourceType.ANIMATOR, true)
        .mapTo(result) { ValueWithDisplayString(it, "@${ResourceType.ANIMATOR.getName()}/$it") }
  }
  return result
}
