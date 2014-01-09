/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests for the empty SDK error
 */
public class NewProjectWizardErrorTest extends AndroidTestCase {

  public void testSetupWithoutSdk() {
    boolean sdkAssertionReached = false;
    NewProjectWizard wizard = null;
    try {
      wizard = new NewProjectWizard();
    } catch (RuntimeException e) {
      if (e.getMessage().contains("Configure | Project Defaults | Project Structure | SDKs") &&
          e.getMessage().contains("Your Android SDK is missing, out of date, or is missing templates")) {
        sdkAssertionReached = true;
      }
    } finally {
      if (wizard != null) {
        Disposer.dispose(wizard.getDisposable());
      }
    }
    assertTrue("SDK Assertion was not thrown", sdkAssertionReached);
  }
}
