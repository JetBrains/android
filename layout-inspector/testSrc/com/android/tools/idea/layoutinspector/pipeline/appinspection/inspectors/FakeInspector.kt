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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors

/**
 * Base class for fake versions of our different inspectors.
 *
 * It provides a very simple structure for handling command / response / event messages, as well as
 * listeners for interception callbacks, so tests can override behavior with finer detail.
 *
 * @param C A generic type representing the inspector's command message
 * @param R A generic type representing the inspector's response message
 * @param E A generic type representing the inspector's event message
 */
abstract class FakeInspector<C, R, E>(val connection: Connection<E>) {
  /**
   * Class that mimics androidx.inspection.Connection.
   */
  abstract class Connection<E> {
    abstract fun sendEvent(event: E)
  }

  private val interceptors = mutableMapOf<(C) -> Boolean, (C) -> R>()
  private val listeners = mutableMapOf<(C) -> Boolean, (C) -> Unit>()

  /**
   * A callback which is triggered with a command received by the inspector from the host, when it
   * matches the specified [condition].
   *
   * Only the first interceptor that matches its condition is used; in other words, multiple
   * overlapping interceptors are not allowed.
   *
   * The callback should fire any events as appropriate using the [connection] property and
   * return an appropriate response.
   */
  fun interceptWhen(condition: (C) -> Boolean, interceptor: (C) -> R) {
    interceptors[condition] = interceptor
  }

  /**
   * Similar to [interceptWhen], but fires without requiring the user to return a response.
   *
   * All matching listeners will be triggered.
   */
  fun listenWhen(condition: (C) -> Boolean, listener: (C) -> Unit) {
    listeners[condition] = listener
  }

  /**
   * Handle a received command, mimicking code that would normally exist on the device
   * in `Inspector.onReceiveCommand`.
   *
   * This method should only be called by internal test framework logic.
   */
  internal fun handleCommand(command: C): R {
    val interceptedResponse = interceptors.entries
      .firstOrNull { it.key(command) }
      ?.let { entry -> entry.value(command) }

    listeners.filter { it.key(command) }.forEach { (_, listener) -> listener(command) }
    return interceptedResponse ?: handleCommandImpl(command)
  }

  /**
   * Default command handler for this fake inspector.
   *
   * This will be called with a target [command] unless an interceptor was already registered
   * that handled it.
   */
  protected abstract fun handleCommandImpl(command: C): R
}
