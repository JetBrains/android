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
package com.android.tools.idea.gradle.structure.configurables.android.modules

import com.android.tools.idea.gradle.structure.configurables.BaseNamedConfigurable
import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurable
import com.android.tools.idea.gradle.structure.configurables.ContainerConfigurable
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.structure.configurables.ui.CrossModuleUiStateComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import javax.swing.JComponent

/**
 * A base class for configurables representing a specific perspective of [PsModule] configuration.
 *
 * Implementations should provide their own UI by overriding [createPanel] method.
 */
abstract class AbstractModuleConfigurable<ModuleT : PsModule, out PanelT>(
  val context: PsContext,
  private val perspectiveConfigurable: BasePerspectiveConfigurable,
  module: ModuleT
) : BaseNamedConfigurable<ModuleT>(module), ContainerConfigurable<PsModule>
  where PanelT : JComponent,
        PanelT : CrossModuleUiStateComponent,
        PanelT : Disposable,
        PanelT : Place.Navigator {


  private val lazyPanel = lazy(mode = LazyThreadSafetyMode.NONE) { createPanel().apply { setHistory(history) } }
  protected val modulePanel by lazyPanel
  protected var uiDisposed = false; private set

  protected abstract fun createPanel(): PanelT

  final override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback? = modulePanel.navigateTo(place, requestFocus)!!
  final override fun queryPlace(place: Place) = modulePanel.queryPlace(place)
  final override fun createOptionsPanel(): JComponent = modulePanel
  final override fun restoreUiState() = modulePanel.restoreUiState()

  final override fun setHistory(history: History?) {
    super.setHistory(history)
    // Do not force-initialize the panel.
    if (lazyPanel.isInitialized()) {
      modulePanel.setHistory(history)
    }
  }

  final override fun disposeUIResources() {
    super.disposeUIResources()
    if (lazyPanel.isInitialized()) {
      Disposer.dispose(modulePanel)
    }
    uiDisposed = true
  }

  override fun getChildrenModels(): Collection<PsModule> {
    val currentModuleGradlePath = getModule().gradlePath
    return if (currentModuleGradlePath == null) emptyList()
    else context.project.modules
      .filter { it.gradlePath != null }
      .filter { GradleUtil.isDirectChild(it.gradlePath, currentModuleGradlePath) }
  }

  override fun createChildConfigurable(model: PsModule): NamedConfigurable<out PsModule> =
    perspectiveConfigurable.createChildConfigurable(model)

  override fun onChange(disposable: Disposable, listener: () -> Unit) = context.project.modules.onChange(disposable, listener)

  override fun dispose() = Unit
}

