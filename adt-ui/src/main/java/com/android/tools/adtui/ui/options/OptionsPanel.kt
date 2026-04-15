/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.adtui.model.options.Dropdown
import com.android.tools.adtui.model.options.OptionsBinder
import com.android.tools.adtui.model.options.OptionsProperty
import com.android.tools.adtui.model.options.OptionsProvider
import com.android.tools.adtui.model.options.PropertyInfo
import com.android.tools.adtui.model.options.Slider
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.KeyboardFocusManager
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.Locale
import javax.swing.AbstractButton
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities

/**
 * The OptionsPanel control is dynamically populated based on the currently set {@link OptionsProvider}. This control will enumerate all
 * methods with the property attribute set on a given OptionsProvider object. It will then use the return type of each method to determine
 * which OptionsBinder to use in generating the UI.
 */
class OptionsPanel : JComponent() {
  /** Map of return types to OptionsBinders. Values can be added or replaced in this map. */
  val binders = mutableMapOf<Class<*>, OptionsBinder>()
  private val groups = mutableMapOf<String, JPanel>()
  private var isReadOnly = true
  private var isTaskBasedUx = false
  private var option: OptionsProvider? = null

  init {
    layout = VerticalFlowLayout()
    // Default binders.
    binders[Boolean::class.java] = BooleanBinder()
    binders[Int::class.java] = IntBinder()
    binders[String::class.java] = StringBinder()
  }

  fun setOption(newOption: OptionsProvider?, readOnly: Boolean, taskBasedUx: Boolean) {
    option = newOption
    isReadOnly = readOnly
    isTaskBasedUx = taskBasedUx
    updateOptionProvider()
  }

  /**
   * To map accessor / mutator / attribute functions as the same the method name should be stripped of known prefix's and suffix's.
   * setEnabled = enabled (mutator) isEnabled = enabled (accessor) isEnabled$annotations = enabled (static attribute)
   */
  private fun cleanMethodName(rawMethodName: String): String {
    return rawMethodName
      .removePrefix("get")
      .removePrefix("set")
      .removePrefix("is")
      .removeSuffix("\$annotations")
      .lowercase(Locale.getDefault())
  }

