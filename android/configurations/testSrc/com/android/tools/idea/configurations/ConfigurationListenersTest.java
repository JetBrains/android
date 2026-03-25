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
package com.android.tools.idea.configurations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.configurations.ConfigurationListeners;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ConfigurationListenersTest {

  @Test
  public void testBulkEditingStateAndNesting() {
    ConfigurationListeners listeners = new ConfigurationListeners();

    assertFalse("Should not be bulk editing initially", listeners.isBulkEditing());

    // Start level 1
    listeners.startBulkEditing();
    assertTrue(listeners.isBulkEditing());

    // Start level 2 (Nested)
    listeners.startBulkEditing();
    assertTrue(listeners.isBulkEditing());

    // Finish level 2
    boolean shouldNotify1 = listeners.finishBulkEditing();
    assertFalse("Should not notify until all bulk edits finish", shouldNotify1);
    assertTrue("Should still be bulk editing", listeners.isBulkEditing());

    // Finish level 1
    boolean shouldNotify2 = listeners.finishBulkEditing();
    assertTrue("Should notify when final bulk edit finishes", shouldNotify2);
    assertFalse("Should no longer be bulk editing", listeners.isBulkEditing());
  }

  @Test
  public void testListenerNotifications() {
    ConfigurationListeners listeners = new ConfigurationListeners();
    AtomicInteger receivedFlags = new AtomicInteger(0);

    listeners.addListener(flags -> {
      receivedFlags.set(flags);
      return true;
    });

    listeners.notifyListeners(42);
    assertEquals("Listener should receive the exact flags dispatched", 42, receivedFlags.get());
  }
}