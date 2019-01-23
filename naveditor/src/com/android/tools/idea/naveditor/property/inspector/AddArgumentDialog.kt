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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.CLASS_PARCELABLE
import com.android.annotations.VisibleForTesting
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceUrl
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.argumentName
import com.android.tools.idea.naveditor.model.defaultValue
import com.android.tools.idea.naveditor.model.isArgument
import com.android.tools.idea.naveditor.model.nullable
import com.android.tools.idea.naveditor.model.setArgumentNameAndLog
import com.android.tools.idea.naveditor.model.setDefaultValueAndLog
import com.android.tools.idea.naveditor.model.setNullableAndLog
import com.android.tools.idea.naveditor.model.setTypeAndLog
import com.android.tools.idea.naveditor.model.typeAttr
import com.android.tools.idea.res.resolve
import com.android.tools.idea.uibuilder.model.createChild
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.ClassUtil
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import java.awt.CardLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JList

// open for testing
open class AddArgumentDialog(private val existingComponent: NlComponent?, private val parent: NlComponent) : DialogWrapper(false) {

  private var customType: String? = null
  private var selectedType: Type = Type.values().first()
  private val defaultValueComboModel = MutableCollectionComboBoxModel<String>()

  @VisibleForTesting
  val dialogUI = AddArgumentDialogUI()

  // Open for testing
  @VisibleForTesting
  open var type: String?
    get() {
      val selectedType = dialogUI.myTypeComboBox.selectedItem as Type
      return if (selectedType == Type.CUSTOM) customType else selectedType.attrValue
    }
    set(typeStr) {
      if (typeStr == null) {
        dialogUI.myTypeComboBox.setSelectedItem(Type.INFERRED)
      }
      else {
        var found = false
        for (type in Type.values()) {
          if (typeStr == type.attrValue) {
            dialogUI.myTypeComboBox.selectedItem = type
            found = true
            break
          }
        }
        if (!found) {
          customType = typeStr
          dialogUI.myTypeComboBox.selectedItem = Type.CUSTOM
        }
      }
      updateUi()
    }

  // Open for testing
  @VisibleForTesting
  open var defaultValue: String?
    get() = if (selectedType == Type.BOOLEAN || selectedType == Type.CUSTOM) {
      dialogUI.myDefaultValueComboBox.selectedItem as String?
    }
    else {
      dialogUI.myDefaultValueTextField.text
    }
    set(defaultValue) = if (selectedType == Type.BOOLEAN || selectedType == Type.CUSTOM) {
      dialogUI.myDefaultValueComboBox.setSelectedItem(defaultValue)
    }
    else {
      dialogUI.myDefaultValueTextField.text = defaultValue
    }

  @VisibleForTesting
  var isNullable: Boolean
    get() = dialogUI.myNullableCheckBox.isSelected
    set(nullable) {
      dialogUI.myNullableCheckBox.isSelected = nullable
    }

  // Open for testing
  @VisibleForTesting
  open var name: String?
    get() = dialogUI.myNameTextField.text
    set(name) {
      dialogUI.myNameTextField.text = name
    }

  internal enum class Type(var display: String, var attrValue: String?) {
    INFERRED("<inferred type>", null),
    INTEGER("Integer", "integer"),
    LONG("Long", "long"),
    BOOLEAN("Boolean", "boolean"),
    STRING("String", "string"),
    REFERENCE("Resource Reference", "reference"),
    CUSTOM("Custom Parcelable...", "custom");

    override fun toString(): String {
      return display
    }
  }

  init {
    init()
    Type.values().forEach { dialogUI.myTypeComboBox.addItem(it) }

    dialogUI.myTypeComboBox.setRenderer(object : ListCellRendererWrapper<Type>() {
      override fun customize(list: JList<*>, value: Type, index: Int, isSelected: Boolean, hasFocus: Boolean) {
        if (index == -1 && value == Type.CUSTOM) {
          setText(customType)
        }
        else {
          setText(value.display)
        }
        setBackground(UIUtil.getListBackground(isSelected))
        setForeground(UIUtil.getListForeground(isSelected))
      }
    })

    dialogUI.myTypeComboBox.isEditable = false

    dialogUI.myDefaultValueComboBox.model = defaultValueComboModel

    if (existingComponent != null) {
      name = existingComponent.argumentName
      type = existingComponent.typeAttr
      val nullable = existingComponent.nullable
      isNullable = nullable != null && nullable
      defaultValue = existingComponent.defaultValue
      myOKAction.putValue(Action.NAME, "Update")
      title = "Update Argument Link"
    }
    else {
      (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "textDefaultValue")
      myOKAction.putValue(Action.NAME, "Add")
      title = "Add Argument Link"
    }

    dialogUI.myTypeComboBox.addActionListener { event ->
      if ("comboBoxChanged" == event.actionCommand) {
        newTypeSelected()
      }
    }

    dialogUI.myDefaultValueComboBox.renderer = object : ListCellRendererWrapper<String>() {
      override fun customize(list: JList<*>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean) {
        setText(value ?: "No default value")
      }
    }
  }

