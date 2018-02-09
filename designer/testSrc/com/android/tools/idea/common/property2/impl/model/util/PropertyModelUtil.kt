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
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.support.ItemEnumValue
import com.android.tools.idea.common.property2.impl.ui.EnumValueListCellRenderer
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.ListCellRenderer

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
