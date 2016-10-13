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
import junit.framework.Assert;
import org.jetbrains.android.util.AndroidBundle;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SourceToGradleModuleStepTest extends AndroidGradleImportTestCase {
  private VirtualFile myModule;
  private SourceToGradleModuleStep.SubmoduleFinder myFinder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModule = GradleModuleImportTest.createGradleProjectToImport(new File(getWorkingDir(), "project"), "gradleProject");
    myFinder = new SourceToGradleModuleStep.SubmoduleFinder(new SourceToGradleModuleModel(getProject()));
  }

  public void testValidPathIsFound() throws Exception {
    String modulePath = virtualToIoFile(myModule).getAbsolutePath();
    SourceToGradleModuleStep.SubmoduleFinder.SearchResult result = myFinder.search(modulePath);

    assertThat(result.modules).isNotEmpty();
  }

  public void testEmptyPathIsRejected() {
    try {
      myFinder.search("");
      Assert.fail();
    }
    catch (SourceToGradleModuleStep.SubmoduleFinder.SearchException e) {
      assertThat(e.getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.source.browse.no.location"));
    }
  }

  public void testNonExistantPathIsRejected() {
    String modulePath = virtualToIoFile(myModule).getAbsolutePath();

    try {
      myFinder.search(modulePath + "_path_that_does_not_exist");
      Assert.fail();
    }
    catch (SourceToGradleModuleStep.SubmoduleFinder.SearchException e) {
      assertThat(e.getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.source.browse.invalid.location"));
    }
  }

  public void testPathWithoutGradleModuleIsRejected() {
    try {
      myFinder.search(getWorkingDir().getAbsolutePath());
      Assert.fail();
    }
    catch (SourceToGradleModuleStep.SubmoduleFinder.SearchException e) {
      assertThat(e.getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.source.browse.cant.import"));
    }
  }

  public void testAlreadyImportedProjectIsRejected() {
    try {
      myFinder.search(virtualToIoFile(getProject().getBaseDir()).getAbsolutePath());
      Assert.fail();
    }
    catch (SourceToGradleModuleStep.SubmoduleFinder.SearchException e) {
      assertThat(e.getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.source.browse.taken.location"));
    }
  }

  public void testAdditionalProjectCanBeImported() throws Exception {
    File moduleInProject = createArchiveInModuleWithinCurrentProject(false, String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY));

    SourceToGradleModuleStep.SubmoduleFinder.SearchResult result =
      myFinder.search(moduleInProject.getParentFile().getParentFile().getAbsolutePath());

    assertThat(result.modules).isNotEmpty();
  }
}
