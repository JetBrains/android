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

import com.android.SdkConstants.FD_RES_XML
import com.android.SdkConstants.PreferenceTags.PREFERENCE_SCREEN
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.render
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.runBlocking

class LayoutlibSceneManagerTest : SceneTest() {

  private lateinit var myLayoutlibSceneManager: LayoutlibSceneManager

  override fun setUp() {
    // we register it manually here in the tests context, but in production it should be handled by
    // NlEditorProvider
    DesignerTypeRegistrar.register(PreferenceScreenFileType)
    super.setUp()
    myLayoutlibSceneManager = (myScene.designSurface as NlDesignSurface).sceneManagers.first()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testSceneModeWithPreferenceFile() {
    // Regression test for b/122673792
    val nlSurface = myScene.designSurface as NlDesignSurface

    whenever(nlSurface.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER)
    myLayoutlibSceneManager.updateSceneView()
    assertNotNull(myLayoutlibSceneManager.sceneView)
    assertNull(myLayoutlibSceneManager.secondarySceneView)

    whenever(nlSurface.screenViewProvider).thenReturn(NlScreenViewProvider.BLUEPRINT)
    myLayoutlibSceneManager.updateSceneView()
    assertNotNull(myLayoutlibSceneManager.sceneView)
    assertNull(myLayoutlibSceneManager.secondarySceneView)

    whenever(nlSurface.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER_AND_BLUEPRINT)
    myLayoutlibSceneManager.updateSceneView()
    assertNotNull(myLayoutlibSceneManager.sceneView)
    assertNotNull(myLayoutlibSceneManager.secondarySceneView)
  }

  fun testDoNotCacheSuccessfulRenderImage() = runBlocking {
    myLayoutlibSceneManager.sceneRenderConfiguration.cacheSuccessfulRenderImage = false
    myLayoutlibSceneManager.render()
    myLayoutlibSceneManager.sceneRenderConfiguration.forceReinflate()
    myLayoutlibSceneManager.renderResult!!.let {
      assertTrue(it.renderResult.isSuccess)
      assertTrue(it.renderedImage.isValid)
      assertEquals(768, it.rootViewDimensions.width)
      assertEquals(1280, it.rootViewDimensions.height)
    }

    // Break the XML, the next render will fail but will retain the image and dimensions
    WriteCommandAction.runWriteCommandAction(project) {
      val manager = PsiDocumentManager.getInstance(project)
      val document = manager.getDocument(myLayoutlibSceneManager.model.file)!!
      document.setText("<broken />")
      manager.commitAllDocuments()
    }
    myLayoutlibSceneManager.render()
    myLayoutlibSceneManager.renderResult!!.let {
      assertFalse("broken render should have failed", it.renderResult.isSuccess)
      assertFalse("image should not be valid after the failed rener", it.renderedImage.isValid)
    }
  }

  fun testCacheSuccessfulRenderImage() = runBlocking {
    myLayoutlibSceneManager.sceneRenderConfiguration.cacheSuccessfulRenderImage = true
    myLayoutlibSceneManager.render()
    myLayoutlibSceneManager.sceneRenderConfiguration.forceReinflate()
    myLayoutlibSceneManager.renderResult!!.let {
      assertTrue(it.renderResult.isSuccess)
      assertTrue(it.renderedImage.isValid)
      assertEquals(768, it.rootViewDimensions.width)
      assertEquals(1280, it.rootViewDimensions.height)
    }

    // Break the XML, the next render will fail but will retain the image and dimensions
    WriteCommandAction.runWriteCommandAction(project) {
      val manager = PsiDocumentManager.getInstance(project)
      val document = manager.getDocument(myLayoutlibSceneManager.model.file)!!
      document.setText("<broken />")
      manager.commitAllDocuments()
    }
    myLayoutlibSceneManager.render()
    myLayoutlibSceneManager.renderResult!!.let {
      assertFalse("broken render should have failed", it.renderResult.isSuccess)
      assertTrue(
        "image should be still valid because of a previous successful render",
        it.renderedImage.isValid,
      )
      assertEquals(768, it.rootViewDimensions.width)
      assertEquals(1280, it.rootViewDimensions.height)
    }
  }

  override fun createModel(): ModelBuilder {
    return model(
      FD_RES_XML,
      "preference.xml",
      component(PREFERENCE_SCREEN)
        .withBounds(0, 0, 1000, 1000)
        .matchParentWidth()
        .matchParentHeight(),
    )
  }
}
