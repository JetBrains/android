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
import com.android.sdklib.deviceprovisioner.DeviceActionCanceledException
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.ReservationAction
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs the supplied block, which should invoke a DeviceAction. In the case of a
 * DeviceActionException, logs the exception and shows an error dialog with the supplied title.
 */
suspend fun runCatchingDeviceActionException(
  project: Project?,
  title: String,
  block: suspend () -> Unit
) {
  try {
    block()
  } catch (e: DeviceActionCanceledException) {
    logger<DeviceAction>().info(e.message)
  } catch (e: DeviceActionException) {
    logger<DeviceAction>().warn(e)
    withContext(AndroidDispatchers.uiThread) { Messages.showErrorDialog(project, e.message, title) }
  }
}

/**
 * Launches a coroutine in the DeviceHandle's [scope] that runs the given [block]. Any
 * [DeviceActionException] is caught, logged, and displayed in an error popup.
 *
 * @param coroutineContext additional CoroutineContext to apply to the launch
 * @param project the Project used for the error popup
 */
fun <DeviceHandleT : DeviceHandle> DeviceHandleT.launchCatchingDeviceActionException(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  project: Project? = null,
  block: suspend DeviceHandleT.() -> Unit
) {
  scope.launch(coroutineContext) {
    runCatchingDeviceActionException(project, state.properties.title) { block() }
  }
}

/**
 * Launches a coroutine in the supplied [coroutineScope] that runs the given [block]. Any
 * [DeviceActionException] is caught, logged, and displayed in an error popup.
 *
 * @param project the Project used for the error popup
 */
fun <DeviceTemplateT : DeviceTemplate> DeviceTemplateT.launchCatchingDeviceActionException(
  coroutineScope: CoroutineScope,
  project: Project? = null,
  block: suspend DeviceTemplateT.() -> Unit
) {
  coroutineScope.launch { runCatchingDeviceActionException(project, properties.title) { block() } }
}

/**
 * Returns the DeviceHandle associated with the existing event. Note that this depends on some
 * component related to the event implementing [DataProvider] and supplying the handle.
 */
fun AnActionEvent.deviceHandle() = DEVICE_HANDLE_KEY.getData(dataContext)

/**
 * Returns the DeviceTemplate associated with the existing event. Note that this depends on some
 * component related to the event implementing [DataProvider] and supplying the handle.
 */
fun AnActionEvent.deviceTemplate() = DEVICE_TEMPLATE_KEY.getData(dataContext)

/**
 * Returns the [ReservationAction] for [DeviceHandle]; null if the handle does not have
 * [ReservationAction]
 */
internal fun AnActionEvent.reservationAction() = deviceHandle()?.reservationAction