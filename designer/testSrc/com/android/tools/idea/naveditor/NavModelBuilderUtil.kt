// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor

import com.android.SdkConstants
import com.android.SdkConstants.*
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.TAG_ACTION
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.*
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.Dimension
import java.awt.Point
import java.util.function.BiConsumer
import java.util.function.Function

/**
 * Descriptors used for building navigation [com.android.tools.idea.common.model.NlModel]s
 */
object NavModelBuilderUtil {
  private val TAG_FRAGMENT = "fragment"
  private val TAG_NAVIGATION = "navigation"

  fun model(name: String, facet: AndroidFacet, fixture: JavaCodeInsightTestFixture, f: () -> ComponentDescriptor): ModelBuilder {
    val managerFactory = Function<SyncNlModel, SceneManager> { model ->
      val surface = model.surface as NavDesignSurface

      try {
        createIfNecessary(facet)
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
      `when`(surface.schema).thenReturn(NavigationSchema.get(facet))
      `when`<NlComponent>(surface.currentNavigation).then { model.components[0] }
      `when`(surface.extentSize).thenReturn(Dimension(500, 500))
      `when`(surface.scrollPosition).thenReturn(Point(0, 0))

      val selectionModel = mock(SelectionModel::class.java)
      `when`<ImmutableList<NlComponent>>(selectionModel.selection).thenReturn(ImmutableList.of<NlComponent>())

      val sceneView = mock(SceneView::class.java)
      `when`<NlModel>(sceneView.model).thenReturn(model)
      `when`<Configuration>(sceneView.configuration).thenReturn(model.configuration)
      `when`(sceneView.selectionModel).thenReturn(selectionModel)
      `when`<DesignSurface>(sceneView.surface).thenReturn(surface)

      `when`<SceneView>(surface.currentSceneView).thenReturn(sceneView)

      NavSceneManager(model, surface)
    }

    return ModelBuilder(facet, fixture, name, f(), managerFactory,
        BiConsumer<NlModel, NlModel> { model, newModel -> NavSceneManager.updateHierarchy(model, newModel) }, "navigation",
        NavDesignSurface::class.java)
  }

  fun navigation(id: String? = null, label: String? = null, startDestination: String? = null,
                 f: NavigationComponentDescriptor.() -> Unit = {}): NavigationComponentDescriptor {
    val descriptor = NavigationComponentDescriptor(id, startDestination, label)
    descriptor.apply(f)
    return descriptor
  }

  class NavigationComponentDescriptor(id: String?, startDestination: String?, label: String?) : NavComponentDescriptor(TAG_NAVIGATION) {

    init {
      id?.let { id("@+id/" + it) }
      startDestination?.let { withAttribute(AUTO_URI, ATTR_START_DESTINATION, "@id/" + it) }
      label?.let { withAttribute(ANDROID_URI, ATTR_LABEL, it) }
    }

    fun fragment(id: String,
                 layout: String? = null,
                 name: String? = null,
                 label: String? = null,
                 f: FragmentComponentDescriptor.() -> Unit = {}) {
      val fragment = FragmentComponentDescriptor(id, layout, name, label)
      addChild(fragment, null)
      fragment.apply(f)
    }

    fun activity(id: String, f: ActivityComponentDescriptor.() -> Unit = {}) {
      val activity = ActivityComponentDescriptor(id)
      addChild(activity, null)
      activity.apply(f)
    }

    fun include(graphId: String) {
      val include = IncludeComponentDescriptor(graphId)
      addChild(include, null)
    }

    fun navigation(id: String, startDestination: String? = null, label: String? = null, f: NavigationComponentDescriptor.() -> Unit = {}) {
      val navigation = NavigationComponentDescriptor(id, startDestination, label)
      addChild(navigation, null)
      navigation.apply(f)
    }

    fun action(id: String, destination: String? = null) {
      addChild(ActionComponentDescriptor(id, destination), null)
    }

    fun deeplink(uri: String) {
      addChild(DeepLinkComponentDescriptor(uri), null)
    }

    fun argument(name: String, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, value), null)
    }
  }

  class FragmentComponentDescriptor(id: String, layout: String?, name: String?, label: String?)
    : NavComponentDescriptor(TAG_FRAGMENT) {
    init {
      id("@+id/" + id)
      layout?.let { withAttribute(TOOLS_URI, ATTR_LAYOUT, "@layout/" + it) }
      name?.let { withAttribute(ANDROID_URI, ATTR_NAME, it) }
      label?.let { withAttribute(ANDROID_URI, ATTR_LABEL, it) }
    }

    fun action(id: String, destination: String? = null, popUpTo: String? = null, inclusive: Boolean = false, f: ActionComponentDescriptor.() -> Unit = {}) {
      val action = ActionComponentDescriptor(id, destination, popUpTo, inclusive)
      addChild(action, null)
      action.apply(f)
    }

    fun deeplink(uri: String) {
      addChild(DeepLinkComponentDescriptor(uri), null)
    }

    fun argument(name: String, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, value), null)
    }
  }

  class ActionComponentDescriptor(id: String, destination: String?, popUpTo: String? = null, popUpToInclusive: Boolean = false)
    : NavComponentDescriptor(TAG_ACTION) {
    init {
      id("@+id/" + id)
      destination?.let { withAttribute(AUTO_URI, ATTR_DESTINATION, "@id/" + it) }
      popUpTo?.let { withAttribute(AUTO_URI, ATTR_POP_UP_TO, "@id/" + it) }
      if (popUpToInclusive) {
        withAttribute(AUTO_URI, ATTR_POP_UP_TO_INCLUSIVE, "true")
      }
    }

    fun argument(name: String, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, value), null)
    }
  }

  class ActivityComponentDescriptor(id: String) : NavComponentDescriptor(TAG_ACTIVITY) {
    init {
      id("@+id/" + id)
    }

    fun deeplink(uri: String) {
      addChild(DeepLinkComponentDescriptor(uri), null)
    }

    fun argument(name: String, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, value), null)
    }
  }

  class IncludeComponentDescriptor(graphId: String) : NavComponentDescriptor(TAG_INCLUDE) {
    init {
      withAttribute(AUTO_URI, ATTR_GRAPH, NAVIGATION_PREFIX + graphId)
    }
  }

  class DeepLinkComponentDescriptor(uri: String) : NavComponentDescriptor(TAG_DEEPLINK) {
    init {
      withAttribute(AUTO_URI, "uri", uri)
    }
  }

  class ArgumentComponentDescriptor(name: String, value: String?) : NavComponentDescriptor(TAG_ARGUMENT) {
    init {
      withAttribute(ANDROID_URI, SdkConstants.ATTR_NAME, name)
      value?.let { withAttribute(ANDROID_URI, NavigationSchema.ATTR_DEFAULT_VALUE, it) }
    }
  }

  open class NavComponentDescriptor(tagName: String) : ComponentDescriptor(tagName)
}
