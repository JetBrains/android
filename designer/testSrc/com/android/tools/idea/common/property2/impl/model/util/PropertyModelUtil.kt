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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.ANDROID_URI
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.support.ItemEnumValue
import com.android.tools.idea.common.property2.impl.ui.EnumValueListCellRenderer
import icons.StudioIcons
import javax.swing.Icon
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import javax.swing.ListCellRenderer

interface TestFlagsPropertyItem: FlagsPropertyItem<FlagPropertyItem> {
  override var resolvedValue: String?
}

object PropertyModelUtil {
  fun makeProperty(namespace: String, name: String, value: String?): PropertyItem {
    return object : PropertyItem {
      var propertyValue = value

      override val namespace: String
        get() = namespace

      override val namespaceIcon: Icon?
        get() = if (namespace == TOOLS_URI) StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY else null

      override val name: String
        get() = name

      override var value: String?
        get() = propertyValue
        set(value) {
          propertyValue = value
        }

      override val resolvedValue: String?
        get() = value?.plus("_resolved")

      override val isReference: Boolean
        get() = false
    }
  }

  fun makeFlagsProperty(propertyName: String, flagNames: List<String>, values: List<Int>): TestFlagsPropertyItem {
    require(flagNames.size == values.size)
    val property = object : TestFlagsPropertyItem {
      override val namespace = ANDROID_URI
      override val name = propertyName
      override val flags = mutableListOf<FlagPropertyItem>()
      override fun flag(itemName: String): FlagPropertyItem? = flags.firstOrNull { it.name == itemName }
      override var value: String? = null
        set(value) {
          field = value
          resolvedValue = value
        }
      override var resolvedValue: String? = null
      override var isReference = false
      override val maskValue: Int
        get() {
          var mask = 0
          flags.filter { valueAsSet.contains(it.name) }.forEach { mask = mask or it.maskValue }
          return mask
        }
      val valueAsSet: HashSet<String>
        get() {
          val value = resolvedValue ?: return HashSet()
          return HashSet(Splitter.on("|").trimResults().splitToList(value))
        }
    }

    for (index in flagNames.indices) {
      property.flags.add(object : FlagPropertyItem {
        override val flags = property
        override val namespace = ANDROID_URI
        override val name = flagNames[index]
        override val maskValue = values[index]
        override var actualValue: Boolean
          get() = flags.valueAsSet.contains(name)
          set(value) {
            val set = flags.valueAsSet
            if (value) {
              set.add(name)
            }
            else {
              set.remove(name)
            }
            flags.value = Joiner.on("|").join(set)
          }
        override val effectiveValue: Boolean
          get() = (flags.maskValue and maskValue) == maskValue
        override var value: String?
          get() = if (actualValue) "true" else "false"
          set(value) { actualValue = (value == "true") }
        override val isReference = false
      })
    }
    return property
  }

  fun makeEnumSupport(vararg values: String): EnumSupport {
    return object: EnumSupport {
      override val values: List<EnumValue> = values.map { ItemEnumValue(it) }

      override val renderer: ListCellRenderer<EnumValue> by lazy {
        EnumValueListCellRenderer()
      }
    }
  }

  fun makePropertyEditorModel(property: PropertyItem): PropertyEditorModel {
    return makePropertyEditorModel(property, null)
  }

  fun makePropertyEditorModel(property: PropertyItem, form: FormModel?): PropertyEditorModel {
    return object: PropertyEditorModel {

      override val property: PropertyItem
        get() = property

      override var value: String = property.value ?: ""

      override val formModel: FormModel
        get() = form ?: throw NotImplementedError()

      override var visible: Boolean = true

      override var focusRequest: Boolean = false

      override val focus = false

      override var line: InspectorLineModel? = null

      override fun refresh() {
        value = (if (form?.showResolvedValues == true) property.resolvedValue else property.value) ?: ""
      }

      override fun addListener(listener: ValueChangedListener) {
        throw NotImplementedError()
      }

      override fun removeListener(listener: ValueChangedListener) {
        throw NotImplementedError()
      }
    }
  }
}