  private fun updateOptionProvider() {
    // Cache the currently focused button text so we can restore focus after the UI rebuild.
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner

    // ONLY save the focus state if the currently focused component is actually INSIDE this OptionsPanel,
    // to prevent stealing focus from other parts of Android Studio during the initial load.
    val focusedText =
      if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, this)) {
        (focusOwner as? AbstractButton)?.text
      } else {
        null
      }

    removeAll()
    groups.clear()
    if (option == null) {
      return
    }
    val methods = option!!.javaClass.methods
    val properties = mutableMapOf<String, PropertyInfo>()
    // In kotlin attributes can be assigned to the get/set methods directly or to the property
    // itself. If set to the property they resolve
    // as a static and not on the accessor/mutator for the intended property. To work around this
    // first find all all attributes and
    // associated method names
    for (method in methods) {
      val methodName = cleanMethodName(method.name)
      if (method.getAnnotation(OptionsProperty::class.java) != null) {
        val info = properties.computeIfAbsent(methodName) { PropertyInfo(option!!, methodName) }
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
        if (!propertyMetadata.parent.isBlank()) {
          info.parent = propertyMetadata.parent
          info.parentValue = propertyMetadata.parentValue
        }
      }
    }

    // Link children to parents
    properties.values.forEach { info ->
      if (info.parent.isNotEmpty()) {
        val parentInfo = properties[cleanMethodName(info.parent)]
        if (parentInfo != null) {
          parentInfo.children = parentInfo.children + info
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
      } else if (method.parameterCount == 0 && method.returnType != Void.TYPE) {
        info.accessor = method
        info.binder = info.binder ?: binders[info.accessor?.returnType]
        if (info.binder == null && info.accessor?.returnType?.isEnum == true) {
          info.binder = EnumBinder { updateOptionProvider() }
        }
      }
      if (method.getAnnotation(Slider::class.java) != null) {
        val slider = method.getAnnotation(Slider::class.java)
        info.binder = SliderBinder(slider.min, slider.max, slider.step)
      }
      if (method.getAnnotation(Dropdown::class.java) != null) {
        val dropdown = method.getAnnotation(Dropdown::class.java)
        info.binder = DropdownBinder(dropdown.values.toList())
      }
    }
    buildHeader(properties["name"])
    buildPropertyUI(properties.values.toList().sortedBy { it.name })
    revalidate()
    repaint()

    // Restore focus to the new instance of the previously focused component
    if (focusedText != null) {
      val componentToFocus = findComponentWithText(this, focusedText)
      SwingUtilities.invokeLater { componentToFocus?.requestFocusInWindow() }
    }
  }

  private fun findComponentWithText(container: Container, text: String): Component? {
    for (component in container.components) {
      if (component is AbstractButton && component.text == text) {
        return component
      }
      if (component is Container) {
        val found = findComponentWithText(component, text)
        if (found != null) return found
      }
    }
    return null
  }

  private fun buildHeader(propertyInfo: PropertyInfo?) {
    // For task-based ux, in edit config dialog the name of the task is not displayed as a field but
    // rather as a header. So, if there
    // isn't a name property, then there won't be a header.
    if (!isTaskBasedUx || propertyInfo == null) {
      return
    }
    val name = propertyInfo.value.toString()
    val headerPanel = JPanel(VerticalFlowLayout())
    val headerLabel = JLabel(name)
    headerLabel.font = headerLabel.font.deriveFont(Font.BOLD)
    headerLabel.setSize(100, 100)
    headerPanel.add(headerLabel)
    headerPanel.border = JBUI.Borders.emptyBottom(15)
    add(headerPanel)
  }

  private fun buildPropertyUI(properties: List<PropertyInfo>) {
    // Group by groups
    val sortedProperties = properties.sortedWith(compareBy<PropertyInfo> { it.order })
    for (property in sortedProperties) {
      if (property.accessor == null) continue

      // Skip properties that have a parent, they are handled by the parent
      if (property.parent.isNotEmpty()) {
        continue
      }

      // Check visibility
      if (option?.isVisible(property.methodName) == false) {
        continue
      }

      val groupPanel = buildOrGetGroup(property.group)

      if (!(isTaskBasedUx && property.methodName == "name")) {
        val component = buildComponent(property)

        // Apply indentation if the property metadata specifies it
        if (property.indent) {
          component.border = JBUI.Borders.merge(component.border, JBUI.Borders.emptyLeft(20), true)
        }

        groupPanel.add(component)
      }

      if (property.description.isNotEmpty()) {
        groupPanel.add(
          JLabel(property.description).apply {
            // Match the horizontal position of the control
            // For boolean binders, the control is a Checkbox which doesn't have a 120px preceding label
            val leftPadding =
              if (property.binder is BooleanBinder) {
                if (property.indent) 44 else 24
              } else {
                if (property.indent) 140 else 120
              }
            border = JBUI.Borders.emptyLeft(leftPadding)
            foreground = JBColor(0x4E4E4E, 0xB5B5B5)
          }
        )
      }
    }
    for (panel in groups.values) {
      add(panel)
    }
  }

  private fun buildOrGetGroup(group: String): JPanel {
    if (group == DEFAULT_GROUP || isTaskBasedUx) {
      // No group name for TaskBasedUx
      return groups.computeIfAbsent(DEFAULT_GROUP) { JPanel(VerticalFlowLayout()) }
    }
    return groups.computeIfAbsent(group) {
      JPanel(VerticalFlowLayout()).apply {
        add(
          JPanel(TabularLayout("Fit,10px,*", "*,*")).apply {
            border = JBUI.Borders.emptyTop(12)
            add(JLabel(group), TabularLayout.Constraint(0, 0))
            add(
              JPanel(TabularLayout("*", "*,*")).apply { add(JSeparator(), TabularLayout.Constraint(1, 0)) },
              TabularLayout.Constraint(0, 2),
            )
          }
        )
      }
    }
  }

  private fun buildComponent(data: PropertyInfo): JComponent {
    // Use the OptionsBinder to build a UI component. If this fails or returns null, then we
    // fallback a label with "Unknown return type".
    val readOnly =
      data.mutator == null ||
        data.mutator?.parameterCount != 1 ||
        data.mutator?.parameterTypes!![0] != data.accessor?.returnType ||
        isReadOnly
    val component =
      data.binder?.bind(data, readOnly) ?: JLabel("Unknown return type (${data.accessor?.returnType?.name}) for property \"${data.name}\"")
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
    val valueLabel =
      JLabel("${data.value} ${data.unit}").apply {
        border = JBUI.Borders.emptyLeft(5)
        isEnabled = false
      }
    return JPanel(TabularLayout("120px,*,Fit", "*")).apply {
      border = JBUI.Borders.emptyTop(12)
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))
      add(
        JSlider(min, max, data.accessor?.invoke((data.provider)) as Int).apply {
          majorTickSpacing = step
          paintTicks = true
          isEnabled = !readonly
          addChangeListener {
            data.value = this.value
            valueLabel.text = "${data.value} ${data.unit}"
          }
        },
        TabularLayout.Constraint(0, 1),
      )
      add(valueLabel, TabularLayout.Constraint(0, 2))
    }
  }
}

