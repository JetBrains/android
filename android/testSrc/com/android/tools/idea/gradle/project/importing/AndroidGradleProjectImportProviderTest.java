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
package com.android.tools.idea.gradle.project.importing;

import com.android.tools.idea.testing.IdeComponents;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.testFramework.JavaProjectTestCase;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AndroidGradleProjectImportProviderTest extends JavaProjectTestCase {
  private GradleProjectImporter mockGradleProjectImporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mockGradleProjectImporter = IdeComponents.mockApplicationService(GradleProjectImporter.class, getTestRootDisposable());
  }

  public void testDelegatesToGradleProjectImporter() {
    ProjectImportProvider[] providers = ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions();
    List<ProjectImportProvider> androidProviders = Arrays.stream(providers)
                                                         .filter(imp -> imp instanceof AndroidGradleProjectImportProvider)
                                                         .collect(Collectors.toList());
    assertSize(1, androidProviders);
    androidProviders.get(0).getBuilder().commit(getProject());
    verify(mockGradleProjectImporter, times(1)).importProject(eq(getProject().getBaseDir()));
  }
}
