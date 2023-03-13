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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.model.TypeFilter
import com.android.tools.idea.ui.resourcemanager.rendering.SlowResource.Companion.isSlowResource
import com.android.utils.usLocaleCapitalize
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Component
import java.awt.event.ItemEvent
import java.awt.event.MouseEvent
import javax.swing.GroupLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.PopupMenuEvent

private const val SEARCH_FIELD_LABEL = "Search resources by name"
private const val ADD_BUTTON_LABEL = "Add resources to the module"
private const val FILTERS_BUTTON_LABEL = "Filter displayed resources"
private const val MODULE_PREFIX = "Module: "

private val MIN_FIELD_SIZE = JBUI.scale(40)
private val PREF_FIELD_SIZE = JBUI.scale(125)
private val MAX_FIELD_SIZE = JBUI.scale(150)
private val BUTTON_SIZE = JBUI.size(20)
private val GAP_SIZE = JBUI.scale(10)
private val ACTION_BTN_SIZE get() = JBUI.scale(32)

/**
 * Toolbar displayed at the top of the resource explorer which allows users
 * to change the module and add resources.
 */
class ResourceExplorerToolbar private constructor(
  private val toolbarViewModel: ResourceExplorerToolbarViewModel,
  private val moduleSelectionCombo: ComboBox<String>)
  : JPanel(), DataProvider by toolbarViewModel {

  private val searchAction = createSearchField()
  private val refreshActionToolbar =
    ActionManager.getInstance().createActionToolbar(
      "ResourceExplorer",
      DefaultActionGroup(RefreshAction(toolbarViewModel)),
      true
    ).apply {
      targetComponent = this@ResourceExplorerToolbar
    }

  private val refreshActionComponent = refreshActionToolbar.component

  init {
    layout = GroupLayout(this)
    val groupLayout = layout as GroupLayout
    val addAction = action(AddAction(toolbarViewModel))
    val separator = com.android.tools.idea.ui.resourcemanager.widget.Separator()
    val filterAction = action(FilterAction(toolbarViewModel))

    val sequentialGroup = groupLayout.createSequentialGroup()
      .addFixedSizeComponent(addAction, true)
      .addComponent(refreshActionComponent, ACTION_BTN_SIZE, ACTION_BTN_SIZE, ACTION_BTN_SIZE)
      .addFixedSizeComponent(separator)
      .addComponent(moduleSelectionCombo, MIN_FIELD_SIZE, PREF_FIELD_SIZE, MAX_FIELD_SIZE)
      .addComponent(searchAction, MIN_FIELD_SIZE, PREF_FIELD_SIZE, Int.MAX_VALUE)
      .addFixedSizeComponent(filterAction)

    val verticalGroup = groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
      .addComponent(addAction)
      .addComponent(refreshActionComponent)
      .addComponent(separator)
      .addComponent(moduleSelectionCombo)
      .addComponent(searchAction)
      .addComponent(filterAction)

    groupLayout.setHorizontalGroup(sequentialGroup)
    groupLayout.setVerticalGroup(verticalGroup)

    border = JBUI.Borders.merge(JBUI.Borders.empty(4, 2), JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), true)
    toolbarViewModel.updateUICallback = this::update
    update() // Update current module right away.
  }

  private fun update() {
    moduleSelectionCombo.selectedItem = toolbarViewModel.currentModuleName
    refreshActionToolbar.updateActionsImmediately()
  }

  private fun createSearchField() = SearchTextField(true).apply {
    isFocusable = true
    toolTipText = SEARCH_FIELD_LABEL
    accessibleContext.accessibleName = SEARCH_FIELD_LABEL
    textEditor.columns = GAP_SIZE
    textEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        toolbarViewModel.searchString = e.document.getText(0, e.document.length)
      }
    })
  }

  companion object {
    /**
     * Returns a [ResourceExplorerToolbar].
     *
     * @param moduleComboEnabled Whether the module selection box UI should show as enabled.
     */
    @JvmStatic
    fun create(toolbarViewModel: ResourceExplorerToolbarViewModel, moduleComboEnabled: Boolean): ResourceExplorerToolbar {
      val moduleSelectionCombo = createModuleSelectionComboBox(toolbarViewModel, moduleComboEnabled)
      return ResourceExplorerToolbar(toolbarViewModel, moduleSelectionCombo)
    }
  }
}

/**
 * Button to add new resources
 */