private class IntBinder : OptionsBinder {
  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    return JPanel(TabularLayout("120px,Fit,Fit,Fit", "Fit")).apply {
      border = JBUI.Borders.emptyTop(12)
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))
      add(
        JSpinner(SpinnerNumberModel(data.value as Int, 0, 100000, 100)).apply {
          addChangeListener { data.value = this.value }
          isEnabled = !readonly
        },
        TabularLayout.Constraint(0, 1),
      )
      add(JLabel(data.unit), TabularLayout.Constraint(0, 2))
    }
  }
}

private class StringBinder : OptionsBinder {
  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    return JPanel(TabularLayout("120px,300px,*", "Fit")).apply {
      border = JBUI.Borders.emptyTop(12)
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))
      add(
        JBTextField(data.value?.toString()).apply {
          addKeyListener(
            object : KeyAdapter() {
              override fun keyReleased(e: KeyEvent) {
                data.value = text
              }
            }
          )
          isEnabled = !readonly
        },
        TabularLayout.Constraint(0, 1),
      )
    }
  }
}

private class EnumBinder(private val onUpdate: () -> Unit) : OptionsBinder {
  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    val returnType = data.accessor!!.returnType
    val enumConstants = returnType.enumConstants
    val buttonGroup = javax.swing.ButtonGroup()

