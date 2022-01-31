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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.projectsystem.getAndroidModulesForDisplay
import com.android.tools.idea.run.PreferGradleMake
import com.android.tools.idea.run.configuration.editors.AndroidWearConfigurationEditor
import com.android.tools.idea.run.editor.AndroidDebuggerContext
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

abstract class AndroidWearConfiguration(project: Project, factory: ConfigurationFactory) :
  ModuleBasedConfiguration<JavaRunConfigurationModule, Element>(JavaRunConfigurationModule(project, false), factory),
  RunConfigurationWithSuppressedDefaultRunAction, RunConfigurationWithSuppressedDefaultDebugAction, PreferGradleMake, ComponentSpecificConfiguration, RunConfigurationWithAndroidConfigurationExecutorBase {
  var componentName: String? = null
  var installFlags = ""

  abstract val userVisibleComponentTypeName: String
  abstract val componentBaseClassesFqNames: Array<String>
  val androidDebuggerContext: AndroidDebuggerContext = AndroidDebuggerContext(AndroidJavaDebugger.ID)

  override fun getConfigurationEditor(): AndroidWearConfigurationEditor<*> = AndroidWearConfigurationEditor(project, this)
  override fun checkConfiguration() {
    configurationModule.checkForWarning()
    // If module is null `configurationModule.checkForWarning()` will throw an error
    val module = configurationModule.module!!
    AndroidFacet.getInstance(module) ?: throw RuntimeConfigurationError(AndroidBundle.message("no.facet.error", module.name))
    componentName ?: throw RuntimeConfigurationError("$userVisibleComponentTypeName is not chosen")
  }

  final override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
    return AndroidRunProfileStateAdapter(getExecutor(environment))
  }

  override fun writeExternal(element: Element) {
    super<ModuleBasedConfiguration>.writeExternal(element)
    XmlSerializer.serializeInto(this, element)
  }

  override fun readExternal(element: Element) {
    super<ModuleBasedConfiguration>.readExternal(element)
    XmlSerializer.deserializeInto(this, element)
  }

  override fun getValidModules() = project.getAndroidModulesForDisplay()

  override val module: Module?
    get() = configurationModule.module
}