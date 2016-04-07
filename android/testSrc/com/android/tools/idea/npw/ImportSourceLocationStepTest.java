/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.gradle.project.GradleModuleImportTest;
import com.google.common.io.Files;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Test wizard page for specifying the location
 */
public final class ImportSourceLocationStepTest extends AndroidTestBase {
  private VirtualFile myModule;
  private ImportSourceLocationStep myPage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = GradleModuleImportTest.createGradleProjectToImport(new File(Files.createTempDir(), "project"), "gradleProject");
    myPage = new ImportSourceLocationStep(new WizardContext(getProject()), null, new NewModuleWizardState(), null, null);
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
  }

  public void testValidation() {
    String modulePath = virtualToIoFile(myModule).getAbsolutePath();
    assertValidationResult(ImportSourceLocationStep.PageStatus.OK, modulePath);
    assertValidationResult(ImportSourceLocationStep.PageStatus.DOES_NOT_EXIST,
                           modulePath + "_it_cant_exists_I_even_add_random_numbers_201404021710");
    assertValidationResult(ImportSourceLocationStep.PageStatus.EMPTY_PATH, "");
    assertValidationResult(ImportSourceLocationStep.PageStatus.NOT_ADT_OR_GRADLE, virtualToIoFile(myModule.getParent()).getAbsolutePath());
  }

  public void testPathInProject() throws IOException {
    VirtualFile moduleInProject = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return GradleModuleImportTest.createGradleProjectToImport(VfsUtilCore.virtualToIoFile(getProject().getBaseDir()), "gradlemodule");
      }
    });
    moduleInProject.refresh(false, true);
    assertValidationResult(ImportSourceLocationStep.PageStatus.IS_PROJECT_OR_MODULE, virtualToIoFile(getProject().getBaseDir()).getAbsolutePath());
    assertValidationResult(ImportSourceLocationStep.PageStatus.OK, virtualToIoFile(moduleInProject).getAbsolutePath());
  }

  private void assertValidationResult(ImportSourceLocationStep.PageStatus validationResult, String path) {
    assertEquals(validationResult, myPage.checkPath(path).myStatus);
  }

}
