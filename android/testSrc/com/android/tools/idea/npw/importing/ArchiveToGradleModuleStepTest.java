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
import com.android.tools.idea.ui.properties.BatchInvoker;
import com.android.tools.idea.ui.properties.TestInvokeStrategy;
import com.android.tools.idea.ui.validation.Validator;
import com.google.common.io.Files;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.google.common.truth.Truth.assertThat;

public class ArchiveToGradleModuleStepTest extends AndroidGradleTestCase {
  private TestInvokeStrategy myInvokeStrategy;
  private ArchiveToGradleModuleModel myModel;
  private ArchiveToGradleModuleStep.ArchiveValidator myArchiveValidator;
  private ArchiveToGradleModuleStep.GradleValidator myGradleValidator;

  private static File createFileOutsideOfProject(@NotNull String filename) throws IOException {
    File newFile = new File(FileUtil.createTempDirectory("directoryOutsideOfProject", null), filename);
    Files.touch(newFile);
    return newFile;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myModel = new ArchiveToGradleModuleModel(getProject());
    ArchiveToGradleModuleStep step = new ArchiveToGradleModuleStep(myModel);
    Disposer.register(getTestRootDisposable(), myModel);
    Disposer.register(getTestRootDisposable(), step);

    myArchiveValidator = new ArchiveToGradleModuleStep.ArchiveValidator();
    myGradleValidator = new ArchiveToGradleModuleStep.GradleValidator(getProject());
    myInvokeStrategy = new TestInvokeStrategy();
    BatchInvoker.setOverrideStrategy(myInvokeStrategy);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      BatchInvoker.clearOverrideStrategy();
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testValidateArchivePathEmpty() throws IOException {
    assertThat(myArchiveValidator.validate("").getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.no.path"));
  }

  public void testValidateArchivePathInvalidPath() throws IOException {
    assertThat(myArchiveValidator.validate("/not/a/real/path.jar").getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.path"));
  }

  public void testValidateArchivePathNotAJarOrAar() throws IOException {
    File notAJarOrAArFile = createFileOutsideOfProject("test.abc");
    assertThat(myArchiveValidator.validate(notAJarOrAArFile.getPath()).getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.extension"));
  }

  public void testValidateArchivePathValidJar() throws IOException {
    File aJarFile = createFileOutsideOfProject("test.jar");
    assertEquals(Validator.Result.OK, myArchiveValidator.validate(aJarFile.getPath()));
  }

  public void testValidateArchivePathValidAar() throws IOException {
    File anAarFile = createFileOutsideOfProject("test.aar");
    assertEquals(Validator.Result.OK, myArchiveValidator.validate(anAarFile.getPath()));
  }

  public void testValidateSubprojectNameEmpty() throws Exception {
    assertThat(myGradleValidator.validate("").getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.no.name"));
  }

  public void testValidateSubprojectNameInvalidForwardSlash() throws Exception {
    assertThat(myGradleValidator.validate("not/valid").getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.name", "/"));
  }

  public void testValidateSubprojectNameInvalidBackslash() throws Exception {
    assertThat(myGradleValidator.validate("also\\invalid").getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.name", "\\"));
  }

  public void testValidateSubprojectNameAlreadyUsed() throws Exception {
    String moduleName = "simple";
    loadProject(IMPORTING);
    assertThat(myGradleValidator.validate(moduleName).getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.taken.name", moduleName));
  }

  public void testInitialiseNameFromPath() {
    myModel.archive().set("");
    myModel.gradlePath().set("");
    myInvokeStrategy.updateAllSteps();

    myModel.archive().set("/some/path/modulename.jar");
    myInvokeStrategy.updateAllSteps();

    assertEquals("modulename", myModel.gradlePath().get());
  }
}
