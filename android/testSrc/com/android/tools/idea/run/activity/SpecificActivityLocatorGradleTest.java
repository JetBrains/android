/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;

public class SpecificActivityLocatorGradleTest extends AndroidGradleTestCase {

  public void testFindActivity() throws Exception {
    loadProject(TestProjectPaths.UI_TOOLING_DEPENDENCY);

    final String appActivity = "com.android.test.uitoolingdependency.MainActivity";
    final String externalActivity = "androidx.ui.tooling.preview.PreviewActivity";

    Project project = myAndroidFacet.getModule().getProject();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    GlobalSearchScope globalScope = GlobalSearchScope.allScope(project);

    SpecificActivityLocator locator = new SpecificActivityLocator(myAndroidFacet, appActivity, globalScope);
    // Activities within the project scope should be found regardless.
    locator.validate();
    locator = new SpecificActivityLocator(myAndroidFacet, appActivity, projectScope);
    locator.validate();

    locator = new SpecificActivityLocator(myAndroidFacet, externalActivity, globalScope);
    // Activities outside of the project scope (e.g. in libraries) can only be found if the search scope is global.
    locator.validate();
    try {
      locator = new SpecificActivityLocator(myAndroidFacet, externalActivity, projectScope);
      locator.validate();
      fail("Expected to throw an Exception for not finding PreviewActivity");
    }
    catch (Exception expected) {
    }
  }
}