private abstract class PopupAction internal constructor(val icon: Icon?, description: String)
  : AnAction(description, description, icon), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    var x = 0
    var y = 0
    val inputEvent = e.inputEvent
    if (inputEvent is MouseEvent) {
      x = 0
      y = inputEvent.component.height
    }

    showAddPopup(inputEvent!!.component, x, y)
  }

  private fun showAddPopup(component: Component, x: Int, y: Int) {
    ActionManager.getInstance()
      .createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, createAddPopupGroup())
      .component.show(component, x, y)
  }

  protected abstract fun createAddPopupGroup(): ActionGroup
}

private class AddAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : PopupAction(AllIcons.General.Add, ADD_BUTTON_LABEL) {
  override fun createAddPopupGroup() = DefaultActionGroup().apply {
    addAll(viewModel.addActions)
    val importersActions = viewModel.getImportersActions()
    if (importersActions.isNotEmpty()) {
      add(Separator())
      addAll(importersActions)
    }
  }
}

/**
 * Action to refresh the previews of a particular type of resources.
 */
private class RefreshAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : AnAction("Refresh previews", "Refresh previews for ${viewModel.resourceType.displayName}s", AllIcons.Actions.Refresh) {
  override fun actionPerformed(e: AnActionEvent) {
    // TODO: update tracking to support this action.
    viewModel.refreshResourcesPreviewsCallback()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (viewModel.resourceType.isSlowResource()) {
      e.presentation.text = templatePresentation.text
      e.presentation.description = templatePresentation.description
      e.presentation.isEnabled = true
    }
    else {
      val text = "${viewModel.resourceType.displayName}s refresh automatically"
      e.presentation.text = text // Used for tooltips
      e.presentation.description = text
      e.presentation.isEnabled = false
    }
  }
}

private class FilterAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : PopupAction(AllIcons.General.Filter, FILTERS_BUTTON_LABEL) {
  override fun createAddPopupGroup() = DefaultActionGroup().apply {
    add(ShowModuleDependenciesAction(viewModel))
    add(ShowLibrariesAction(viewModel))
    add(ShowFrameworkAction(viewModel))
    add(ShowThemeAttributesAction(viewModel))
    addRelatedTypeFilterActions(viewModel)
  }
}

private class ShowModuleDependenciesAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : ToggleAction("Show local dependencies") {
  override fun isSelected(e: AnActionEvent) = viewModel.isShowModuleDependencies
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    viewModel.isShowModuleDependencies = state
    ResourceManagerTracking.logShowLocalDependenciesToggle(viewModel.facet, state)
  }
}

private class ShowLibrariesAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : ToggleAction("Show libraries") {
  override fun isSelected(e: AnActionEvent) = viewModel.isShowLibraryDependencies
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    viewModel.isShowLibraryDependencies = state
    ResourceManagerTracking.logShowLibrariesToggle(viewModel.facet, state)
  }
}

private class ShowFrameworkAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : ToggleAction("Show android resources") {
  override fun isSelected(e: AnActionEvent) = viewModel.isShowFrameworkResources
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    viewModel.isShowFrameworkResources = state
    ResourceManagerTracking.logShowFrameworkToggle(viewModel.facet, state)
  }
}

private class ShowThemeAttributesAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : ToggleAction("Show theme attributes") {
  override fun isSelected(e: AnActionEvent) = viewModel.isShowThemeAttributes
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    viewModel.isShowThemeAttributes = state
    ResourceManagerTracking.logShowThemeAttributesToggle(viewModel.facet, state)
  }
}

/** Maximum number of [TypeFilter]s that can be displayed. Any additional will be collapsed on a different 'Other' menu. */
private const val FILTERS_DISPLAY_LIMIT = 7
/** Pattern to identify [String]s as XmlTags. Eg: 'animated-vector' */
private val TAG_NAME_PATTERN = Regex("([a-z]+-)+([a-z]+)")

/**
 * Action to toggle several [TypeFilter]s in the [ResourceExplorerToolbarViewModel.typeFiltersModel].
 *
 * @param displayName The name by which the [typeFilters] are grouped. Also used for the action's name.
 * @param typeFilters Related [TypeFilter]s, they will all be toggled together.
 */
private class TypeFilterAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel,
                                                    displayName: String,
                                                    private val typeFilters: List<TypeFilter>)
  : ToggleAction(fixFilterDisplayNameForActionText(displayName),
                 "Filter ${viewModel.resourceType.displayName}s by File extension or Xml root tag.",
                 null) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return typeFilters.any { viewModel.typeFiltersModel.isEnabled(viewModel.resourceType, it) }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      ResourceManagerTracking.logTypeFilterEnabled(viewModel.facet, viewModel.resourceType)
    }
    typeFilters.forEach { typeFilter ->
      viewModel.typeFiltersModel.setEnabled(viewModel.resourceType, typeFilter, state)
    }
  }
}

/**
 * Action that clears all [TypeFilter]s under the same Resource Type in the [viewModel].
 */
