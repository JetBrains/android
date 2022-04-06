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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A latch that allows a test to delay the response of a command, while performing another operation.
 */
class CommandLatch(private val timeout: Long, private val unit: TimeUnit) {
  private val waitingCommand = AtomicBoolean(false)
  private var incoming = ReportingCountDownLatch(1)
  private var consumer = ReportingCountDownLatch(1)

  /**
   * Enable this latch.
   *
   * When this latch is enabled, incoming command responses will be delayed until [waitingCommand] is called.
   * When this latch is disabled, incoming command responses are received immediately.
   */
  var enabled: Boolean = false

  /**
   * Called from a command interceptor to allow a test to delay the response from the command.
   *
   * If this latch is not [enabled] or a previous command is currently being delayed: the response will be returned immediately.
   * Otherwise: release a test that may already have called [waitForCommand] and wait for that operation to finish.
   */
  fun incomingCommand() {
    if (enabled && !waitingCommand.getAndSet(true)) {  // Do not delay this command if a previous command is pending
      consumer.countDown()
      incoming.await(timeout, unit)
      incoming = ReportingCountDownLatch(1)
      waitingCommand.set(false)
    }
  }

  /**
   * Called from test to:
   * - wait for an [incomingCommand]
   * - run a specified [operation]
   * - release the incoming command
   */
  fun waitForCommand(operation: () -> Unit) {
    if (enabled) {
      consumer.await(timeout, unit)
      consumer = ReportingCountDownLatch(1)
      operation()
      incoming.countDown()
    }
  }
}
