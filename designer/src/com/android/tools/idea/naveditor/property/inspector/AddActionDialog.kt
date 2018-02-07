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

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.model.*
import com.android.tools.idea.naveditor.property.editors.getAnimatorsPopupContent
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ListCellRendererWrapper
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_EXIT_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.FRAGMENT
import org.jetbrains.android.resourceManagers.LocalResourceManager
import java.awt.Font
import java.awt.event.ItemEvent
import javax.swing.Action
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList

/**
 * Create a new action for the given component
 */
// Open for testing only
open class AddActionDialog(
  defaultsType: Defaults,
  private val existingAction: NlComponent?,
  private val parent: NlComponent,
  resourceResolver: ResourceResolver?
) : DialogWrapper(false) {

  private var previousPopTo: NlComponent? = null
  private var previousInclusive: Boolean = false
  @VisibleForTesting
  val dialog = AddActionDialogUI()

  // Open for testing
  open val source: NlComponent
    get() = dialog.myFromComboBox.selectedItem as NlComponent

  // Open for testing
  open val destination: NlComponent?
    get() {
      val item = dialog.myDestinationComboBox.selectedItem as DestinationListEntry?
      return item?.component
    }

  // Open for testing
  open val enterTransition: String?
    get() = (dialog.myEnterComboBox.selectedItem as ValueWithDisplayString).value

  // Open for testing
  open val exitTransition: String?
    get() = (dialog.myExitComboBox.selectedItem as ValueWithDisplayString).value

  // Open for testing
  open val popTo: String?
    get() {
      val component = dialog.myPopToComboBox.selectedItem as NlComponent?
      return if (component == null) null else stripPlus(component.getAttribute(ANDROID_URI, ATTR_ID))
    }

  // Open for testing
  open val isInclusive: Boolean
    get() = dialog.myInclusiveCheckBox.isSelected

  // Open for testing
  open val isSingleTop: Boolean
    get() = dialog.mySingleTopCheckBox.isSelected

  // Open for testing
  open val isDocument: Boolean
    get() = dialog.myDocumentCheckBox.isSelected

  // Open for testing
  open val isClearTask: Boolean
    get() = dialog.myClearTaskCheckBox.isSelected

  enum class Defaults {
    NORMAL, RETURN_TO_SOURCE, GLOBAL
  }

  init {
    val model = parent.model
    setUpComponents(model, resourceResolver)

    dialog.myFromComboBox.addItem(parent)

    if (existingAction != null) {
      setupFromExisting()
    } else {
      setDefaults(defaultsType)
    }

    init()

    title = if (existingAction == null) {
      myOKAction.putValue(Action.NAME, "Add")
      "Add Action"
    } else {
      myOKAction.putValue(Action.NAME, "Update")
      "Update Action"
    }
  }

  final override fun init() {
    super.init()
  }

  private fun setDefaults(type: Defaults) {
    dialog.myDestinationComboBox.addItem(null)
    populateDestinations()
    if (type == Defaults.GLOBAL) {
      dialog.myFromComboBox.addItem(parent.parent)
      dialog.myFromComboBox.selectedIndex = dialog.myFromComboBox.itemCount - 1
      selectItem(dialog.myDestinationComboBox, { it.component }, parent)
    } else if (type == Defaults.RETURN_TO_SOURCE) {
      dialog.myPopToComboBox.selectedItem = parent
      dialog.myInclusiveCheckBox.isSelected = true
      selectItem(dialog.myDestinationComboBox, { entry -> entry.isReturnToSource }, true)
    }
  }

  private fun populateDestinations() {
    val byParent = parent.visibleDestinations
      .filter { component -> component.parent != null }
      .groupBy { it.parent }
    // Add parent and siblings
    if (!parent.isRoot) {
      dialog.myDestinationComboBox.addItem(DestinationListEntry(parent.parent))
      byParent[parent.parent]?.forEach { c ->
        dialog.myDestinationComboBox
          .addItem(DestinationListEntry(c))
      }
    } else {
      // If this is the root, we need to explicitly add it.
      dialog.myDestinationComboBox.addItem(DestinationListEntry(parent))
    }
    // Add return to source
    dialog.myDestinationComboBox.addItem(RETURN_TO_SOURCE)
    // Add children if we're a nav
    if (parent.isNavigation && byParent.containsKey(parent)) {
      dialog.myDestinationComboBox.addItem(SEPARATOR)
      byParent[parent]?.forEach { c -> dialog.myDestinationComboBox.addItem(DestinationListEntry(c)) }
    }
    // Add siblings of ancestors
    if (byParent.keys.stream().anyMatch { c -> c !== parent && c !== parent.parent }) {
      dialog.myDestinationComboBox.addItem(SEPARATOR)
      for (nav in byParent.keys) {
        if (nav === parent || nav === parent.parent) {
          continue
        }
        dialog.myDestinationComboBox.addItem(DestinationListEntry.Parent(nav))
        for (child in byParent[nav]!!) {
          if (!byParent.containsKey(child)) {
            dialog.myDestinationComboBox.addItem(DestinationListEntry(child))
          }
        }
      }
    }
  }

  private fun populatePopTo() {
    dialog.myPopToComboBox.addItem(null)
    val navs = parent.model.flattenComponents()
      .filter { c -> c.isNavigation }
    for (nav in navs) {
      dialog.myPopToComboBox.addItem(nav)
      for (component in nav.children) {
        if (component.isDestination && !component.isNavigation) {
          dialog.myPopToComboBox.addItem(component)
        }
      }
    }
  }

  private fun setupFromExisting() {
    if (existingAction == null) {
      return
    }

    dialog.myFromComboBox.addItem(existingAction.parent)

    if (!existingAction.parent!!.isRoot) {
      dialog.myFromComboBox.addItem(existingAction.parent)
    }

    val destination = existingAction.actionDestinationId
    if (destination != null) {

      dialog.myDestinationComboBox.addItem(
          DestinationListEntry(existingAction.parent!!.findVisibleDestination(destination))
      )
      dialog.myDestinationComboBox.selectedIndex = 0
    }
    dialog.myDestinationComboBox.isEnabled = false

    selectItem(dialog.myPopToComboBox, { it.getAttribute(ANDROID_URI, ATTR_ID) }, NavigationSchema.ATTR_POP_UP_TO, AUTO_URI, existingAction)
    dialog.myInclusiveCheckBox.isSelected = existingAction.inclusive
    selectItem(dialog.myEnterComboBox, { it.value }, ATTR_ENTER_ANIM, AUTO_URI, existingAction)
    selectItem(dialog.myExitComboBox, { it.value }, ATTR_EXIT_ANIM, AUTO_URI, existingAction)
    dialog.mySingleTopCheckBox.isSelected = existingAction.singleTop
    dialog.myDocumentCheckBox.isSelected = existingAction.document
    dialog.myClearTaskCheckBox.isSelected = existingAction.clearTask
  }

  private fun <T, U> selectItem(
    comboBox: JComboBox<T>,
    valueGetter: (T) -> U,
    targetValue: U?
  ) {
    for (i in 0 until comboBox.itemCount) {
      val item = comboBox.getItemAt(i)
      val value = if (item == null) null else valueGetter(item)
      if (targetValue == value) {
        comboBox.selectedIndex = i
        return
      }
    }
  }

  private fun <T> selectItem(
    comboBox: JComboBox<T>,
    valueGetter: (T) -> String?,
    attrName: String,
    namespace: String?,
    component: NlComponent
  ) {
    var targetValue = component.getAttribute(namespace, attrName)
    targetValue = stripPlus(targetValue)
    selectItem(comboBox, { c -> stripPlus(valueGetter(c)) }, targetValue)
  }

  private fun stripPlus(targetValue: String?): String? {
    var result = targetValue
    if (result != null) {
      if (result.startsWith("@+")) {
        result = "@" + result.substring(2)
      }
    }
    return result
  }


  private fun setUpComponents(
    model: NlModel,
    resourceResolver: ResourceResolver?
  ) {
    val sourceRenderer = object : ListCellRendererWrapper<NlComponent>() {
      override fun customize(list: JList<*>, value: NlComponent?, index: Int, selected: Boolean, hasFocus: Boolean) {
        if (value == null) {
          setText("None")
        } else {
          setText(value.getUiName(resourceResolver))
        }
      }
    }

    dialog.myFromComboBox.renderer = sourceRenderer
    dialog.myFromComboBox.isEnabled = false

    val destinationRenderer = object : ListCellRendererWrapper<DestinationListEntry>() {
      override fun customize(list: JList<*>, value: DestinationListEntry?, index: Int, selected: Boolean, hasFocus: Boolean) {
        when {
          value == null -> setText("None")
          value.isReturnToSource -> setText("↵ Source")
          value.isSeparator -> setSeparator()
          else -> {
            val component = value.component
            var text = component?.getUiName(resourceResolver)
            val valueParent = component!!.parent
            if (valueParent !== parent.parent && component !== parent.parent && valueParent !== parent) {
              if (value.isParent) {
                setFont(list.font.deriveFont(Font.BOLD))
              } else if (index != -1) {
                text = "  " + text
              }
            }
            if (component === parent) {
              text = "↻ " + text
            }
            if (component.parent == null) {
              text += " (Root)"
            }
            setText(text)
          }
        }
      }
    }

    dialog.myDestinationComboBox.renderer = destinationRenderer

    val resourceManager = LocalResourceManager.getInstance(model.module)
    dialog.myDestinationComboBox.addItemListener { event ->
      dialog.myEnterComboBox.removeAllItems()
      dialog.myExitComboBox.removeAllItems()
      dialog.myEnterComboBox.addItem(ValueWithDisplayString("None", null))
      dialog.myExitComboBox.addItem(ValueWithDisplayString("None", null))
      val component = (event.item as DestinationListEntry).component
      var isFragment = false
      if (component != null) {
        isFragment = component.destinationType == FRAGMENT
      }
      if (resourceManager != null) {
        getAnimatorsPopupContent(resourceManager, isFragment)
          .forEach { item ->
            dialog.myEnterComboBox.addItem(item)
            dialog.myExitComboBox.addItem(item)
          }
      }
    }

    dialog.myDestinationComboBox.addItemListener { event ->
      val item = event.item as DestinationListEntry?
      if (event.stateChange == ItemEvent.SELECTED || item == null) {
        if (item != null && item.isReturnToSource) {
          previousPopTo = dialog.myPopToComboBox.selectedItem as NlComponent
          previousInclusive = dialog.myInclusiveCheckBox.isSelected
          dialog.myPopToComboBox.selectedItem = parent
          dialog.myPopToComboBox.isEnabled = false
          dialog.myInclusiveCheckBox.isSelected = true
          dialog.myInclusiveCheckBox.isEnabled = false
        } else {
          if (!dialog.myPopToComboBox.isEnabled) {
            dialog.myPopToComboBox.selectedItem = previousPopTo
            dialog.myInclusiveCheckBox.isSelected = previousInclusive
            dialog.myPopToComboBox.isEnabled = true
            dialog.myInclusiveCheckBox.isEnabled = true
          }
        }
      }
    }

    dialog.myEnterComboBox.addItem(ValueWithDisplayString("None", null))
    dialog.myExitComboBox.addItem(ValueWithDisplayString("None", null))

    populatePopTo()
    dialog.myPopToComboBox.renderer = object : ListCellRendererWrapper<NlComponent>() {
      override fun customize(
        list: JList<*>,
        value: NlComponent?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
      ) {
        if (value == null) {
          setText("None")
        } else {
          var text = value.getUiName(resourceResolver)
          if (value.isNavigation) {
            setFont(list.font.deriveFont(Font.BOLD))
          } else if (index != -1) {
            text = "  " + text
          }
          if (value.parent == null) {
            text += " (Root)"
          }
          setText(text)
        }
      }
    }
  }

  override fun doValidate(): ValidationInfo? {

    return if (dialog.myDestinationComboBox.selectedItem == null && dialog.myPopToComboBox.selectedItem == null) {
      ValidationInfo("Destination must be set!", dialog.myDestinationComboBox)
    } else super.doValidate()
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  override fun createCenterPanel(): JComponent? {
    return dialog.myContentPanel
  }

  @VisibleForTesting
  fun writeUpdatedAction() {
    WriteCommandAction.runWriteCommandAction(null) {
      val realComponent = existingAction ?: source.createAction()
      realComponent.actionDestinationId = destination?.id
      realComponent.enterAnimation = enterTransition
      realComponent.exitAnimation = exitTransition
      realComponent.popUpTo = popTo
      realComponent.inclusive = isInclusive
      realComponent.singleTop = isSingleTop
      realComponent.document = isDocument
      realComponent.clearTask = isClearTask
    }
  }

  @VisibleForTesting
  open class DestinationListEntry(component: NlComponent?) {
    var component: NlComponent? = null

    open val isSeparator: Boolean
      get() = false

    open val isParent: Boolean
      get() = false

    open val isReturnToSource: Boolean
      get() = false

    internal class Parent(component: NlComponent?) : DestinationListEntry(component) {

      override val isParent: Boolean
        get() = true
    }

    init {
      this.component = component
    }
  }
}

private val SEPARATOR = object : AddActionDialog.DestinationListEntry(null) {
  override val isSeparator: Boolean
    get() = true
}

private val RETURN_TO_SOURCE = object : AddActionDialog.DestinationListEntry(null) {
  override val isReturnToSource: Boolean
    get() = true
}
