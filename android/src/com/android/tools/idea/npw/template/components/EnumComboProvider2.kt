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
package com.android.tools.idea.npw.template.components

import com.android.SdkConstants.ATTR_ID
import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.ui.ApiComboBoxItem
import com.android.tools.idea.wizard.template.EnumParameter
import com.android.tools.idea.wizard.template.Parameter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import org.w3c.dom.Element
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel

/**
 * Provides a [ComboBox] well suited for handling [EnumParameter] parameters.
 */
class EnumComboProvider2(parameter: EnumParameter<*>) : ParameterComponentProvider2<ComboBox<*>>(parameter) {
  private val log: Logger = Logger.getInstance(EnumComboProvider2::class.java)

  // TODO add support for min api, or better yet custom availability condition
  private fun createItemForOption(parameter: EnumParameter<*>, value: Enum<*>): ApiComboBoxItem<String> =
    ApiComboBoxItem(value.name, value.name, 1, 1)

  /**
   * Helper method to parse any integer attributes found in an option enumeration.
   */
  @Suppress("SameParameterValue")
  private fun getIntegerOptionValue(option: Element, attribute: String, parameterName: String?, defaultValue: Int): Int {
    val stringValue = option.getAttribute(attribute)
    try {
      return if (StringUtil.isEmpty(stringValue)) defaultValue else stringValue.toInt()
    }
    catch (e: NumberFormatException) {
      log.warn("Invalid $attribute value ($stringValue) for option ${option.getAttribute(ATTR_ID)} in parameter $parameterName", e)
      return defaultValue
    }
  }

  override fun createComponent(parameter: Parameter<*>): ComboBox<*> {
    val options = (parameter as EnumParameter<*>).options // FIXME EnumParameterComponentProvider?
    val comboBoxModel = DefaultComboBoxModel<Any>()

    assert(options.isNotEmpty())
    options.forEach {
      comboBoxModel.addElement(createItemForOption(parameter, it))
    }
    return ComboBox(comboBoxModel)
  }

  override fun createProperty(component: ComboBox<*>): AbstractProperty<*>? = ApiComboBoxTextProperty(component)

  /**
   * Swing property which interacts with [ApiComboBoxItem]s.
   *
   * NOTE: This is currently only needed here but we can promote it to ui.wizard.properties if it's ever needed in more places.
   */
  private class ApiComboBoxTextProperty(private val comboBox: ComboBox<*>) : AbstractProperty<String>(), ActionListener {
    init {
      comboBox.addActionListener(this)
    }

    override fun setDirectly(value: String) {
      val model = comboBox.model as DefaultComboBoxModel<*>

      for (i in 0 until model.size) {
        val item = model.getElementAt(i) as ApiComboBoxItem<*>
        if (value == item.data) {
          comboBox.selectedIndex = i
          return
        }
      }

      comboBox.selectedIndex = -1
    }

    override fun get(): String {
      val item = comboBox.selectedItem as? ApiComboBoxItem<*> ?: return ""
      return item.data as String
    }

    override fun actionPerformed(e: ActionEvent) {
      notifyInvalidated()
    }
  }
}
