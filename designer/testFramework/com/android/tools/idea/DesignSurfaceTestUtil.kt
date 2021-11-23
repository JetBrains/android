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
package com.android.tools.idea

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.fixtures.ModelBuilder.TestActionManager
import com.android.tools.idea.common.model.DefaultSelectionModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.awt.Dimension
import java.util.function.Function
import javax.swing.JPanel

object DesignSurfaceTestUtil {

  @JvmStatic
  fun createMockSurface(disposableParent: Disposable,
                        surfaceClass: Class<out DesignSurface>,
                        interactionHandlerCreator: (DesignSurface) -> InteractionHandler): DesignSurface {
    val surface = Mockito.mock(surfaceClass)
    Disposer.register(disposableParent, surface)
    val listeners: MutableList<DesignSurfaceListener> = ArrayList()
    Mockito.`when`(surface.getData(ArgumentMatchers.any())).thenCallRealMethod()
    Mockito.`when`(surface.layeredPane).thenReturn(JPanel())
    Mockito.`when`(surface.interactionPane).thenReturn(JPanel())
    val selectionModel: SelectionModel = DefaultSelectionModel()
    Mockito.`when`(surface.selectionModel).thenReturn(selectionModel)
    Mockito.`when`(surface.size).thenReturn(Dimension(1000, 1000))
    Mockito.`when`(surface.scale).thenReturn(0.5)
    Mockito.`when`(surface.selectionAsTransferable).thenCallRealMethod()
    Mockito.`when`(surface.actionManager).thenReturn(TestActionManager(surface))
    Mockito.`when`(surface.interactionManager).thenReturn(InteractionManager(surface, interactionHandlerCreator(surface)))
    if (surface is NlDesignSurface) {
      Mockito.`when`<DesignerAnalyticsManager>(surface.analyticsManager).thenReturn(NlAnalyticsManager(surface))
    }
    Mockito.doAnswer { listeners.add(it.getArgument(0)) }
      .`when`(surface).addListener(ArgumentMatchers.any(DesignSurfaceListener::class.java))

    Mockito.doAnswer { listeners.remove(it.getArgument<Any>(0) as DesignSurfaceListener) }
      .`when`(surface).removeListener(ArgumentMatchers.any(DesignSurfaceListener::class.java))

    selectionModel.addListener { _, selection -> listeners.forEach { listener -> listener.componentSelectionChanged(surface, selection) } }
    return surface
  }

  /**
   * FIXME(b/194482298): Refactor and remove this function. We don't need to give [SyncNlModel] when creating [DesignSurface].
   */
  @JvmStatic
  fun createMockSurfaceWithModel(disposableParent: Disposable,
                                 project: Project,
                                 sceneManagerFactory: (DesignSurface, SyncNlModel) -> SceneManager,
                                 surfaceClass: Class<out DesignSurface>,
                                 interactionHandlerCreator: (DesignSurface) -> InteractionHandler,
                                 model: SyncNlModel): DesignSurface {
    val surface = createMockSurface(disposableParent, surfaceClass, interactionHandlerCreator)

    Mockito.`when`(surface.model).thenReturn(model)
    Mockito.`when`(surface.models).thenReturn(ImmutableList.of(model))
    Mockito.`when`(surface.configuration).thenReturn(model.configuration)
    Mockito.`when`(surface.configurations).thenReturn(ImmutableList.of(model.configuration))

    // TODO: NlDesignSurface should not be referenced from here.
    // TODO: Do we need a special version of ModelBuilder for Nele?
    if (surface is NlDesignSurface) {
      Mockito.`when`(surface.screenViewProvider).thenReturn(NlScreenViewProvider.BLUEPRINT)
      Mockito.`when`(surface.getActionHandlerProvider()).thenReturn(Function { NlDesignSurface.defaultActionHandlerProvider(it) })
    }

    val sceneManager: SceneManager = sceneManagerFactory(surface, model)
    Mockito.`when`(surface.sceneManager).thenReturn(sceneManager)
    Mockito.`when`(surface.sceneManagers).thenReturn(ImmutableList.of(sceneManager))
    Mockito.`when`(surface.getSceneViewAtOrPrimary(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenCallRealMethod()
    Mockito.`when`(surface.focusedSceneView).thenReturn(sceneManager.sceneView)
    val scene = sceneManager.scene
    sceneManager.update()
    Mockito.`when`(surface.scene).thenReturn(scene)
    Mockito.`when`(surface.project).thenReturn(project)
    Mockito.`when`(surface.layoutType).thenCallRealMethod()
    Mockito.`when`(surface.canZoomToFit()).thenReturn(true)
    return surface
  }
}
