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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager

/**
 * List of available layouts for the Compose Preview Surface.
 */
internal val PREVIEW_LAYOUT_MANAGER_OPTIONS = listOf(
  SurfaceLayoutManagerOption(message("vertical.layout"),
                             VerticalOnlyLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                                                       NlConstants.SCREEN_DELTA, NlConstants.SCREEN_DELTA,
                                                       SingleDirectionLayoutManager.Alignment.CENTER)),
  SurfaceLayoutManagerOption(message("grid.layout"),
                             GridSurfaceLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                                                      NlConstants.SCREEN_DELTA, NlConstants.SCREEN_DELTA),
                             DesignSurface.SceneViewAlignment.LEFT)
)

/**
 * Default layout manager selected in the preview.
 */
internal val DEFAULT_PREVIEW_LAYOUT_MANAGER = PREVIEW_LAYOUT_MANAGER_OPTIONS.first().layoutManager