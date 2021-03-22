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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil.model
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture

class TestSceneManager(model: NlModel,
                       surface: DesignSurface,
                       sceneComponentProvider: SceneComponentHierarchyProvider? = null)
  : SceneManager(model, surface, false, sceneComponentProvider, null) {
  override fun doCreateSceneView(): SceneView = TestSceneView(100, 100)

  override fun getSceneScalingFactor(): Float = 1f

  override fun createTemporaryComponent(component: NlComponent): TemporarySceneComponent {
    throw UnsupportedOperationException()
  }

  override fun requestRender(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
  override fun requestLayout(animate: Boolean): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
  override fun layout(animate: Boolean) {}
  override fun getSceneDecoratorFactory(): SceneDecoratorFactory = object : SceneDecoratorFactory() {
    override fun get(component: NlComponent): SceneDecorator = BASIC_DECORATOR
  }

  override fun getDefaultProperties(): MutableMap<Any, MutableMap<ResourceReference, ResourceValue>> = mutableMapOf()
  override fun getDefaultStyles(): MutableMap<Any, ResourceReference> = mutableMapOf()
}

class ScenerManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  /**
   * Checks that when multiple top level components are provided, this is handled correctly by the SceneManager.
   */
  @RunsInEdt
  @Test
  fun testMultipleRootHierarchyProvider() {
    val model =
      model(projectRule, "layout", "layout.xml",
            ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER))
        .build()
    val surface = TestDesignSurface(projectRule.project, projectRule.fixture.testRootDisposable)
    surface.addModelWithoutRender(model)
    val scene = surface.sceneManagers.first().scene
    val rootNlComponent =  model.getRoot()
    val hitProvider = DefaultHitProvider()
    val sceneManager = TestSceneManager(model, surface, object: SceneManager.SceneComponentHierarchyProvider {
      override fun createHierarchy(manager: SceneManager, component: NlComponent): MutableList<SceneComponent> =
        mutableListOf(
          SceneComponent(scene, rootNlComponent, hitProvider),
          SceneComponent(scene, rootNlComponent, hitProvider),
          SceneComponent(scene, rootNlComponent, hitProvider),
          SceneComponent(scene, rootNlComponent, hitProvider)
        )

      override fun syncFromNlComponent(sceneComponent: SceneComponent) {}
    })

    sceneManager.updateSceneView()
    sceneManager.update()
    assertEquals(4, sceneManager.scene.root!!.childCount)
    Disposer.dispose(sceneManager)
    Disposer.dispose(model)
  }
}