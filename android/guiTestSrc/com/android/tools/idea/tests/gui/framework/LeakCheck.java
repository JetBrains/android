/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.intellij.testFramework.LeakHunter;
import org.junit.rules.Verifier;

public class LeakCheck extends Verifier {

  private boolean myEnabled = false;

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  @Override
  protected void verify() throws Throwable {
    if (!myEnabled) {
      return;
    }

    try {
      // TODO: should checkProjectLeak return all the leaks at once, instead of bailing out with the first?
      LeakHunter.checkProjectLeak();
      // TODO: Can we say anything about the Disposer state?
      // Disposer.assertIsEmpty(true);
    } catch (Exception ignored) {}
  }
}
