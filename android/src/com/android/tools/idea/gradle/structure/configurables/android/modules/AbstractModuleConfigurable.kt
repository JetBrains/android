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
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import javax.swing.JComponent

/**
 * A base class for configurables representing a specific perspective of [PsAndroidModule] configuration.
 *
 * Implementations should provide their own UI by overriding [createPanel] method.
 */
abstract class AbstractModuleConfigurable<out PanelT>(
    val context: PsContext,
    module: PsAndroidModule
) : BaseNamedConfigurable<PsModule>(module)
    where PanelT : JComponent,
          PanelT : Disposable,
          PanelT : Place.Navigator {

  private val lazyPanel = lazy(mode = LazyThreadSafetyMode.NONE) { createPanel(module).apply { setHistory(history) } }
  private val modulePanel by lazyPanel

  protected abstract fun createPanel(module: PsAndroidModule): PanelT

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = modulePanel.navigateTo(place, requestFocus)
  override fun queryPlace(place: Place) = modulePanel.queryPlace(place)
  override fun createOptionsPanel(): JComponent = modulePanel

  override fun setHistory(history: History?) {
    super.setHistory(history)
    // Do not force-initialize the panel.
    if (lazyPanel.isInitialized()) {
      modulePanel.setHistory(history)
    }
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    if (lazyPanel.isInitialized()) {
      Disposer.dispose(modulePanel)
    }
  }
}