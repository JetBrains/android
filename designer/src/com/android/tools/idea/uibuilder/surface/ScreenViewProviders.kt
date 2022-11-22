/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SceneLayer
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.ScreenView.DEVICE_CONTENT_SIZE_POLICY
import com.android.tools.idea.uibuilder.visual.ColorBlindModeScreenViewLayer
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import java.awt.Dimension

/**
 * Interface to generate [ScreenView]s for the DesignSurface.
 */
interface ScreenViewProvider {
  /**
   * User visible name when switching through different [ScreenViewProvider]s.
   */
  val displayName: String

  /**
   * May return another [ScreenViewProvider]. Used to quickly toggle through different types of [ScreenViewProvider] in the DesignSurface.
   */
  operator fun next(): ScreenViewProvider? = null

  /**
   * The default [ScreenView] to show in the DesignSurface.
   */
  fun createPrimarySceneView(surface: NlDesignSurface, manager: LayoutlibSceneManager): ScreenView

  /**
   * An optional [ScreenView] that may be displayed instead of the primary or side to side in the DesignSurface.
   */
  fun createSecondarySceneView(surface: NlDesignSurface, manager: LayoutlibSceneManager): ScreenView? = null

  /** Called if the current provider was this, and is being replaced by another in DesignSurface. */
  fun onViewProviderReplaced() {}

  /**
   * The [LayoutEditorState.Surfaces] to be reported by this scene view for metrics purposes.
   */
  val surfaceType: LayoutEditorState.Surfaces
}

/**
 * Common [ScreenViewProvider]s for the Layout Editor.
 */
enum class NlScreenViewProvider(override val displayName: String,
                                val primary: (NlDesignSurface, LayoutlibSceneManager, Boolean) -> ScreenView,
                                val secondary: ((NlDesignSurface, LayoutlibSceneManager, Boolean) -> ScreenView)? = null,
                                private val visibleToUser: Boolean = true,
                                override val surfaceType: LayoutEditorState.Surfaces) : ScreenViewProvider {

  RENDER("Design", ::defaultProvider, surfaceType = LayoutEditorState.Surfaces.SCREEN_SURFACE),
  BLUEPRINT("Blueprint", ::blueprintProvider, surfaceType = LayoutEditorState.Surfaces.BLUEPRINT_SURFACE),
  RENDER_AND_BLUEPRINT("Design and Blueprint", ::defaultProvider, ::blueprintProvider, surfaceType = LayoutEditorState.Surfaces.BOTH),
  RESIZABLE_PREVIEW("Preview",
                    { surface, manager, _ ->
                      ScreenView.newBuilder(surface, manager)
                        .resizeable()
                        .decorateContentSizePolicy { policy -> ScreenView.ImageContentSizePolicy(policy) }
                        .build()
                    },
                    visibleToUser = false,
                    surfaceType = LayoutEditorState.Surfaces.SCREEN_SURFACE),
  VISUALIZATION("Visualization", ::visualizationProvider, visibleToUser = false, surfaceType = LayoutEditorState.Surfaces.SCREEN_SURFACE),
  COLOR_BLIND("Color Blind Mode", ::colorBlindProvider, visibleToUser = false, surfaceType = LayoutEditorState.Surfaces.SCREEN_SURFACE);

  override operator fun next(): NlScreenViewProvider {
    val values = values().filter { it.visibleToUser }
    return values[(ordinal + 1) % values.size]
  }

  override fun createPrimarySceneView(surface: NlDesignSurface, manager: LayoutlibSceneManager): ScreenView =
    primary(surface, manager, false)

  override fun createSecondarySceneView(surface: NlDesignSurface, manager: LayoutlibSceneManager): ScreenView? =
    secondary?.invoke(surface, manager, true)?.apply { isSecondary = true }

  companion object {

    @VisibleForTesting
    val DEFAULT_SCREEN_MODE = RENDER_AND_BLUEPRINT

    @VisibleForTesting
    val SCREEN_MODE_PROPERTY = "NlScreenModeProvider"

    private var cachedScreenViewProvider: NlScreenViewProvider? = null

    @Synchronized
    fun loadPreferredMode(): NlScreenViewProvider {
      if (cachedScreenViewProvider != null) {
        return cachedScreenViewProvider!!
      }

      val modeName = PropertiesComponent.getInstance()?.getValue(SCREEN_MODE_PROPERTY, DEFAULT_SCREEN_MODE.name)
                     ?: return DEFAULT_SCREEN_MODE // In unit testing we might not have the PropertiesComponent
      cachedScreenViewProvider = try {
        valueOf(modeName)
      }
      catch (e: IllegalArgumentException) {
        // If the code reach here, that means some of unexpected SceneMode is saved as user's preference.
        // In this case, return the default mode instead.
        Logger.getInstance(NlDesignSurface::class.java)
          .warn("The mode $modeName is not recognized, use default mode $SCREEN_MODE_PROPERTY instead")
        DEFAULT_SCREEN_MODE
      }

      return cachedScreenViewProvider!!
    }

    @Synchronized
    fun savePreferredMode(mode: NlScreenViewProvider) {
      // See comment about SCREEN_COMPOSE_ONLY on loadPreferredMode
      if (cachedScreenViewProvider == mode) {
        return
      }

      cachedScreenViewProvider = mode
      PropertiesComponent.getInstance().setValue(SCREEN_MODE_PROPERTY, mode.name)
    }
  }
}

