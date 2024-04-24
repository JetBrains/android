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
package com.android.tools.idea.uibuilder.scene

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.DefaultHitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.TestDesignSurface
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil.model
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class TestSceneManager(
  model: NlModel,
  surface: DesignSurface<*>,
  sceneComponentProvider: SceneComponentHierarchyProvider? = null,
) : SceneManager(model, surface, sceneComponentProvider, null) {
  override fun doCreateSceneView(): SceneView = TestSceneView(100, 100, this)

  override fun getSceneScalingFactor(): Float = 1f

  override fun createTemporaryComponent(component: NlComponent): TemporarySceneComponent {
    throw UnsupportedOperationException()
  }

  override fun requestRenderAsync(): CompletableFuture<Void> =
    CompletableFuture.completedFuture(null)

  override fun requestLayoutAsync(animate: Boolean): CompletableFuture<Void> =
    CompletableFuture.completedFuture(null)

  override fun layout(animate: Boolean) {}

  override fun getSceneDecoratorFactory(): SceneDecoratorFactory =
    object : SceneDecoratorFactory() {
      override fun get(component: NlComponent): SceneDecorator = BASIC_DECORATOR
    }

  override fun getDefaultProperties():
    MutableMap<Any, MutableMap<ResourceReference, ResourceValue>> = mutableMapOf()

  override fun getDefaultStyles(): MutableMap<Any, ResourceReference> = mutableMapOf()
}

class SceneManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val edtRule = EdtRule()

  private fun SceneComponent.withBounds(x: Int, y: Int, width: Int, height: Int): SceneComponent {
    setPosition(x, y)
    setSize(width, height)

    return this
  }

  /**
   * Checks that when multiple top level components are provided, this is handled correctly by the
   * SceneManager.
   */
  @RunsInEdt
  @Test
  fun testMultipleRootHierarchyProvider() {
    val model =
      model(
          projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor("androidx.compose.ui.tooling.ComposeViewAdapter"),
        )
        .build()
    val surface = TestDesignSurface(projectRule.project, projectRule.fixture.testRootDisposable)
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model))
    val scene = surface.sceneManagers.first().scene
    val rootNlComponent = model.getRoot()
    val hitProvider = DefaultHitProvider()
    val sceneManager =
      TestSceneManager(
        model,
        surface,
        object : SceneManager.SceneComponentHierarchyProvider {
          override fun createHierarchy(
            manager: SceneManager,
            component: NlComponent,
          ): MutableList<SceneComponent> =
            mutableListOf(
              SceneComponent(scene, rootNlComponent, hitProvider).withBounds(20, 20, 20, 20),
              SceneComponent(scene, rootNlComponent, hitProvider).withBounds(20, 20, 20, 20),
              SceneComponent(scene, rootNlComponent, hitProvider).withBounds(20, 20, 20, 20),
              SceneComponent(scene, rootNlComponent, hitProvider).withBounds(20, 30, 60, 60),
            )

          override fun syncFromNlComponent(sceneComponent: SceneComponent) {}
        },
      )

    sceneManager.updateSceneView()
    sceneManager.update()
    assertEquals(4, sceneManager.scene.root!!.childCount)
    assertEquals(20, sceneManager.scene.root!!.drawX)
    assertEquals(20, sceneManager.scene.root!!.drawY)
    assertEquals(60, sceneManager.scene.root!!.drawWidth)
    assertEquals(70, sceneManager.scene.root!!.drawHeight)
    Disposer.dispose(sceneManager)
    Disposer.dispose(model)
  }

  @RunsInEdt
  @Test
  fun testDoNotRegisterDuplicatedListenerToResourceNotificationManager() {
    val mockedManager = projectRule.mockProjectService(ResourceNotificationManager::class.java)

    val model =
      model(projectRule, "layout", "layout.xml", ComponentDescriptor(SdkConstants.FRAME_LAYOUT))
        .build()
    val surface = TestDesignSurface(projectRule.project, projectRule.fixture.testRootDisposable)
    surface.addModelWithoutRender(model)
    val sceneManager = TestSceneManager(model, surface)
    sceneManager.updateSceneView()
    sceneManager.update()

    val source = Object()

    sceneManager.deactivate(source)
    verify(mockedManager, times(0)).removeListener(any(), any(), any(), any())

    sceneManager.activate(source)
    sceneManager.activate(source)
    sceneManager.deactivate(source)
    verify(mockedManager, times(1)).addListener(any(), any(), any(), any())
    verify(mockedManager, times(1)).removeListener(any(), any(), any(), any())

    Disposer.dispose(sceneManager)
    Disposer.dispose(model)
  }
}
