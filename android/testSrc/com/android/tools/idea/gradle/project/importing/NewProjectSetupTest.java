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
package com.android.tools.idea.gradle.project.importing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.intellij.pom.java.LanguageLevel.JDK_1_6;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link NewProjectSetup}.
 */
public class NewProjectSetupTest extends IdeaTestCase {
  @Mock TopLevelModuleFactory myTopLevelModuleFactory;

  private NewProjectSetup myNewProjectSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(JDK_1_6);

    CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(project);
    assertNotNull(compilerProjectExtension);
    assertNull(compilerProjectExtension.getCompilerOutputUrl());

    ProjectTypeService.setProjectType(project, null);

    myNewProjectSetup = new NewProjectSetup(myTopLevelModuleFactory);
  }

  public void testPrepareProjectForImportWithLanguageLevel() {
    myNewProjectSetup.prepareProjectForImport(getProject(), JDK_1_8, true);

    verifyLanguageLevel(JDK_1_8);
    verifyCompilerOutputUrl();
    verifyProjectType();
  }

  public void testPrepareProjectForImportWithoutLanguageLevel() {
    myNewProjectSetup.prepareProjectForImport(getProject(), null, true);

    verifyLanguageLevel(JDK_1_6);
    verifyCompilerOutputUrl();
    verifyProjectType();
  }

  private void verifyLanguageLevel(@NotNull LanguageLevel expected) {
    assertSame(expected, LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel());
  }

  private void verifyCompilerOutputUrl() {
    CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(getProject());
    assertNotNull(compilerProjectExtension);
    assertNotNull(compilerProjectExtension.getCompilerOutputUrl());
  }

  private void verifyProjectType() {
    assertSame(NewProjectSetup.ANDROID_PROJECT_TYPE, ProjectTypeService.getProjectType(getProject()));
  }
}