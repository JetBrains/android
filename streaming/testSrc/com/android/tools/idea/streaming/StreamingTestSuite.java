/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IconManager;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses(StreamingTestSuite.class)  // A suite may not contain itself.
public class StreamingTestSuite extends IdeaTestSuiteBase {

  static {
    // Since icons are cached and not reloaded for each test, loading of real icons
    // has to be enabled before the first test in the suite that may trigger icon loading.
    try {
      IconManager.activate(null);
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
    IconLoader.activate();
  }
}
