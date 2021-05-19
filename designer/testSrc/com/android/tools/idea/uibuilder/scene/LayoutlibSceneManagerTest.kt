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
package com.android.tools.idea.uibuilder.scene

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType
import org.mockito.Mockito

class LayoutlibSceneManagerTest: SceneTest() {

  override fun setUp() {
    // we register it manually here in the tests context, but in production it should be handled by NlEditorProvider
    DesignerTypeRegistrar.register(PreferenceScreenFileType)
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testSceneModeWithPreferenceFile() {
    // Regression test for b/122673792
    val nlSurface = myScene.designSurface as NlDesignSurface
    val sceneManager = nlSurface.sceneManager!!

    Mockito.`when`(nlSurface.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER)
    sceneManager.updateSceneView()
    assertNotNull(sceneManager.sceneView)
    assertNull(sceneManager.secondarySceneView)

    Mockito.`when`(nlSurface.screenViewProvider).thenReturn(NlScreenViewProvider.BLUEPRINT)
    sceneManager.updateSceneView()
    assertNotNull(sceneManager.sceneView)
    assertNull(sceneManager.secondarySceneView)

    Mockito.`when`(nlSurface.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER_AND_BLUEPRINT)
    sceneManager.updateSceneView()
    assertNotNull(sceneManager.sceneView)
    assertNotNull(sceneManager.secondarySceneView)
  }

  override fun createModel(): ModelBuilder {
    return model(SdkConstants.FD_RES_XML, "preference.xml",
                 component(SdkConstants.TAG_PREFERENCE_SCREEN)
                   .withBounds(0, 0, 1000, 1000)
                   .matchParentWidth()
                   .matchParentHeight()
    )
  }
}
