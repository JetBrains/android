/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.ui.options

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.model.options.DEFAULT_GROUP
import com.android.tools.adtui.model.options.DEFAULT_ORDER
import com.android.tools.adtui.model.options.OptionsBinder
import com.android.tools.adtui.model.options.OptionsProvider
import com.android.tools.adtui.model.options.OptionsProperty
import com.android.tools.adtui.model.options.PropertyInfo
import com.android.tools.adtui.model.options.Slider
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * The OptionsPanel control is dynamically populated based on the currently set {@link OptionsProvider}. This control will enumerate all
 * methods with the property attribute set on a given OptionsProvider object. It will then use the return type of each method to determine
 * which OptionsBinder to use in generating the UI.
 */
class OptionsPanel : JComponent() {
  /**
   * Map of return types to OptionsBinders. Values can be added or replaced in this map.
   */
  val binders = mutableMapOf<Class<*>, OptionsBinder>()
  private val groups = mutableMapOf<String, JPanel>()
  private var isReadOnly = true
  private var option: OptionsProvider? = null
  init {
    layout = VerticalFlowLayout()
    // Default binders.
    binders[Boolean::class.java] = BooleanBinder()
    binders[Int::class.java] = IntBinder()
    binders[String::class.java] = StringBinder()
  }

  fun setOption(newOption: OptionsProvider?, readOnly: Boolean) {
    option = newOption
    isReadOnly = readOnly
    updateOptionProvider()
  }

  /**
   * To map accessor / mutator / attribute functions as the same the method name should be stripped of known prefix's and suffix's.
   * setEnabled = enabled (mutator)
   * isEnabled = enabled (accessor)
   * isEnabled$annotations = enabled (static attribute)
   */
  private fun cleanMethodName(rawMethodName: String): String {
    return rawMethodName.removePrefix("get")
      .removePrefix("set")
      .removePrefix("is")
      .removeSuffix("\$annotations")
      .lowercase(Locale.getDefault())
  }

  private fun updateOptionProvider() {
    removeAll()
    groups.clear()
    if (option == null) {
      return
    }
    val methods = option!!.javaClass.methods
    val properties = mutableMapOf<String, PropertyInfo>()
    // In kotlin attributes can be assigned to the get/set methods directly or to the property itself. If set to the property they resolve
    // as a static and not on the accessor/mutator for the intended property. To work around this first find all all attributes and
    // associated method names
    for (method in methods) {
      val methodName = cleanMethodName(method.name)
      if (method.getAnnotation(OptionsProperty::class.java) != null) {
        val info = properties.computeIfAbsent(methodName) {
          PropertyInfo(option!!, methodName)
        }
        val propertyMetadata = method.getAnnotation(OptionsProperty::class.java)
        if (!propertyMetadata.name.isBlank()) {
          info.name = propertyMetadata.name
        }
        if (info.group == DEFAULT_GROUP) {
          info.group = propertyMetadata.group
        }
        if (info.description.isBlank()) {
          info.description = propertyMetadata.description
        }
        if (info.order == DEFAULT_ORDER) {
          info.order = propertyMetadata.order
        }
        if (!propertyMetadata.unit.isBlank()) {
          info.unit = propertyMetadata.unit
        }
      }
    }

    for (method in methods) {
      val methodName = cleanMethodName(method.name)
      if (!properties.contains(methodName)) {
        continue
      }
      val info = properties[methodName]!!
      if (method.parameterCount == 1) {
        info.mutator = method
      }
      else if (method.parameterCount == 0 && method.returnType != Void.TYPE) {
        info.accessor = method
        info.binder = info.binder ?: binders[info.accessor?.returnType]
      }
      if (method.getAnnotation(Slider::class.java) != null) {
        val slider = method.getAnnotation(Slider::class.java)
        info.binder = SliderBinder(slider.min, slider.max, slider.step)
      }
    }
    buildPropertyUI(properties.values.toList().sortedBy { it.name })
    revalidate()
    repaint()
  }

