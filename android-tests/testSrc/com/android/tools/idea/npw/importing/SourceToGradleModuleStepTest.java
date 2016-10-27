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

import com.android.tools.idea.gradle.project.GradleModuleImportTest;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.*;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SourceToGradleModuleStepTest extends AndroidGradleImportTestCase {
  private VirtualFile myModule;
  private SourceToGradleModuleStep myPage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModule = GradleModuleImportTest.createGradleProjectToImport(new File(getWorkingDir(), "project"), "gradleProject");
    myPage = new SourceToGradleModuleStep(new SourceToGradleModuleModel(getProject()));
  }

  public void testValidation() {
    String modulePath = virtualToIoFile(myModule).getAbsolutePath();
    assertThat(myPage.checkPath(modulePath).myStatus).isEqualTo(OK);
    assertThat(myPage.checkPath(modulePath + "_path_that_does_not_exist").myStatus).isEqualTo(DOES_NOT_EXIST);
    assertThat(myPage.checkPath("").myStatus).isEqualTo(EMPTY_PATH);
    assertThat(myPage.checkPath(getWorkingDir().getAbsolutePath()).myStatus).isEqualTo(NOT_ADT_OR_GRADLE);
  }

  public void testPathInProject() throws IOException {
    File moduleInProject = createArchiveInModuleWithinCurrentProject(false, String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY));
    assertThat(myPage.checkPath(virtualToIoFile(getProject().getBaseDir()).getAbsolutePath()).myStatus).isEqualTo(IS_PROJECT_OR_MODULE);
    assertThat(myPage.checkPath(moduleInProject.getParentFile().getParentFile().getAbsolutePath()).myStatus).isEqualTo(OK);
  }
}
