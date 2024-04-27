/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.deviceprovisioner

import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceAction.Presentation
import com.intellij.icons.AllIcons
import icons.StudioIcons

object StudioDefaultDeviceActionPresentation : DeviceAction.DefaultPresentation {
  override val createDeviceAction = Presentation("Create", StudioIcons.Common.ADD, true)
  override val createDeviceTemplateAction = Presentation("Create", StudioIcons.Common.ADD, true)
  override val activationAction = Presentation("Start", StudioIcons.Avd.RUN, true)
  override val coldBootAction = Presentation("Cold Boot", StudioIcons.Avd.RUN, true)
  override val bootSnapshotAction = Presentation("Boot from Snapshot", StudioIcons.Avd.RUN, true)
  override val deactivationAction = Presentation("Stop", StudioIcons.Avd.STOP, true)
  override val editAction = Presentation("Edit", StudioIcons.Avd.EDIT, true)
  override val deleteAction = Presentation("Delete", StudioIcons.Common.DELETE, true)
  override val duplicateAction = Presentation("Duplicate", AllIcons.Actions.Copy, true)
  override val wipeDataAction = Presentation("Wipe Data", StudioIcons.Common.DELETE, true)
  override val showAction = Presentation("Show", AllIcons.Actions.Show, true)
  override val editTemplateAction = Presentation("Edit", StudioIcons.Avd.EDIT, true)
  override val reservationAction = Presentation("Reserve", AllIcons.Actions.Resume, true)
  override val templateActivationAction = Presentation("Start", StudioIcons.Avd.RUN, true)
  override val repairDeviceAction = Presentation("Start", StudioIcons.Misc.BUILD_TYPE, true)
}