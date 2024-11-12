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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Computable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selectedValueIs
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

open class AndroidWearConfigurationEditor<T : AndroidWearConfiguration>(
  private val project: Project,
  private val configuration: T,
) : SettingsEditor<T>() {

  protected val modulesComboBox = ModulesComboBox()

  val moduleSelector =
    object : ConfigurationModuleSelector(project, modulesComboBox) {
      override fun isModuleAccepted(module: Module?): Boolean {
        if (module == null || !super.isModuleAccepted(module)) {
          return false
        }
        return AndroidWearConfigurationEditorToken.isModuleAccepted(module)
      }
    }

  protected lateinit var wearComponentFqNameComboBox: ComboBox<String>
  private var installFlags: String = ""

  val scope = AndroidCoroutineScope(this)

  private val moduleListener: ActionListener = ActionListener {
    scope.launch(workerThread) {
      if (project.getProjectSystem().getSyncManager().isSyncInProgress()) {
        withContext(uiThread) {
          component?.parent?.parent?.apply {
            removeAll()
            layout = BorderLayout()
            add(
              JBPanelWithEmptyText()
                .withEmptyText(
                  AndroidBundle.message("android.run.configuration.synchronization.warning")
                )
            )
          }
        }
        return@launch
      }
      updateComponents()
    }
  }

  private suspend fun updateComponents() {
    val module = moduleSelector.module
    var availableComponents = emptySet<String>()
    if (module == null || DumbService.isDumb(project)) {
      wearComponentFqNameComboBox.model = DefaultComboBoxModel(emptyArray())
    } else {
      availableComponents = blockingContext {
        ApplicationManager.getApplication()
          .runReadAction(Computable { findAvailableComponents(module) })
      }
      wearComponentFqNameComboBox.model = DefaultComboBoxModel(availableComponents.toTypedArray())
    }
    // to explicitly fire action event
    wearComponentFqNameComboBox.item = availableComponents.firstOrNull()
  }

  init {
    modulesComboBox.addActionListener(moduleListener)
  }

  override fun resetEditorFrom(runConfiguration: T) {
    scope.launch { reset(runConfiguration) }
  }

  protected suspend fun reset(runConfiguration: T) {
    modulesComboBox.removeActionListener(moduleListener)
    moduleSelector.reset(runConfiguration)
    installFlags = runConfiguration.deployOptions.pmInstallFlags
    updateComponents()

    wearComponentFqNameComboBox.item = runConfiguration.componentLaunchOptions.componentName
    (component as DialogPanel).reset()
    modulesComboBox.addActionListener(moduleListener)
  }

  private fun getComponentSearchScope(module: Module) =
    AndroidWearConfigurationEditorToken.getComponentSearchScope(module)

  override fun applyEditorTo(runConfiguration: T) {
    (component as DialogPanel).apply()
    moduleSelector.applyTo(runConfiguration)
    runConfiguration.componentLaunchOptions.componentName = wearComponentFqNameComboBox.item
    runConfiguration.deployOptions.pmInstallFlags = installFlags
  }

  override fun createEditor() = panel {
    getModuleChooser()
    getComponentComboBox()
    getInstallFlagsTextField()
  }

  protected fun Panel.getInstallFlagsTextField() {
    row(AndroidBundle.message("android.run.configuration.wear.install.flags")) {
      textField().bindText(::installFlags).align(AlignX.FILL)
    }
  }

  protected fun Panel.getComponentComboBox() {
    row {
        label(configuration.componentLaunchOptions.userVisibleComponentTypeName + ":")
        wearComponentFqNameComboBox =
          comboBox(
              DefaultComboBoxModel(emptyArray<String>()),
              renderer =
                object : SimpleListCellRenderer<String>() {
                  override fun customize(
                    list: JList<out String>,
                    value: String?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                  ) {
                    text =
                      when {
                        value != null -> value
                        modulesComboBox.item == null ->
                          AndroidBundle.message("android.run.configuration.wear.module.not.chosen")
                        list.selectionModel.maxSelectionIndex == -1 ->
                          AndroidBundle.message(
                            "android.run.configuration.wear.component.not.found",
                            configuration.componentLaunchOptions.userVisibleComponentTypeName,
                          )
                        else ->
                          AndroidBundle.message(
                            "android.run.configuration.wear.component.not.chosen",
                            configuration.componentLaunchOptions.userVisibleComponentTypeName,
                          )
                      }
                    isEnabled = value != null
                  }
                },
            )
            .enabledIf(modulesComboBox.selectedValueIs(null).not())
            .align(AlignX.FILL)
            .applyToComponent {
              maximumSize = Dimension(400, maximumSize.height)
              setMinLength(400)
              addPropertyChangeListener("model") {
                this.isEnabled = (it.newValue as ComboBoxModel<*>).size > 0
              }
            }
            .component
      }
      .layout(RowLayout.LABEL_ALIGNED)
  }

  protected fun Panel.getModuleChooser() {
    row {
        label(AndroidBundle.message("android.run.configuration.module.label"))
        cell(modulesComboBox).align(AlignX.FILL).applyToComponent {
          maximumSize = Dimension(400, maximumSize.height)
        }
      }
      .layout(RowLayout.LABEL_ALIGNED)
  }

  private fun findAvailableComponents(module: Module): Set<String> {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val facade = JavaPsiFacade.getInstance(project)
    val projectAllScope = ProjectScope.getAllScope(project)
    val surfaceBaseClasses =
      configuration.componentLaunchOptions.componentBaseClassesFqNames.mapNotNull {
        facade.findClass(it, projectAllScope)
      }
    val resultScope = getComponentSearchScope(module)
    return surfaceBaseClasses
      .flatMap { baseClass ->
        ClassInheritorsSearch.search(baseClass, projectAllScope, true)
          .filtering {
            // TODO: filter base on manifest index.
            // We use this to filter based on the scope applicable for the module since, using the
            // scope returned by [getComponentSearchScope]
            // does not currently work when using the ClassInheritorSearch with Kotlin classes. The
            // inheritance index is broken and we are
            // forced to use the ProjectScope to ensure the parent classes are found by the
            // ClassInheritorsSearch.
            !(it.isInterface ||
              it.modifierList?.hasModifierProperty(PsiModifier.ABSTRACT) == true) &&
              PsiSearchScopeUtil.isInScope(resultScope, it)
          }
          .findAll()
          .mapNotNull { it.qualifiedName }
      }
      .distinct()
      .sorted()
      .toSet()
  }
}