    // Vertical layout for radio buttons and their descriptions
    val radioPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false))

    enumConstants.forEachIndexed { index, constant ->
      val radioButton =
        javax.swing.JRadioButton(constant.toString()).apply {
          isSelected = constant == data.value
          isEnabled = !readonly
          addActionListener {
            data.value = constant
            // Trigger a refresh of the panel to update visibility of other components
            SwingUtilities.invokeLater { onUpdate() }
          }
        }
      buttonGroup.add(radioButton)
      radioPanel.add(radioButton)

      // Look up description for this specific enum value
      val description = data.provider.getDescription(data.methodName, constant)
      if (description != null) {
        radioPanel.add(
          JLabel(description).apply {
            border = JBUI.Borders.emptyLeft(28) // Increased indent to align with text
            foreground = JBColor(0x4E4E4E, 0xB5B5B5)
            font = font.deriveFont(font.size2D - 1f) // Slightly smaller font for sub-labels
          }
        )
      }

      // Render children properties that should appear under this enum value
      val children = data.children.filter { it.parentValue == (constant as Enum<*>).name && data.provider.isVisible(it.methodName) }

      children.forEach { child ->
        val childComponent =
          child.binder?.bind(child, readonly)
            ?: JLabel("Unknown return type (${child.accessor?.returnType?.name}) for property \"${child.name}\"")
        childComponent.isEnabled = !readonly
        childComponent.border = JBUI.Borders.merge(childComponent.border, JBUI.Borders.emptyLeft(28), true)
        radioPanel.add(childComponent)

        if (child.description.isNotEmpty()) {
          val descLabel =
            JLabel(child.description).apply {
              border = JBUI.Borders.empty(0, 28, 10, 0)
              foreground = JBColor(0x4E4E4E, 0xB5B5B5)
            }
          radioPanel.add(descLabel)
        } else {
          childComponent.border = JBUI.Borders.merge(childComponent.border, JBUI.Borders.emptyBottom(10), true)
        }
      }

      // Add spacing after each option block, except the last one
      if (index < enumConstants.size - 1) {
        radioPanel.add(javax.swing.Box.createVerticalStrut(10))
      }
    }

    return JPanel(TabularLayout("Fit,10px,*,Fit", "Fit,Fit")).apply {
      border = JBUI.Borders.emptyTop(12)
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))

      val separatorPanel = JPanel(GridBagLayout())
      val gbc = GridBagConstraints()
      gbc.fill = GridBagConstraints.HORIZONTAL
      gbc.weightx = 1.0
      separatorPanel.add(JSeparator(), gbc)
      add(separatorPanel, TabularLayout.Constraint(0, 2))

      radioPanel.border = JBUI.Borders.emptyTop(10)
      add(radioPanel, TabularLayout.Constraint(1, 0, 1, 4))
    }
  }
}

private class DropdownBinder(private val values: List<Int>) : OptionsBinder {

  override fun bind(data: PropertyInfo, readonly: Boolean): JComponent {
    // Consume description so EnumBinder doesn't render it
    val description = data.description
    data.description = ""

    val unit = data.unit.ifEmpty { "" }
    val displayValues = values.map { DisplayInt(it, unit) }.toTypedArray()

    return JPanel(TabularLayout("Fit,10px,*", "Fit,Fit")).apply {
      border = JBUI.Borders.emptyTop(12)
      add(JLabel(data.name), TabularLayout.Constraint(0, 0))

      val comboBox =
        com.intellij.openapi.ui.ComboBox(displayValues).apply {
          selectedItem = displayValues.find { it.value == data.value }
          isEnabled = !readonly
          setRenderer(
            object : DefaultListCellRenderer() {
              override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
              ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                if (value is DisplayInt) {
                  // index -1 indicates the selected item displayed in the combo box button
                  if (index == -1) {
                    val colorHex = ColorUtil.toHex(JBColor(0x4E4E4E, 0xB5B5B5))
                    component.text = "<html>${value.value} <span style='color:#$colorHex'>${value.unit}</span></html>"
                  } else {
                    component.text = value.toString()
                  }
                }
                return component
              }
            }
          )
          addActionListener { data.value = (selectedItem as DisplayInt).value }
        }

      // Wrap in FlowLayout to prevent stretching if the column is wider (due to description)
      val wrapper =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
          add(comboBox)
          isOpaque = false
        }
      add(wrapper, TabularLayout.Constraint(0, 2))

      if (description.isNotEmpty()) {
        val descLabel =
          JLabel("<html>$description</html>").apply {
            foreground = JBColor(0x4E4E4E, 0xB5B5B5)
            font = font.deriveFont(font.size2D - 1f)
          }
        add(descLabel, TabularLayout.Constraint(1, 2))
      }
    }
  }
}

private data class DisplayInt(val value: Int, val unit: String) {
  override fun toString(): String {
    return "$value"
  }
}
