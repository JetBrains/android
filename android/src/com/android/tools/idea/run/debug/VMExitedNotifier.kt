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
package com.android.tools.idea.run.debug

import com.android.ddmlib.Client
import com.android.tools.idea.flags.StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Ensure [Client.notifyVmMirrorExited] is invoked only once"
 * TODO: delete this class and use [client.notifyVmMirrorExited()] directly when we fully switch to [NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER].
 */
internal class VMExitedNotifier(private val client: Client) {
  private val myNeedsToNotify = AtomicBoolean(true)
  fun notifyClient() {
    // The atomic boolean guarantees that we only ever notify the Client once.
    if (myNeedsToNotify.getAndSet(false)) {
      client.notifyVmMirrorExited()
    }
  }
}