  private fun buildPropertyUI(properties: List<PropertyInfo>) {
    // Group by groups
    val sortedProperties = properties.sortedWith(compareBy<PropertyInfo> { it.order })
    for (property in sortedProperties) {
      if (property.accessor == null) {
        continue
      }
      buildOrGetGroup(property.group).add(buildComponent(property))
      if (!property.description.isBlank()) {
        buildOrGetGroup(property.group).add(JLabel(property.description).apply {
          border = JBUI.Borders.emptyLeft(20)
          foreground = JBColor(0x4E4E4E, 0xB5B5B5)
        })
      }
    }
    for (panel in groups.values) {
      add(panel)
    }
  }

  private fun buildOrGetGroup(group: String): JPanel {
    if (group == DEFAULT_GROUP) {
      return groups.computeIfAbsent(DEFAULT_GROUP) { JPanel(VerticalFlowLayout()) }
    }
    return groups.computeIfAbsent(group) {
      JPanel(VerticalFlowLayout()).apply {
        add(JPanel(TabularLayout("Fit,10px,*", "*,*")).apply {
          border = JBUI.Borders.emptyTop(12)
          add(JLabel(group), TabularLayout.Constraint(0, 0))
          add(JPanel(TabularLayout("*", "*,*"))
                .apply {
                  add(JSeparator(), TabularLayout.Constraint(1, 0))
                }, TabularLayout.Constraint(0, 2))
        })
      }
    }
  }

  private fun buildComponent(data: PropertyInfo): JComponent {
    // Use the OptionsBinder to build a UI component. If this fails or returns null, then we fallback a label with "Unknown return type".
    val readOnly = data.mutator == null ||
                  data.mutator?.parameterCount != 1 ||
                  data.mutator?.parameterTypes!![0] != data.accessor?.returnType ||
                  isReadOnly
    val component = data.binder?.bind(data, readOnly) ?: JLabel(
      "Unknown return type (${data.accessor?.returnType?.name}) for property \"${data.name}\"")
    component.isEnabled = !readOnly
    return component
  }
}

private class BooleanBinder : OptionsBinder {
  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    return JBCheckBox(data.name, data.accessor?.invoke(data.provider) as Boolean).apply {
      addChangeListener { data.value = this.isSelected }
      isEnabled = !readonly
    }
  }
}

private class SliderBinder(private val min: Int, private val max: Int, private val step: Int) : OptionsBinder {
  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    val valueLabel = JLabel("${data.value} ${data.unit}").apply {
      border = JBUI.Borders.emptyLeft(5)
      isEnabled = false
    }
    return JPanel(TabularLayout("120px,*,Fit", "*")).apply {
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))
      add(JSlider(min, max, data.accessor?.invoke((data.provider)) as Int).apply {
        majorTickSpacing = step
        paintTicks = true
        isEnabled = !readonly
        addChangeListener {
          data.value = this.value
          valueLabel.text = "${data.value} ${data.unit}"
        }
      }, TabularLayout.Constraint(0, 1))
      add(valueLabel, TabularLayout.Constraint(0, 2))
    }
  }
}

private class IntBinder : OptionsBinder {
  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    return JPanel(TabularLayout("120px,Fit,Fit,Fit", "Fit")).apply {
      border = JBUI.Borders.emptyTop(12)
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))
      add(JSpinner(SpinnerNumberModel(data.value as Int, 0, 100000, 100)).apply {
        addChangeListener {
          data.value = this.value
        }
        isEnabled = !readonly
      }, TabularLayout.Constraint(0, 1))
      add(JLabel(data.unit), TabularLayout.Constraint(0, 2))
    }
  }
}

private class StringBinder : OptionsBinder {
  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    return JPanel(TabularLayout("120px,300px,*", "Fit")).apply {
      border = JBUI.Borders.emptyTop(12)
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))
      add(JBTextField(data.value?.toString()).apply {
        addKeyListener(object : KeyAdapter() {
          override fun keyReleased(e: KeyEvent) {
            data.value = text
          }
        })
        isEnabled = !readonly
      }, TabularLayout.Constraint(0, 1))
    }
  }
}