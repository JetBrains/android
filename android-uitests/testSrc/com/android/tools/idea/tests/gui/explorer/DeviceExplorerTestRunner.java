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
package com.android.tools.idea.tests.gui.explorer;

import com.android.tools.idea.explorer.DeviceExplorer;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import org.junit.runners.model.InitializationError;

public class DeviceExplorerTestRunner extends GuiTestRunner {

  public DeviceExplorerTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
    // Force-enable the feature when running tests
    DeviceExplorer.enableFeature(true);
  }
}