private class ResetTypeFiltersAction internal constructor(val viewModel: ResourceExplorerToolbarViewModel)
  : AnAction("Clear ${viewModel.resourceType.displayName} Filters",
             "Clear enabled filters for ${viewModel.resourceType.displayName}s.",
             StudioIcons.Common.CLOSE) {
  override fun actionPerformed(e: AnActionEvent) {
    viewModel.typeFiltersModel.clearAll(viewModel.resourceType)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = viewModel.typeFiltersModel.getActiveFilters(viewModel.resourceType).isNotEmpty()
  }
}

/**
 * Adjust the given [displayName] from [TypeFilter] to match the desired format for [AnAction] text. Particularly, XmlTags should be
 * converted to Title Case.
 */
private fun fixFilterDisplayNameForActionText(displayName: String): String {
  return if (displayName.matches(TAG_NAME_PATTERN)) {
    // Fix the name for XmlTags. Eg: "animated-vector" should say "Animated Vector"
    displayName.split('-').joinToString(" ") { it.usLocaleCapitalize() }
  }
  else {
    // Anything else just make sure it's capitalized on the first letter.
    displayName.usLocaleCapitalize()
  }
}

private fun DefaultActionGroup.addRelatedTypeFilterActions(viewModel: ResourceExplorerToolbarViewModel) {
  // Group the supported filters by their display name. So that one menu-item applies to the related filters.
  val supportedFilters = viewModel.typeFiltersModel.getSupportedFilters(viewModel.resourceType).groupBy { it.displayName }
  if (StudioFlags.EXTENDED_TYPE_FILTERS.get() && supportedFilters.isNotEmpty()) {
    addSeparator()
    addSeparator("By ${viewModel.resourceType.displayName} Type")
    val visibleFilters = supportedFilters.entries.take(FILTERS_DISPLAY_LIMIT)
    val remainingFilters = supportedFilters.entries.drop(FILTERS_DISPLAY_LIMIT)
    addVisibleTypeFilters(viewModel, visibleFilters)
    addOtherMenuTypeFilters(viewModel, remainingFilters)
    add(ResetTypeFiltersAction(viewModel))
  }
}

private fun DefaultActionGroup.addVisibleTypeFilters(viewModel: ResourceExplorerToolbarViewModel,
                                                     groupedFilters: List<Map.Entry<String, List<TypeFilter>>>) {
  groupedFilters.forEach { filters ->
    add(TypeFilterAction(viewModel, filters.key, filters.value))
  }
}

private fun DefaultActionGroup.addOtherMenuTypeFilters(viewModel: ResourceExplorerToolbarViewModel,
                                                       groupedFilters: List<Map.Entry<String, List<TypeFilter>>>) {
  if (groupedFilters.isNotEmpty()) {
    val otherMenuGroup = DefaultActionGroup("Other", true)
    groupedFilters.forEach { filters ->
      otherMenuGroup.add(TypeFilterAction(viewModel, filters.key, filters.value))
    }
    add(otherMenuGroup)
  }
}

private fun action(addAction: AnAction) =
  ActionButton(addAction, addAction.templatePresentation, "", BUTTON_SIZE)

private fun GroupLayout.SequentialGroup.addFixedSizeComponent(
  jComponent: JComponent,
  baseline: Boolean = false
): GroupLayout.SequentialGroup {
  val width = jComponent.preferredSize.width
  this.addComponent(baseline, jComponent, width, width, width)
  return this
}


/**
 * Creates a combo box for the [ResourceExplorerToolbar], should contain available modules in the project. Selecting a module should
 * change the working facet in the [ResourceExplorerToolbarViewModel].
 *
 * @param moduleComboEnabled Sets the isEnabled UI property. I.e: Whether it's allowed for the user to select a different module.
 */
private fun createModuleSelectionComboBox(toolbarViewModel: ResourceExplorerToolbarViewModel, moduleComboEnabled: Boolean) =
  ComboBox<String>().apply {
    model = CollectionComboBoxModel(toolbarViewModel.getAvailableModules().toMutableList())
    isEnabled = moduleComboEnabled
    renderer = object : ColoredListCellRenderer<String>() {
      override fun customizeCellRenderer(
        list: JList<out String>,
        value: String,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
      ) {
        append(MODULE_PREFIX + value)
      }
    }

    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        val moduleName = event.itemSelectable.selectedObjects.first() as String
        toolbarViewModel.onModuleSelected(moduleName)
      }
    }

    addPopupMenuListener(object : PopupMenuListenerAdapter() {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
        (model as CollectionComboBoxModel).replaceAll(toolbarViewModel.getAvailableModules())
      }
    })
  }