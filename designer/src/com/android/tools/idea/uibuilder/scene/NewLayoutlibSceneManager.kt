/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.resources.Density
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import java.util.concurrent.Executor

private val DECORATOR_FACTORY: SceneDecoratorFactory = NlSceneDecoratorFactory()

/**
 * [SceneManager] that creates a Scene from an NlModel representing a layout using layoutlib.
 *
 * @param model the [NlModel] to be rendered by this [NewLayoutlibSceneManager].
 * @param designSurface the [DesignSurface] used to present the result of the renders.
 * @param renderTaskDisposerExecutor the [Executor] to be used for running the slow [dispose] calls.
 * @param sceneComponentProvider the [SceneComponentHierarchyProvider] providing the mapping from
 *   [NlComponent] to [SceneComponent]s.
 * @param layoutScannerConfig the [LayoutScannerConfiguration] for layout validation from
 *   Accessibility Testing Framework.
 */
abstract class NewLayoutlibSceneManager(
  model: NlModel,
  designSurface: DesignSurface<*>,
  renderTaskDisposerExecutor: Executor,
  sceneComponentProvider: SceneComponentHierarchyProvider,
  layoutScannerConfig: LayoutScannerConfiguration,
) : SceneManager(model, designSurface, sceneComponentProvider) {
  override val designSurface: NlDesignSurface
    get() = super.designSurface as NlDesignSurface

  override val sceneDecoratorFactory: SceneDecoratorFactory = DECORATOR_FACTORY

  /**
   * In the layout editor, Scene uses [AndroidDpCoordinate]s whereas rendering is done in (zoomed
   * and offset) [AndroidCoordinate]s. The scaling factor between them is the ratio of the screen
   * density to the standard density (160).
   */
  override val sceneScalingFactor: Float
    get() = model.configuration.density.dpiValue / Density.DEFAULT_DENSITY.toFloat()
}
