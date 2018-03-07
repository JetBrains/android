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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceAccessibility
import com.android.resources.ResourceType
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.EnumEditor
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getResourceItems
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import org.jetbrains.android.dom.navigation.NavigationSchema

// TODO: ideally this wouldn't be a separate editor, and EnumEditor could just get the EnumSupport from the property itself.
class AnimationEditor(listener: NlEditingListener, comboBox: CustomComboBox) : EnumEditor(listener, comboBox, null, true, false) {

  constructor() : this(NlEditingListener.DEFAULT_LISTENER, CustomComboBox())

  override fun getEnumSupport(property: NlProperty): EnumSupport = AnimationEnumSupport(property)

  private class AnimationEnumSupport(property: NlProperty) : EnumSupport(property) {
    override fun getAllValues(): List<ValueWithDisplayString> {
      val isFragment = myProperty.components
        .mapNotNull { it.actionDestination }
        .all { it.destinationType == NavigationSchema.DestinationType.FRAGMENT }
      // TODO: check the type of the start destination if the target is a graph


      val repoManager = ResourceRepositoryManager.getOrCreateInstance(myProperty.model.module) ?: return emptyList()
      return getAnimatorsPopupContent(repoManager, isFragment)
    }

    override fun createFromResolvedValue(resolvedValue: String, value: String?, hint: String?) =
      ValueWithDisplayString(value?.substringAfter('/'), value)
  }
}

fun getAnimatorsPopupContent(repoManager: ResourceRepositoryManager, isFragment: Boolean): List<ValueWithDisplayString> {
  // TODO: filter out interpolators
  val appResources = repoManager.getAppResources(true)!!
  val visibilityLookup = repoManager.resourceVisibility
  val result = appResources
    .getResourceItems(ResourceNamespace.TODO, ResourceType.ANIM, visibilityLookup, ResourceAccessibility.PUBLIC)
    .map { ValueWithDisplayString(it, "@${ResourceType.ANIM.getName()}/$it") }.toMutableList()
  if (isFragment) {
    appResources
      .getResourceItems(ResourceNamespace.TODO, ResourceType.ANIMATOR, visibilityLookup, ResourceAccessibility.PUBLIC)
      .mapTo(result) { ValueWithDisplayString(it, "@${ResourceType.ANIMATOR.getName()}/$it") }
  }
  return result
}
