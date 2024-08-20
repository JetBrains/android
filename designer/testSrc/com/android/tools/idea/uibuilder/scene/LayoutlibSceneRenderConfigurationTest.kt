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

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito

class LayoutlibSceneRenderConfigurationTest {
  private val sceneRenderConfiguration =
    LayoutlibSceneRenderConfiguration(
        Mockito.mock(NlModel::class.java),
        Mockito.mock(NlDesignSurface::class.java),
        LayoutScannerConfiguration.DISABLED,
      )
      .apply { needsInflation.set(false) }

  @Test
  fun testChangingShowDecorationsForcesReinflate() {
    val defaultShowDecorations = sceneRenderConfiguration.showDecorations
    assertThat(sceneRenderConfiguration.needsInflation.get()).isFalse()

    sceneRenderConfiguration.showDecorations = !defaultShowDecorations
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()

    sceneRenderConfiguration.showDecorations = defaultShowDecorations
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()
  }

  @Test
  fun testChangingUsePrivateClassLoaderForcesReinflate() {
    val defaultUsePrivateClassLoader = sceneRenderConfiguration.usePrivateClassLoader
    assertThat(sceneRenderConfiguration.needsInflation.get()).isFalse()

    sceneRenderConfiguration.usePrivateClassLoader = !defaultUsePrivateClassLoader
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()

    sceneRenderConfiguration.usePrivateClassLoader = defaultUsePrivateClassLoader
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()
  }

  @Test
  fun testSettingShrinkRenderingForcesReinflate() {
    val defaultShrinkRendering = sceneRenderConfiguration.useShrinkRendering
    assertThat(sceneRenderConfiguration.needsInflation.get()).isFalse()

    sceneRenderConfiguration.useShrinkRendering = !defaultShrinkRendering
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()

    sceneRenderConfiguration.useShrinkRendering = defaultShrinkRendering
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()
  }

  @Test
  fun testSettingTransparentRenderingForcesReinflate() {
    val defaultTransparentRendering = sceneRenderConfiguration.useTransparentRendering
    assertThat(sceneRenderConfiguration.needsInflation.get()).isFalse()

    sceneRenderConfiguration.useTransparentRendering = !defaultTransparentRendering
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()

    sceneRenderConfiguration.useTransparentRendering = defaultTransparentRendering
    assertThat(sceneRenderConfiguration.needsInflation.get()).isTrue()
  }
}
