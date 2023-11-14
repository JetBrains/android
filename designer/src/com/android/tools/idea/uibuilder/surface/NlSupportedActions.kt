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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.surface.DesignSurface

/**
 * The supported action in [NlDesignSurface]. To setup the action to [NlDesignSurface], use
 * [NlDesignSurface.Builder.setSupportedActions] to assign the supported actions.
 *
 * TODO(b/183243031): These mechanism should be integrated into
 *   [com.android.tools.idea.common.editor.ActionManager]
 */
enum class NlSupportedActions {
  SWITCH_DEVICE,
  SWITCH_DEVICE_ORIENTATION,
  SWITCH_DESIGN_MODE,
  SWITCH_NIGHT_MODE,
  TOGGLE_ISSUE_PANEL,
  REFRESH
}

/**
 * TODO(b/183243031): These mechanism should be integrated into
 *   [com.android.tools.idea.common.editor.ActionManager]
 */
fun DesignSurface<*>?.isActionSupported(action: NlSupportedActions) =
  (this as? NlDesignSurface)?.supportedActions?.contains(action) ?: false
