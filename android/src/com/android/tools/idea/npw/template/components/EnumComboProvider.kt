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
import com.android.tools.idea.templates.Parameter
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_BUILD_API
import com.android.tools.idea.ui.ApiComboBoxItem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel

/**
 * Provides a combobox well suited for handling [Parameter.Type.ENUM] parameters.
 */
class EnumComboProvider(parameter: Parameter) : ParameterComponentProvider<ComboBox<*>>(parameter) {

  private val log: Logger
    get() = Logger.getInstance(EnumComboProvider::class.java)

  /**
   * Parse an enum option, which looks something like this:
   *
   * `<option id="choice_id" minApi="15" minBuildApi="17">Choice Description</option>
  ` *
   */
  private fun createItemForOption(parameter: Parameter, option: Element): ApiComboBoxItem<String> {
    val optionId = option.getAttribute(ATTR_ID)
    assert(StringUtil.isNotEmpty(optionId)) { ATTR_ID }
    val childNodes = option.childNodes.also {
      assert(it.length == 1 && it.item(0).nodeType == Node.TEXT_NODE)
    }
    val optionLabel = childNodes.item(0).nodeValue.trim()
    val minSdk = getIntegerOptionValue(option, ATTR_MIN_API, parameter.name, 1)
    val minBuildApi = getIntegerOptionValue(option, ATTR_MIN_BUILD_API, parameter.name, 1)
    return ApiComboBoxItem(optionId!!, optionLabel, minSdk, minBuildApi)
  }

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

  override fun createComponent(parameter: Parameter): ComboBox<*> {
    val options = parameter.options
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
  private class ApiComboBoxTextProperty(private val myComboBox: ComboBox<*>) : AbstractProperty<String>(), ActionListener {
    init {
      myComboBox.addActionListener(this)
    }

    override fun setDirectly(value: String) {
      val model = myComboBox.model as DefaultComboBoxModel<*>

      for (i in 0 until model.size) {
        val item = model.getElementAt(i) as ApiComboBoxItem<*>
        if (value == item.data) {
          myComboBox.selectedIndex = i
          return
        }
      }

      myComboBox.selectedIndex = -1
    }

    override fun get(): String {
      val item = myComboBox.selectedItem as? ApiComboBoxItem<*> ?: return ""
      return item.data as String
    }

    override fun actionPerformed(e: ActionEvent) {
      notifyInvalidated()
    }
  }
}
