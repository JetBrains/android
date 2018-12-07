/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.TestRunnerUtil;
import javax.swing.UIManager;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses(AdtUiTestSuite.class)  // a suite mustn't contain itself
public class AdtUiTestSuite extends IdeaTestSuiteBase {
  static {
    // As a side-effect, the following line initializes an initial application. This is important
    // as this test suite has at least one test that creates and then disposes a temporary mock
    // application. However, the ApplicationManager API doesn't fallback to an older application if
    // one was never set, which leaves other tests that call ApplicationManager.getApplication()
    // unexpectedly accessing a disposed application - leading to exceptions if the tests happen to
    // be called in a bad order.
    IdeaTestApplication.getInstance();
    TestRunnerUtil.replaceIdeEventQueueSafely();
    ((PersistentFSImpl)PersistentFS.getInstance()).cleanPersistedContents();
  }
}

