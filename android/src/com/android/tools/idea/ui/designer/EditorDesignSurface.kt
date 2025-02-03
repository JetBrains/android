/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui.designer

import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.configurations.Configuration
import com.android.tools.idea.ui.designer.overlays.OverlayConfiguration
import com.google.common.collect.ImmutableCollection
import java.awt.LayoutManager

/**
 * A layout editor design surface.
 */
abstract class EditorDesignSurface(layout: LayoutManager) : AdtPrimaryPanel(layout) {
  /**
   * The [OverlayConfiguration] of the [EditorDesignSurface]
   */
  val overlayConfiguration: OverlayConfiguration = OverlayConfiguration()

  /**
   * All the configurations represented in the surface. Since there are multiple models, there can be multiple configurations
   * being rendered.
   */
  abstract val configurations: ImmutableCollection<Configuration>

  /**
   * When called, this will trigger a re-inflate and refresh of the layout.
   *
   * Only call this method if the action is initiated by the user, call [forceRefresh] otherwise.
   */
  abstract fun forceUserRequestedRefresh()

  /**
   * When called, this will trigger a re-inflate and refresh of the layout.
   */
  abstract fun forceRefresh()
}
