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
package com.android.tools.idea.naveditor.property.support

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.ID_PREFIX
import com.android.ide.common.resources.stripPrefixFromId
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.model.parentSequence
import com.android.tools.idea.naveditor.model.visibleDestinations
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.EnumValue
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.util.ClassUtil
import com.intellij.util.SlowOperations
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DESTINATION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO
import org.jetbrains.android.dom.navigation.getClassesForTag
import org.jetbrains.android.dom.navigation.isInProject

private val emptyList = listOf(EnumValue.EMPTY)

class NavEnumSupportProvider : EnumSupportProvider<NlPropertyItem> {
  override fun invoke(actual: NlPropertyItem): EnumSupport? {
    val property = actual.delegate ?: actual

    val components = property.components
    if (components.size != 1) {
      return null
    }

    val component = components[0]

    val values = when (property.namespace) {
      AUTO_URI -> {
        when (property.name) {
          ATTR_DESTINATION,
          ATTR_POP_UP_TO -> getDestinations(component)
          ATTR_START_DESTINATION -> getStartDestinations(component)
          else -> return null
        }
      }
      ANDROID_URI -> {
        when (property.name) {
          ATTR_NAME -> {
            val classes = SlowOperations.allowSlowOperations(ThrowableComputable { getClasses(component) })
            return ClassEnumSupport(emptyList.plus(classes))
          }
          else -> return null
        }
      }
      else -> return null
    }

    return DestinationEnumSupport(emptyList.plus(values))
  }

  companion object {
    private fun getDestinations(component: NlComponent): List<EnumValue> {
      val destination = component.parent ?: return listOf()
      val visibleDestinations = destination.visibleDestinations

      val components = mutableListOf<NlComponent>()

      destination.parentSequence().forEach {
        components.add(it)
        components.addAll(visibleDestinations[it].orEmpty())
      }

      return getDestinationEnumValues(components)
    }

    private fun getStartDestinations(component: NlComponent): List<EnumValue> {
      val children = component.children.filter { it.destinationType != null }.sortedBy { it.id }
      return getDestinationEnumValues(children)
    }

    private fun getClasses(component: NlComponent): List<EnumValue> {
      return getClassesForTag(component.model.module, component.tagName)
        .filterKeys { it.qualifiedName != null }
        .map { ClassEnumValue(it.key.qualifiedName!!, displayString(it.key.qualifiedName!!), it.value, it.key.isInProject()) }
        .sortedWith(compareBy({ !it.isInProject }, { it.display }))
        .toList()
    }

    private fun displayString(qualifiedName: String): String {
      return "${ClassUtil.extractClassName(qualifiedName)} (${ClassUtil.extractPackageName(qualifiedName)})"
    }

    private fun getDestinationEnumValues(components: List<NlComponent>): List<EnumValue> {
      return components.mapNotNull { getDestinationEnumValue(it) }
    }

    private fun getDestinationEnumValue(component: NlComponent): EnumValue? {
      val id = component.id ?: return null
      return EnumValue.item(ID_PREFIX + id, id)
    }
  }

  private class DestinationEnumSupport(override val values: List<EnumValue>) : EnumSupport {
    override fun createValue(stringValue: String): EnumValue {
      return EnumValue.item(stringValue, stripPrefixFromId(stringValue))
    }
  }

  private class ClassEnumSupport(override val values: List<EnumValue>) : EnumSupport {
    override fun createValue(stringValue: String): EnumValue {
      return EnumValue.item(stringValue, displayString(stringValue))
    }
  }
}
