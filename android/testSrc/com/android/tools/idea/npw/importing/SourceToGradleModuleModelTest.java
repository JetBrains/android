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

package com.android.tools.idea.npw.importing;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;

public class SourceToGradleModuleModelTest extends AndroidGradleTestCase {

  public void testContextCreation() {
    Project project = getProject();
    SourceToGradleModuleModel model = new SourceToGradleModuleModel(project);
    assertEquals(project, model.getContext().getProject());
  }

  public void testPropertiesAreStripped() {
    String testString = "some Test String";
    SourceToGradleModuleModel model = new SourceToGradleModuleModel(getProject());

    model.sourceLocation().set(" " + testString + " ");
    assertEquals(testString, model.sourceLocation().get());
  }
}
