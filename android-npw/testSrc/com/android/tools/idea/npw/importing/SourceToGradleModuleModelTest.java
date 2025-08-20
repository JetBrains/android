/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ProjectRule;
import org.junit.Rule;
import org.junit.Test;

public class SourceToGradleModuleModelTest {
  @Rule
  public ProjectRule projectRule = new ProjectRule();

  @Test
  public void testContextCreation() {
    Project project = projectRule.getProject();
    SourceToGradleModuleModel model = new SourceToGradleModuleModel(project, new ProjectSyncInvoker.DefaultProjectSyncInvoker());
    assertThat(model.getContext().getProject()).isEqualTo(project);
  }

  @Test
  public void testPropertiesAreStripped() {
    String testString = "some Test String";
    SourceToGradleModuleModel model = new SourceToGradleModuleModel(projectRule.getProject(), new ProjectSyncInvoker.DefaultProjectSyncInvoker());

    model.sourceLocation.set(" " + testString + " ");
    assertThat(model.sourceLocation.get()).isEqualTo(testString);
  }
}
