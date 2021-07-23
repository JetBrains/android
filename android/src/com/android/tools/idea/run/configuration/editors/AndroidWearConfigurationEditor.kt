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

import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.not
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selectedValueIs
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel

class AndroidWearConfigurationEditor(private val project: Project, private val configuration: AndroidWearConfiguration) :
  SettingsEditor<AndroidWearConfiguration>() {

  private val modulesComboBox = ModulesComboBox()
  private val moduleSelector = object : ConfigurationModuleSelector(project, modulesComboBox) {
    override fun isModuleAccepted(module: Module?): Boolean {
      if (module == null || !super.isModuleAccepted(module)) {
        return false
      }
      val facet = AndroidFacet.getInstance(module) ?: return false
      return !facet.configuration.isLibraryProject
    }
  }

  private lateinit var wearComponentFqNameComboBox: CellBuilder<ComboBox<String>>
  private var availableComponents = listOf<String>()
  private var componentName: String? = null
  private var installFlags: String = ""

  init {
    modulesComboBox.addActionListener { event ->
      val module = moduleSelector.module
      val availableComponents = if (module == null) {
        emptyList()
      }
      else {
        val facade = JavaPsiFacade.getInstance(project)
        val surfaceBaseClasses = configuration.componentBaseClassesFqNames.mapNotNull {
          facade.findClass(it, ProjectScope.getAllScope(project))
        }
        surfaceBaseClasses.flatMap { baseClass ->
          ClassInheritorsSearch.search(baseClass, module.moduleScope, true).findAll().mapNotNull { it.qualifiedName }
        }
      }
      wearComponentFqNameComboBox.component.model = DefaultComboBoxModel(availableComponents.toTypedArray())
    }
  }

  override fun resetEditorFrom(runConfiguration: AndroidWearConfiguration) {
    moduleSelector.reset(runConfiguration)
    val componentClass = moduleSelector.findClass(runConfiguration.componentName)
    if (componentClass != null) {
      componentName = runConfiguration.componentName
    }
    installFlags = runConfiguration.installFlags
    (component as DialogPanel).reset()
  }

  override fun applyEditorTo(runConfiguration: AndroidWearConfiguration) {
    (component as DialogPanel).apply()
    moduleSelector.applyTo(runConfiguration)
    runConfiguration.componentName = componentName
    runConfiguration.installFlags = installFlags
  }

  override fun createEditor() =
    panel {
      row {
        label(AndroidBundle.message("android.run.configuration.module.label"))
        component(modulesComboBox)
          .constraints(CCFlags.growX, CCFlags.pushX)
          .applyToComponent {
            maximumSize = Dimension(400, maximumSize.height)
          }
      }
      row {
        label(configuration.userVisibleComponentTypeName)
        wearComponentFqNameComboBox = comboBox(
          DefaultComboBoxModel(emptyArray<String>()),
          { componentName },
          { componentName = it },
          renderer = SimpleListCellRenderer.create("Module is not chosen") { it.toString() })
          .enableIf(modulesComboBox.selectedValueIs(null).not())
          .constraints(CCFlags.growX, CCFlags.pushX)
          .applyToComponent {
            maximumSize = Dimension(400, maximumSize.height)
          }
      }
      row {
        label("Install Flags:")
        textField(::installFlags).constraints(CCFlags.growX, CCFlags.pushX)
      }
    }
}