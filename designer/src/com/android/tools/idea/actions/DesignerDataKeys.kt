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
@file:JvmName("DesignerDataKeys")

package com.android.tools.idea.actions

import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.editor.AnimationToolbar
import com.intellij.openapi.actionSystem.DataKey

/**
 * Data key for the actions work in Design Editor. This includes DesignSurface and ActionToolBar,
 * but **exclude** all attached ToolWindows. Attached ToolWindows take the responsibility of
 * handling shortcuts and key events. For example, when focusing Palette, typing means search the
 * widget.
 */
@JvmField
val DESIGN_SURFACE: DataKey<DesignSurface<*>> = DataKey.create(DesignSurface::class.qualifiedName!!)

@JvmField
val ANIMATION_TOOLBAR: DataKey<AnimationToolbar> =
  DataKey.create(AnimationToolbar::class.qualifiedName!!)

@JvmField val SCENE_VIEW: DataKey<SceneView> = DataKey.create(SceneView::class.qualifiedName!!)

@JvmField
val CONFIGURATIONS: DataKey<Collection<Configuration>> =
  DataKey.create(DesignSurface::class.qualifiedName!! + "_Configurations")
