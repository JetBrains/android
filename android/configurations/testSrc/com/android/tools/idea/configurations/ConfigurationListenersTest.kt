/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.tools.configurations.ConfigurationListener
import com.android.tools.configurations.ConfigurationListeners
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfigurationListenersTest {
  @Test
  fun testBulkEditingStateAndNesting() {
    val listeners = ConfigurationListeners()

    assertThat(listeners.isBulkEditing).named("Should not be bulk editing initially").isFalse()

    // Start level 1
    listeners.startBulkEditing()
    assertThat(listeners.isBulkEditing).isTrue()

    // Start level 2 (Nested)
    listeners.startBulkEditing()
    assertThat(listeners.isBulkEditing).isTrue()

    // Finish level 2
    val shouldNotify1 = listeners.finishBulkEditing()
    assertThat(shouldNotify1).named("Should not notify until all bulk edits finish").isFalse()
    assertThat(listeners.isBulkEditing).named("Should still be bulk editing").isTrue()

    // Finish level 1
    val shouldNotify2 = listeners.finishBulkEditing()
    assertThat(shouldNotify2).named("Should notify when final bulk edit finishes").isTrue()
    assertThat(listeners.isBulkEditing).named("Should no longer be bulk editing").isFalse()
  }

  @Test
  fun testListenerNotifications() {
    val listeners = ConfigurationListeners()
    var receivedFlags = 0

    listeners.addListener(
      ConfigurationListener { flags: Int ->
        receivedFlags = flags
        true
      }
    )

    listeners.notifyListeners(42)
    assertThat(receivedFlags).named("Listener should receive the exact flags dispatched").isEqualTo(42)
  }
}
