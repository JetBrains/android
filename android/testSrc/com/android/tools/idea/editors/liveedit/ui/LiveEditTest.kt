/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit.ui

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt
import com.android.tools.idea.editors.literals.EditState
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.literals.LiveEditService.Companion.DISABLED_STATUS
import com.android.tools.idea.editors.literals.LiveEditService.Companion.UP_TO_DATE_STATUS
import com.android.tools.idea.emulator.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import java.util.Collections

class LiveEditTest {
  //lateinit var project: Project
  //lateinit var service: LiveEditService
  //lateinit var presentation: Presentation
  //lateinit var device: IDevice
  //
  //@Before
  //fun setup() {
  //  project = MockitoKt.mock()
  //  service = MockitoKt.mock()
  //  presentation = Presentation()
  //  device = MockitoKt.mock()
  //}
  //
  //@Test
  //fun testInitialState() {
  //  `when`(project.getService(LiveEditService::class.java)).thenReturn(service)
  //  `when`(service.editStatus(device)).thenReturn(DISABLED_STATUS)
  //
  //  val action = LiveEditAction(null)
  //  LiveEditAction.registerProject(project, object: LiveEditAction.DeviceGetter {
  //    override fun serial(dataContext: DataContext): String {
  //      return "serial"
  //    }
  //
  //    override fun device(dataContext: DataContext): IDevice {
  //      return device
  //    }
  //
  //    override fun devices(): List<IDevice> {
  //      return Collections.singletonList(device)
  //    }
  //  })
  //  action.update(project, presentation, DataContext.EMPTY_CONTEXT)
  //  assertThat(presentation.isEnabledAndVisible).isFalse()
  //}
  //
  //@Test
  //fun testUpdateIcon() {
  //  val action = LiveEditAction(null)
  //  val indicator = action.createCustomComponent(presentation, RUNNING_DEVICES_TOOL_WINDOW_ID) as ActionButtonWithText
  //  presentation.putClientProperty(LiveEditAction.LIVE_EDIT_STATUS, LiveEditService.Companion.UP_TO_DATE_STATUS)
  //  indicator.updateIcon()
  //  assertThat(indicator.icon).isEqualTo(LiveEditAction.getIconForState(EditState.UP_TO_DATE))
  //}
  //
  //@Test
  //fun testUpdateEditStatus() {
  //  `when`(project.getService(LiveEditService::class.java)).thenReturn(service)
  //  `when`(service.editStatus(device)).thenReturn(UP_TO_DATE_STATUS)
  //  val action = LiveEditAction(null)
  //  presentation.putClientProperty(LiveEditAction.LIVE_EDIT_STATUS, LiveEditService.Companion.UP_TO_DATE_STATUS)
  //  LiveEditAction.registerProject(project, object: LiveEditAction.DeviceGetter {
  //    override fun serial(dataContext: DataContext): String {
  //      return "serial"
  //    }
  //
  //    override fun device(dataContext: DataContext): IDevice {
  //      return device
  //    }
  //
  //    override fun devices(): List<IDevice> {
  //      return Collections.singletonList(device)
  //    }
  //  })
  //  action.update(project, presentation, DataContext.EMPTY_CONTEXT)
  //  assertThat(presentation.icon).isEqualTo(LiveEditAction.getIconForState(EditState.UP_TO_DATE))
  //  assertThat(presentation.isEnabledAndVisible).isTrue()
  //}
}