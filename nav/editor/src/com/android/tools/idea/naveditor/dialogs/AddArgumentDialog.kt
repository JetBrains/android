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
package com.android.tools.idea.naveditor.dialogs

import com.android.SdkConstants.CLASS_PARCELABLE
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceUrl
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
import com.android.tools.idea.res.FloatResources
import com.android.tools.idea.res.resolve
import com.android.tools.idea.uibuilder.model.createChild
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.ClassUtil
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import org.jetbrains.kotlin.utils.doNothing
import java.awt.CardLayout
import java.awt.Dimension
import java.lang.IllegalStateException
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JList

// open for testing
class AddArgumentDialog(
  private val existingComponent: NlComponent?,
  private val parent: NlComponent,
  private val kotlinTreeClassChooserFactory: KotlinTreeClassChooserFactory = KotlinTreeClassChooserFactory.getInstance()) : DialogWrapper(false) {

  private var selectedType: Type = Type.values().first()
  private val defaultValueComboModel = MutableCollectionComboBoxModel<String>()

  private val psiManager = PsiManager.getInstance(parent.model.project)
  internal val parcelableClass = ClassUtil.findPsiClass(psiManager, CLASS_PARCELABLE)!!
  internal val serializableClass = ClassUtil.findPsiClass(psiManager, "java.io.Serializable")!!

  @VisibleForTesting
  val dialogUI = AddArgumentDialogUI()

  // open for testing
  @VisibleForTesting
  open var type: String? = null

  // Open for testing
  @VisibleForTesting
  open var defaultValue: String?
    get() = if (selectedType == Type.BOOLEAN || selectedType.isCustom || isArray) {
      dialogUI.myDefaultValueComboBox.selectedItem as String?
    }
    else {
      dialogUI.myDefaultValueTextField.text
    }
    set(defaultValue) = if (selectedType == Type.BOOLEAN || selectedType.isCustom || isArray) {
      dialogUI.myDefaultValueComboBox.setSelectedItem(defaultValue)
    }
    else {
      dialogUI.myDefaultValueTextField.text = defaultValue
    }

  @VisibleForTesting
  var isArray: Boolean
    get() = dialogUI.myArrayCheckBox.isSelected
    set(array) {
      dialogUI.myArrayCheckBox.isSelected = array
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

  @VisibleForTesting
  enum class Type(val display: String,
                  val attrValue: String?,
                  val isCustom: Boolean = false,
                  val supportsNullable: Boolean = false,
                  val supportsArray: Boolean = true) {
    INFERRED("<inferred type>", null),
    INTEGER("Integer", "integer"),
    FLOAT("Float", "float"),
    LONG("Long", "long"),
    BOOLEAN("Boolean", "boolean"),
    STRING("String", "string", supportsNullable = true),
    REFERENCE("Resource Reference", "reference", supportsArray = false),
    CUSTOM_PARCELABLE("Custom Parcelable...", "custom_parcelable", isCustom = true, supportsNullable = true),
    CUSTOM_SERIALIZABLE("Custom Serializable...", "custom_serializable", isCustom = true, supportsNullable = true),
    CUSTOM_ENUM("Custom Enum...", "custom_enum", isCustom = true,  supportsArray = false);

    override fun toString(): String {
      return display
    }
  }

  init {
    init()
    Type.values().forEach { dialogUI.myTypeComboBox.addItem(it) }

    dialogUI.myTypeComboBox.setRenderer(object : ListCellRendererWrapper<Type>() {
      override fun customize(list: JList<*>, value: Type, index: Int, isSelected: Boolean, hasFocus: Boolean) {
        if (index == -1 && value.isCustom && selectedType == value) {
          setText(type)
        }
        else {
          setText(value.display)
        }
        setBackground(UIUtil.getListBackground(isSelected, true))
        setForeground(UIUtil.getListForeground(isSelected, true))
      }
    })

    dialogUI.myTypeComboBox.isEditable = false

    dialogUI.myDefaultValueComboBox.model = defaultValueComboModel

    if (existingComponent != null) {
      name = existingComponent.argumentName
      val nullable = existingComponent.nullable
      isNullable = nullable != null && nullable
      val typeStr = existingComponent.typeAttr
      type = typeStr
      if (typeStr == null) {
        dialogUI.myTypeComboBox.setSelectedItem(Type.INFERRED)
      }
      else {
        var found = false

        if (typeStr.endsWith("[]")) isArray = true
        val typeStrNoSuffix = typeStr.removeSuffix("[]")

        for (type in Type.values()) {
          if (typeStrNoSuffix == type.attrValue) {
            selectedType = type
            dialogUI.myTypeComboBox.selectedItem = selectedType
            found = true
            break
          }
        }
        if (!found) {
          selectedType = ClassUtil.findPsiClassByJVMName(psiManager, typeStrNoSuffix)?.let {
            when {
              it.isEnum -> Type.CUSTOM_ENUM
              it.isInheritor(parcelableClass, true) -> Type.CUSTOM_PARCELABLE
              it.isInheritor(serializableClass, true) -> Type.CUSTOM_SERIALIZABLE
              else -> null
            }
          } ?: Type.CUSTOM_PARCELABLE
          dialogUI.myTypeComboBox.selectedItem = selectedType
        }
      }
      updateUi()

      defaultValue = existingComponent.defaultValue
      myOKAction.putValue(Action.NAME, "Update")
      title = "Update Argument"
    }
    else {
      (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "textDefaultValue")
      myOKAction.putValue(Action.NAME, "Add")
      title = "Add Argument"
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

    dialogUI.myArrayCheckBox.addActionListener { event ->
      type = updateArgType(type)
      updateUi()
    }

    dialogUI.myNullableCheckBox.addActionListener { event ->
      updateUi()
    }
  }

  private fun updateArgType(argType: String?) = argType?.let { it.removeSuffix("[]") + if (isArray) "[]" else "" }

  private fun newTypeSelected() {
    val selectedItem = dialogUI.myTypeComboBox.selectedItem as? Type
    if (selectedItem != null) {
      if (selectedItem.isCustom) {
        val project = parent.model.project
        val superType = when (dialogUI.myTypeComboBox.selectedItem) {
          Type.CUSTOM_PARCELABLE -> parcelableClass
          Type.CUSTOM_SERIALIZABLE -> serializableClass
          // we're using a class filter in createInheritanceClassChooser below for the Enum case,
          // as during manual tests the behavior was inconsistent when using Enum supertype
          Type.CUSTOM_ENUM -> null
          else -> throw IllegalStateException("Can never happen.")
        }
        val current = type?.removeSuffix("[]")?.let { ClassUtil.findPsiClass(psiManager, it) }
        val chooser = kotlinTreeClassChooserFactory.createKotlinTreeClassChooser("Select Class", project, GlobalSearchScope.allScope(project), superType, current) {
              aClass -> if (superType == null) aClass.isEnum else true
          }
        chooser.showDialog()
        val selection = chooser.selected
        if (selection != null) {
          type = updateArgType(ClassUtil.getJVMClassName(selection))
          selectedType = selectedItem
        }
        else {
          dialogUI.myTypeComboBox.setSelectedItem(selectedType)
        }
      }
      else {
        type = updateArgType(selectedItem.attrValue)
        selectedType = selectedItem
      }
    }
    updateUi()
  }

  private fun updateUi() {
    val nullable = selectedType.supportsNullable || isArray
    dialogUI.myNullableCheckBox.isEnabled = nullable
    dialogUI.myNullableLabel.isEnabled = nullable
    if (!nullable) {
      dialogUI.myNullableCheckBox.isSelected = false
    }
    val supportsArray = selectedType.supportsArray
    dialogUI.myArrayCheckBox.isEnabled = supportsArray
    dialogUI.myArrayLabel.isEnabled = supportsArray
    if (!supportsArray) {
      dialogUI.myArrayCheckBox.isSelected = false
    }

    when {
      selectedType == Type.BOOLEAN && !isArray -> {
        (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "comboDefaultValue")
        defaultValueComboModel.update(Lists.newArrayList<String>(null, "true", "false"))
      }
      selectedType == Type.CUSTOM_ENUM && !isArray -> {
        (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "comboDefaultValue")
        val list = ClassUtil.findPsiClass(psiManager, type.orEmpty())
                     ?.fields
                     ?.filter { it is PsiEnumConstant }
                     ?.map { it.name }?.toMutableList<String?>()
                   ?: mutableListOf()
        list.add(null)
        if (isNullable) list.add("@null")
        defaultValueComboModel.update(list)
      }
      selectedType.isCustom || isArray -> {
        (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "comboDefaultValue")
        val list = mutableListOf<String?>(null)
        if (isNullable) list.add("@null")
        defaultValueComboModel.update(list)
      }
      else -> {
        dialogUI.myDefaultValueTextField.text = ""
        (dialogUI.myDefaultValuePanel.layout as CardLayout).show(dialogUI.myDefaultValuePanel, "textDefaultValue")
      }
    }
    dialogUI.myDefaultValueComboBox.isEnabled = defaultValueComboModel.size != 1
  }

  override fun createCenterPanel(): JComponent {
    dialogUI.myContentPanel.minimumSize = Dimension(320,200)
    return dialogUI.myContentPanel
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  public override fun doValidate(): ValidationInfo? {
    val name = name
    if (name.isNullOrEmpty()) {
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
    if (!newDefaultValue.isNullOrEmpty() && !isArray) {
      when (dialogUI.myTypeComboBox.selectedItem as Type) {
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
              resourceResolver.resolve(url, parent.tagDeprecated)
            }) ?: return ValidationInfo("Resource does not exist")
          }
        }
        Type.FLOAT -> {
          if (!FloatResources.parseFloatAttribute(newDefaultValue, FloatResources.TypedValue(), false)) {
            try {
              newDefaultValue.toFloat()
              return null
            }
            catch (e: NumberFormatException) {
              return ValidationInfo("Default value must be an integer")
            }
          }
        }
        Type.INFERRED, Type.BOOLEAN, Type.STRING, Type.CUSTOM_PARCELABLE, Type.CUSTOM_SERIALIZABLE, Type.CUSTOM_ENUM -> doNothing()
      }
    }
    return null
  }

  fun save() {
    WriteCommandAction.runWriteCommandAction(parent.model.project) {
      val realComponent = existingComponent ?: parent.createChild(TAG_ARGUMENT)
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
      if (!isArray && !newDefaultValue.isNullOrEmpty()
          && dialogUI.myTypeComboBox.selectedItem === Type.LONG && !newDefaultValue.endsWith("L")) {
        newDefaultValue += "L"
      }
      realComponent.setDefaultValueAndLog(newDefaultValue, NavEditorEvent.Source.PROPERTY_INSPECTOR)
    }
  }
}
