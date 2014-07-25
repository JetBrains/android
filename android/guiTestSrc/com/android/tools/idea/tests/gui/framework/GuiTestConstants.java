/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import org.fest.swing.timing.Timeout;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.fest.swing.timing.Timeout.timeout;

public final class GuiTestConstants {
  public static Timeout SHORT_TIMEOUT = timeout(2, MINUTES);
  public static Timeout LONG_TIMEOUT = timeout(5, MINUTES);

  private GuiTestConstants() {
  }
}
