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
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchResponse
import kotlin.test.fail
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol
/**
 * A class which provides fake logic that behaves like a view layout inspector running on the
 * device.
 *
 * By default, it returns minimal responses. Use [addCommandInterceptor] as a way to intercept and
 * modify the returned response.
 */
class FakeViewLayoutInspector(val connection: Connection) {
  /**
   * Params used internally for passing data through callback chains
   */
  private class Params(
    val command: Command,
    val response: Response,
  )

  /**
   * Class that mimics androidx.inspection.Connection.
   */
  abstract class Connection {
    abstract fun sendEvent(event: Event)

    /** Convenience method for creating a builder and avoiding test boilerplate. */
    fun sendEvent(init: Event.Builder.() -> Unit) {
      sendEvent(Event.newBuilder().apply(init).build())
    }
  }

  private val commandInterceptors = mutableListOf<(Params) -> Response>()

  /**
   * A callback which is triggered with a command received by the inspector from the host.
   *
   * As this is an interceptor, callers are expected to return a custom response, but they may
   * return null to indicate that a default response is fine (perhaps an interceptor will only
   * create a response if some command condition is met).
   */
  fun addCommandInterceptor(case: Command.SpecializedCase, interceptor: (Command) -> Response?) {
    commandInterceptors.add { params ->
      if (case == params.command.specializedCase) {
        interceptor(params.command) ?: params.response
      }
      else {
        params.response
      }
    }
  }

  /**
   * A convenience method for when you want to listen to commands being handled but don't care
   * about the response.
   */
  fun addCommandListener(case: Command.SpecializedCase, listener: (Command) -> Unit) {
    commandInterceptors.add { params, ->
      if (case == params.command.specializedCase) {
        listener(params.command)
      }
      params.response
    }
  }

  /**
   * Handle a received [ViewProtocol.Command], mimicking code that would normally exist on the device
   * in `Inspector.onReceiveCommand`.
   *
   * By default, all responses to commands are default stubs, mimicking a device that has no layout
   * at all. This is fine for many tests, but users should register interceptors with
   * [addCommandInterceptor] if they need different behavior.
   *
   * This method should only be called by internal test framework logic.
   */
  internal fun handleCommand(command: Command): Response {
    val response = when (command.specializedCase) {
      Command.SpecializedCase.START_FETCH_COMMAND -> {
        Response.newBuilder().setStartFetchResponse(StartFetchResponse.getDefaultInstance()).build()
      }
      Command.SpecializedCase.STOP_FETCH_COMMAND -> {
        Response.newBuilder().setStopFetchResponse(StopFetchResponse.getDefaultInstance()).build()
      }
      Command.SpecializedCase.GET_PROPERTIES_COMMAND -> {
        Response.newBuilder().setGetPropertiesResponse(GetPropertiesResponse.getDefaultInstance()).build()
      }
      else -> fail("Unhandled view inspector command: ${command.specializedCase}")
    }
    return commandInterceptors.fold(response) { response, intercept -> intercept(Params(command, response)) }
  }
}