interface AndroidWearConfigurationEditorToken<P : AndroidProjectSystem> : Token {
  fun isModuleAccepted(projectSystem: P, module: Module): Boolean = defaultIsModuleAccepted(module)

  fun getComponentSearchScope(projectSystem: P, module: Module): GlobalSearchScope

  companion object {
    val EP_NAME =
      ExtensionPointName<AndroidWearConfigurationEditorToken<AndroidProjectSystem>>(
        "com.android.tools.idea.run.configuration.editors.androidWearConfigurationEditorToken"
      )

    fun isModuleAccepted(module: Module): Boolean {
      val projectSystem = module.project.getProjectSystem()
      return when (val token = projectSystem.getTokenOrNull(EP_NAME)) {
        null -> defaultIsModuleAccepted(module)
        else -> token.isModuleAccepted(projectSystem, module)
      }
    }

    private fun defaultIsModuleAccepted(module: Module): Boolean {
      val facet = AndroidFacet.getInstance(module) ?: return false
      return facet.getModuleSystem().type == AndroidModuleSystem.Type.TYPE_APP
    }

    fun getComponentSearchScope(module: Module): GlobalSearchScope {
      val projectSystem = module.project.getProjectSystem()
      return when (val token = projectSystem.getTokenOrNull(EP_NAME)) {
        null -> module.getModuleSystem().getResolveScope(ScopeType.MAIN)
        else -> token.getComponentSearchScope(projectSystem, module)
      }
    }
  }
}