  private fun newTypeSelected() {
    if (dialogUI.myTypeComboBox.selectedItem === Type.CUSTOM) {
      val project = parent.model.project
      val parcelable = ClassUtil.findPsiClass(PsiManager.getInstance(project), CLASS_PARCELABLE)
      val current = customType?.let { ClassUtil.findPsiClass(PsiManager.getInstance(project), it) }
      val chooser = TreeClassChooserFactory.getInstance(project)
        .createInheritanceClassChooser("Select Parcelable Class", GlobalSearchScope.allScope(project), parcelable, current)
      chooser.showDialog()
      val selection = chooser.selected
      if (selection != null) {
        customType = selection.qualifiedName
      }
      else {
        dialogUI.myTypeComboBox.setSelectedItem(selectedType)
      }
    }
    updateUi()
  }

  private fun updateUi() {
    val newType = dialogUI.myTypeComboBox.selectedItem as Type
    if (newType != selectedType) {
      val nullable = newType == Type.STRING || newType == Type.CUSTOM
      dialogUI.myNullableCheckBox.isEnabled = nullable
      dialogUI.myNullableLabel.isEnabled = nullable
      if (!nullable) {
        dialogUI.myNullableCheckBox.isSelected = false
      }
      when (newType) {
        Type.BOOLEAN -> {
          (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "comboDefaultValue")
          defaultValueComboModel.update(Lists.newArrayList<String>(null, "true", "false"))
        }
        Type.CUSTOM -> {
          (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "comboDefaultValue")
          defaultValueComboModel.update(Lists.newArrayList<String>(null, "@null"))
        }
        else -> {
          dialogUI.myDefaultValueTextField.text = ""
          (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "textDefaultValue")
        }
      }
      selectedType = newType
    }
  }

  override fun createCenterPanel(): JComponent {
    return dialogUI.myContentPanel
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  public override fun doValidate(): ValidationInfo? {
    val name = name
    if (name == null || name.isEmpty()) {
      return ValidationInfo("Name must be set", dialogUI.myNameTextField)
    }
    if (parent.children.any { c ->
        c !== existingComponent
        && c.isArgument
        && c.argumentName == name
      }) {
      return ValidationInfo("Name must be unique", dialogUI.myNameTextField)
    }
    var newDefaultValue = defaultValue
    if (newDefaultValue != null && !newDefaultValue.isEmpty()) {
      when (dialogUI.myTypeComboBox.selectedItem) {
        Type.LONG -> {
          if (!newDefaultValue.endsWith("L")) {
            newDefaultValue += "L"
          }
          try {
            (newDefaultValue.substring(0, newDefaultValue.length - 1)).toLong()
          }
          catch (e: NumberFormatException) {
            return ValidationInfo("Long default values must be in the format '1234L'")
          }

        }
        Type.INTEGER -> {
          try {
            newDefaultValue.toInt()
          }
          catch (e: NumberFormatException) {
            return ValidationInfo("Default value must be an integer")
          }

        }
        Type.REFERENCE -> {
          val url = ResourceUrl.parse(newDefaultValue) ?: return ValidationInfo("Reference not correctly formatted")
          val resourceResolver = parent.model.configuration.resourceResolver
          if (resourceResolver != null) {
            ApplicationManager.getApplication().runReadAction(Computable<ResourceValue> {
              resourceResolver.resolve(url, parent.tag)
            }) ?: return ValidationInfo("Resource does not exist")
          }
        }
      }
    }
    return null
  }

  fun save() {
    WriteCommandAction.runWriteCommandAction(parent.model.project) {
      var realComponent = existingComponent ?: parent.createChild(TAG_ARGUMENT)
      if (realComponent == null) {
        ApplicationManager.getApplication().invokeLater {
          Messages.showErrorDialog(parent.model.project, "Failed to create Argument!", "Error")
        }
        return@runWriteCommandAction
      }

      realComponent.setArgumentNameAndLog(name, NavEditorEvent.Source.PROPERTY_INSPECTOR)
      realComponent.setTypeAndLog(type, NavEditorEvent.Source.PROPERTY_INSPECTOR)
      realComponent.setNullableAndLog(isNullable, NavEditorEvent.Source.PROPERTY_INSPECTOR)
      var newDefaultValue = defaultValue
      if (newDefaultValue != null && !newDefaultValue.isEmpty()
          && dialogUI.myTypeComboBox.selectedItem === Type.LONG && !newDefaultValue.endsWith("L")) {
        newDefaultValue += "L"
      }
      realComponent.setDefaultValueAndLog(newDefaultValue, NavEditorEvent.Source.PROPERTY_INSPECTOR)
    }
  }
}
