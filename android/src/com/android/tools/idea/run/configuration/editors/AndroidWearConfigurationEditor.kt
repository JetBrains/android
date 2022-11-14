/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.editors

import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.not
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selectedValueIs
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JList

open class AndroidWearConfigurationEditor<T : AndroidWearConfiguration>(private val project: Project, private val configuration: T) :
  SettingsEditor<T>() {

  private val modulesComboBox = ModulesComboBox()

  val moduleSelector = object : ConfigurationModuleSelector(project, modulesComboBox) {
    override fun isModuleAccepted(module: Module?): Boolean {
      if (module == null || !super.isModuleAccepted(module)) {
        return false
      }
      val facet = AndroidFacet.getInstance(module) ?: return false
      if (!module.isHolderModule()) return false
      return when (facet.getModuleSystem().type) {
        AndroidModuleSystem.Type.TYPE_NON_ANDROID -> false
        AndroidModuleSystem.Type.TYPE_APP -> true
        AndroidModuleSystem.Type.TYPE_LIBRARY -> false
        AndroidModuleSystem.Type.TYPE_TEST -> false
        AndroidModuleSystem.Type.TYPE_ATOM -> false
        AndroidModuleSystem.Type.TYPE_INSTANTAPP -> false
        AndroidModuleSystem.Type.TYPE_FEATURE -> false
        AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE -> false
      }
    }
  }

  private lateinit var wearComponentFqNameComboBox: ComboBox<String>
  protected var componentName: String? = null
    private set(value) {
      if (field != value) {
        field = value
        onComponentNameChanged(value)
      }
    }
  private var installFlags: String = ""

  init {
    Disposer.register(project, this)

    modulesComboBox.addActionListener {
      object : Task.Modal(project, AndroidBundle.message("android.run.configuration.loading"), true) {
        var availableComponents: Set<String> = emptySet()

        override fun run(indicator: ProgressIndicator) {
          val module = moduleSelector.module
          if (module == null || DumbService.isDumb(project)) {
            return
          }
          availableComponents = ApplicationManager.getApplication().runReadAction(Computable {findAvailableComponents(module)})
        }

        override fun onFinished() {
          wearComponentFqNameComboBox.model = DefaultComboBoxModel(availableComponents.toTypedArray())
          componentName = wearComponentFqNameComboBox.item
          if (project.getProjectSystem().getSyncManager().isSyncInProgress()) {
            component?.parent?.parent?.apply {
              removeAll()
              layout = BorderLayout()
              add(JBPanelWithEmptyText().withEmptyText("Can't edit configuration while Project is synchronizing"))
            }
          }
        }
      }.queue()
    }
  }

  open fun onComponentNameChanged(newComponent: String?) {}

  override fun resetEditorFrom(runConfiguration: T) {
    moduleSelector.reset(runConfiguration)
    val componentClass = moduleSelector.module?.let { getComponentSearchScope(it) }
    if (componentClass != null) {
      componentName = runConfiguration.componentLaunchOptions.componentName
    }
    installFlags = runConfiguration.deployOptions.pmInstallFlags
    (component as DialogPanel).reset()
  }

  private fun getComponentSearchScope(module: Module) = module.getMainModule().getModuleSystem().getResolveScope(ScopeType.MAIN)

  override fun applyEditorTo(runConfiguration: T) {
    (component as DialogPanel).apply()
    moduleSelector.applyTo(runConfiguration)
    runConfiguration.componentLaunchOptions.componentName = componentName
    runConfiguration.deployOptions.pmInstallFlags = installFlags
  }

  override fun createEditor() =
    panel {
      getModuleChooser()
      getComponentCompoBox()
      getInstallFlagsTextField()
    }

  protected fun LayoutBuilder.getInstallFlagsTextField() {
    row {
      label("Install Flags:")
      textField({ installFlags }, { installFlags = it }).constraints(CCFlags.growX, CCFlags.pushX)
    }
  }

  protected fun LayoutBuilder.getComponentCompoBox() {
    row {
      label(configuration.componentLaunchOptions.userVisibleComponentTypeName + ":")
      wearComponentFqNameComboBox = comboBox(
        DefaultComboBoxModel(emptyArray<String>()),
        { componentName },
        { componentName = it },
        renderer = object : SimpleListCellRenderer<String>() {
          override fun customize(list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean) {
            text = when {
              value != null -> value
              modulesComboBox.item == null -> "Module is not chosen"
              list.selectionModel.maxSelectionIndex == -1 -> "${configuration.componentLaunchOptions.userVisibleComponentTypeName} not found"
              else -> "${configuration.componentLaunchOptions.userVisibleComponentTypeName} is not chosen"
            }
          }
        })
        .enableIf(modulesComboBox.selectedValueIs(null).not())
        .constraints(CCFlags.growX, CCFlags.pushX)
        .applyToComponent {
          maximumSize = Dimension(400, maximumSize.height)
          setMinLength(400)
          addPropertyChangeListener("model") {
            this.isEnabled = (it.newValue as ComboBoxModel<*>).size > 0
          }
        }.component
    }
  }

  protected fun LayoutBuilder.getModuleChooser() {
    row {
      label(AndroidBundle.message("android.run.configuration.module.label"))
      component(modulesComboBox)
        .constraints(CCFlags.growX, CCFlags.pushX)
        .applyToComponent {
          maximumSize = Dimension(400, maximumSize.height)
        }
    }
  }

  private fun findAvailableComponents(module: Module): Set<String> {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val facade = JavaPsiFacade.getInstance(project)
    val surfaceBaseClasses = configuration.componentLaunchOptions.componentBaseClassesFqNames.mapNotNull {
      facade.findClass(it, ProjectScope.getAllScope(project))
    }
    return surfaceBaseClasses.flatMap { baseClass ->
      ClassInheritorsSearch.search(baseClass, getComponentSearchScope(module), true)
        .findAll()
        // TODO: filter base on manifest index.
        .filter { !(it.isInterface || it.modifierList?.hasModifierProperty(PsiModifier.ABSTRACT) == true) }
        .mapNotNull { it.qualifiedName }
    }.distinct()
      .sorted()
      .toSet()
  }
}