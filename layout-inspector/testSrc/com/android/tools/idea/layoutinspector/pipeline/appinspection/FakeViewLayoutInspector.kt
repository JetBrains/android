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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import kotlin.test.fail
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

/**
 * A class which provides fake logic that behaves like a view layout inspector running on the
 * device.
 *
 * By default, it returns minimal responses. Use [addCommandInterceptor] as a way to intercept and
 * modify the returned response.
 */
class FakeViewLayoutInspector {
  private val commandInterceptors = mutableListOf<(ViewProtocol.Command, ViewProtocol.Response) -> ViewProtocol.Response>()

  /**
   * A callback which is triggered with both the command received and the default response created this class.
   *
   * Callers may return the response as is, or create a new, modified version, and return that.
   */
  fun addCommandInterceptor(listener: (ViewProtocol.Command, ViewProtocol.Response) -> ViewProtocol.Response) {
    commandInterceptors.add(listener)
  }

  /**
   * A convenience method for when you want to listen to commands being handled but not modify the response.
   */
  fun addCommandListener(listener: (ViewProtocol.Command, ViewProtocol.Response) -> Unit) {
    commandInterceptors.add { command, response ->
      listener(command, response)
      response
    }
  }

  /**
   * Handle a received [ViewProtocol.Command], mimicking code that would normally exist on the device
   * in `Inspector.onReceiveCommand`.
   *
   * This should only be called by internal test framework logic.
   */
  internal fun handleCommand(command: ViewProtocol.Command): ViewProtocol.Response {
    val response = when (command.specializedCase) {
      ViewProtocol.Command.SpecializedCase.START_FETCH_COMMAND -> {
        ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
      }
      ViewProtocol.Command.SpecializedCase.STOP_FETCH_COMMAND -> {
        ViewProtocol.Response.newBuilder().setStopFetchResponse(ViewProtocol.StopFetchResponse.getDefaultInstance()).build()
      }
      else -> fail("Unhandled view inspector command: ${command.specializedCase}")
    }
    return commandInterceptors.fold(response) { response, intercept -> intercept(command, response) }
  }
}