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
import com.android.SdkConstants.TAG_ACTION
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.naveditor.model.NavComponentHelper
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.*
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.Dimension
import java.awt.Point
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

@DslMarker
annotation class NavTestDsl

/**
 * Descriptors used for building navigation [com.android.tools.idea.common.model.NlModel]s
 */
object NavModelBuilderUtil {
  private const val TAG_FRAGMENT = "fragment"
  private const val TAG_NAVIGATION = "navigation"

  fun model(name: String,
            facet: AndroidFacet,
            fixture: JavaCodeInsightTestFixture,
            f: () -> ComponentDescriptor,
            path: String = "navigation"): ModelBuilder {
    val managerFactory = Function<SyncNlModel, SceneManager> { model ->
      val surface = model.surface as NavDesignSurface

      try {
        createIfNecessary(facet.module)
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }

      `when`<NlComponent>(surface.currentNavigation).then { model.components[0] }
      `when`(surface.extentSize).thenReturn(Dimension(500, 500))
      `when`(surface.scrollPosition).thenAnswer { Point(0, 0) }

      val sceneView = mock(SceneView::class.java)
      `when`<NlModel>(sceneView.model).thenReturn(model)
      `when`<Configuration>(sceneView.configuration).thenReturn(model.configuration)
      val selectionModel = surface.selectionModel
      `when`(sceneView.selectionModel).thenReturn(selectionModel)
      `when`<DesignSurface>(sceneView.surface).thenReturn(surface)

      `when`<SceneView>(surface.focusedSceneView).thenReturn(sceneView)

      NavSceneManager(model, surface)
    }

    return ModelBuilder(facet, fixture, name, f(), managerFactory,
                        BiConsumer<NlModel, NlModel> { model, newModel -> NavSceneManager.updateHierarchy(model, newModel) }, path,
                        NavDesignSurface::class.java, Consumer<NlComponent> { NavComponentHelper.registerComponent(it) })
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
      startDestination?.let { withAttribute(AUTO_URI, ATTR_START_DESTINATION, "@id/$it") }
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

    fun custom(tag: String,
               id: String = tag,
               layout: String? = null,
               name: String? = null,
               label: String? = null,
               f: FragmentlikeComponentDescriptor.() -> Unit = {}) {
      val destination = FragmentlikeComponentDescriptor(tag, id, layout, name, label)
      addChild(destination, null)
      destination.apply(f)
    }

    fun activity(id: String,
                 layout: String? = null,
                 name: String? = null,
                 f: ActivityComponentDescriptor.() -> Unit = {}) {
      val activity = ActivityComponentDescriptor(id, layout, name)
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

    fun action(id: String, destination: String? = null, popUpTo: String? = null) {
      addChild(ActionComponentDescriptor(id, destination, popUpTo), null)
    }

    fun deeplink(uri: String, autoVerify: Boolean = false) {
      addChild(DeepLinkComponentDescriptor(uri, autoVerify), null)
    }

    fun argument(name: String, type: String? = null, nullable: Boolean? = null, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, type, nullable, value), null)
    }
  }

  class FragmentComponentDescriptor(id: String, layout: String?, name: String?, label: String?)
    : FragmentlikeComponentDescriptor(TAG_FRAGMENT, id, layout, name, label)

  open class FragmentlikeComponentDescriptor(tag: String, id: String, layout: String?, name: String?, label: String?)
    : NavComponentDescriptor(tag) {
    init {
      id("@+id/$id")
      layout?.let { withAttribute(TOOLS_URI, ATTR_LAYOUT, "@layout/$it") }
      name?.let { withAttribute(ANDROID_URI, ATTR_NAME, it) }
      label?.let { withAttribute(ANDROID_URI, ATTR_LABEL, it) }
    }

    fun action(id: String,
               destination: String? = null,
               popUpTo: String? = null,
               inclusive: Boolean = false,
               f: ActionComponentDescriptor.() -> Unit = {}) {
      val action = ActionComponentDescriptor(id, destination, popUpTo, inclusive)
      addChild(action, null)
      action.apply(f)
    }

    fun deeplink(uri: String, autoVerify: Boolean = false) {
      addChild(DeepLinkComponentDescriptor(uri, autoVerify), null)
    }

    fun argument(name: String, type: String? = null, nullable: Boolean? = null, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, type, nullable, value), null)
    }
  }

  class ActionComponentDescriptor(id: String, destination: String?, popUpTo: String? = null, popUpToInclusive: Boolean = false)
    : NavComponentDescriptor(TAG_ACTION) {
    init {
      id("@+id/" + id)
      destination?.let { withAttribute(AUTO_URI, ATTR_DESTINATION, "@id/$it") }
      popUpTo?.let { withAttribute(AUTO_URI, ATTR_POP_UP_TO, "@id/$it") }
      if (popUpToInclusive) {
        withAttribute(AUTO_URI, ATTR_POP_UP_TO_INCLUSIVE, "true")
      }
    }

    fun argument(name: String, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, null, null, value), null)
    }
  }

  class ActivityComponentDescriptor(id: String, layout: String?, name: String?) : NavComponentDescriptor(TAG_ACTIVITY) {
    init {
      id("@+id/$id")
      name?.let { withAttribute(ANDROID_URI, ATTR_NAME, it) }
      layout?.let { withAttribute(TOOLS_URI, ATTR_LAYOUT, layout) }
    }

    fun deeplink(uri: String, autoVerify: Boolean = false) {
      addChild(DeepLinkComponentDescriptor(uri, autoVerify), null)
    }

    fun argument(name: String, type: String? = null, nullable: Boolean? = null, value: String? = null) {
      addChild(ArgumentComponentDescriptor(name, type, nullable, value), null)
    }
  }

  class IncludeComponentDescriptor(graphId: String) : NavComponentDescriptor(TAG_INCLUDE) {
    init {
      withAttribute(AUTO_URI, ATTR_GRAPH, NAVIGATION_PREFIX + graphId)
    }
  }

  class DeepLinkComponentDescriptor(uri: String, autoVerify: Boolean) : NavComponentDescriptor(TAG_DEEP_LINK) {
    init {
      withAttribute(AUTO_URI, ATTR_URI, uri)
      if (autoVerify) {
        withAttribute(ANDROID_URI, ATTR_AUTO_VERIFY, "true")
      }
    }
  }

  class ArgumentComponentDescriptor(name: String, type: String?, nullable: Boolean?, value: String?) : NavComponentDescriptor(TAG_ARGUMENT) {
    init {
      withAttribute(ANDROID_URI, SdkConstants.ATTR_NAME, name)
      value?.let { withAttribute(ANDROID_URI, NavigationSchema.ATTR_DEFAULT_VALUE, it) }
      type?.let { withAttribute(AUTO_URI, SdkConstants.ATTR_ARG_TYPE, it) }
      nullable?.let { withAttribute(AUTO_URI, SdkConstants.ATTR_NULLABLE, it.toString()) }
    }
  }

  @NavTestDsl
  open class NavComponentDescriptor(tagName: String) : ComponentDescriptor(tagName)
}
