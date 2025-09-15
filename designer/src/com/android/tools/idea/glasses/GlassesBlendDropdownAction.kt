/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.glasses

import com.android.sdklib.devices.Device
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.actions.SCENE_VIEW
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.StudioIcons

/**
 * A Dropdown action that contains preset backgrounds to be applied in a Compose Preview when the
 * device is XR glasses. The backgrounds will be blended into the preview by applying certain
 * heuristics used to simulate how Composables look like in XR Glasses environment.
 */
class GlassesBlendDropdownAction :
  DropDownAction("Select Preset Backgrounds", null, StudioIcons.Avd.DEVICE_GLASS) {

  // TODO(b/413740393): Create actions to set the preset background and add each one of them as
  //  options in the dropdown

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible =
      Device.isXrGlasses(e.getData(SCENE_VIEW)?.configuration?.device)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
