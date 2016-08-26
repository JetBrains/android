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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  com.android.tools.idea.IdeaTestSuite.class,  // a suite mustn't contain itself
  com.android.tools.idea.rendering.RenderSecurityManagerTest.class,  // calls System.setSecurityManager
  com.android.tools.idea.templates.TemplateTest.class, // we typically set DISABLE_STUDIO_TEMPLATE_TESTS because it's so slow
})
public class IdeaTestSuite {
  // Initialize Idea specific environment
  static {
    System.setProperty("idea.home", System.getProperty("java.io.tmpdir"));
    if (System.getenv("TEST_SRCDIR") != null) {
      VfsRootAccess.allowRootAccess(System.getenv("TEST_SRCDIR"));
    }
  }
}