/**
 * Default provider that provider the [ScreenView] design surface only.
 */
internal fun defaultProvider(surface: NlDesignSurface,
                             manager: LayoutlibSceneManager,
                             @Suppress("UNUSED_PARAMETER") isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager).resizeable().build()

internal fun blueprintProvider(surface: NlDesignSurface, manager: LayoutlibSceneManager, isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .resizeable()
    .withColorSet(BlueprintColorSet())
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        if (it.hasBorderLayer()) {
          add(BorderLayer(it))
        }
        if (!isSecondary) {
          add(CanvasResizeLayer(it.surface, it))
        }
        add(SceneLayer(it.surface, it, true))
      }.build()
    }
    .build()

/**
 * Returns a [ScreenView] for the multi-visualization.
 */
internal fun visualizationProvider(surface: NlDesignSurface,
                                   manager: LayoutlibSceneManager,
                                   @Suppress("UNUSED_PARAMETER") isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        // Always has border in visualization tool.
        add(BorderLayer(it))
        add(ScreenViewLayer(it))
        add(SceneLayer(it.surface, it, false).apply { isShowOnHover = true })
        add(WarningLayer(it))
      }.build()
    }
    .withContentSizePolicy(DEVICE_CONTENT_SIZE_POLICY)
    .decorateContentSizePolicy { wrappedPolicy ->
      object : ScreenView.ContentSizePolicy {
        override fun measure(screenView: ScreenView, outDimension: Dimension) = wrappedPolicy.measure(screenView, outDimension)

        // In visualization view, we always use configuration to decide the size.
        override fun hasContentSize(screenView: ScreenView) = screenView.isVisible
      }
    }
    .disableBorder()
    .build()

/**
 * Returns appropriate mode based on model display name.
 */
private fun colorBlindMode(sceneManager: SceneManager): ColorBlindMode? {
  val model: NlModel = sceneManager.model
  for (mode in ColorBlindMode.values()) {
    if (mode.displayName == model.modelDisplayName) {
      return mode
    }
  }
  return null
}

/**
 * View for drawing color blind modes.
 */
internal fun colorBlindProvider(surface: NlDesignSurface,
                                manager: LayoutlibSceneManager,
                                @Suppress("UNUSED_PARAMETER") isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        // Always has border in visualization tool.
        add(BorderLayer(it))
        val mode: ColorBlindMode? = colorBlindMode(manager)
        if (mode != null) {
          add(ColorBlindModeScreenViewLayer(it, mode))
        }
        else {
          // ERROR - at least show the original.
          add(ScreenViewLayer(it))
        }
      }.build()
    }
    .disableBorder()
    .build()

/**
 * Provider for an specific [ColorBlindMode].
 */
internal fun colorBlindProviderSelector(surface: NlDesignSurface,
                                        manager: LayoutlibSceneManager,
                                        @Suppress("UNUSED_PARAMETER") isSecondary: Boolean,
                                        mode: ColorBlindMode): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        // Always has border in visualization tool.
        add(BorderLayer(it))
        add(ColorBlindModeScreenViewLayer(it, mode))
      }.build()
    }
    .disableBorder()
    .decorateContentSizePolicy { policy -> ScreenView.ImageContentSizePolicy(policy) }
    .build()
