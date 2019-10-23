/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.util.concurrent.TimeUnit;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** A {@link Timeout} that is disabled when the idea.debug.mode property is true. */
public class DebugFriendlyTimeout extends Timeout {
  public DebugFriendlyTimeout(long timeout, TimeUnit timeUnit) {
    super(timeout, timeUnit);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    if (Boolean.getBoolean("idea.debug.mode")) {
      return base;
    } else {
      return super.apply(base, description);
    }
  }
}
