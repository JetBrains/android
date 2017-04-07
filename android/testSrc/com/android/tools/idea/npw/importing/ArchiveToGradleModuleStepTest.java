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

import com.android.tools.idea.ui.properties.BatchInvoker;
import com.android.tools.idea.ui.properties.TestInvokeStrategy;
import com.android.tools.idea.ui.validation.Validator;
import com.google.common.io.Files;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.util.AndroidBundle;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class ArchiveToGradleModuleStepTest extends AndroidGradleImportTestCase {
  private TestInvokeStrategy myInvokeStrategy;
  private ArchiveToGradleModuleStep myStep;
  private ArchiveToGradleModuleModel myModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myModel = new ArchiveToGradleModuleModel(getProject());
    myStep = new ArchiveToGradleModuleStep(myModel);
    myInvokeStrategy = new TestInvokeStrategy();
    BatchInvoker.setOverrideStrategy(myInvokeStrategy);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      BatchInvoker.clearOverrideStrategy();
      if (myStep != null) {
        Disposer.dispose(myStep);
        myStep = null;
      }
      if (myModel != null) {
        Disposer.dispose(myModel);
        myModel = null;
      }
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testValidateArchivePath() throws IOException {
    ArchiveToGradleModuleStep.ArchiveValidator validator = new ArchiveToGradleModuleStep.ArchiveValidator();
    File notAJarOrAArFile = new File(getWorkingDir(), "test.abc");
    Files.touch(notAJarOrAArFile);
    File aJarFile = new File(getWorkingDir(), "test.jar");
    Files.touch(aJarFile);
    File anAarFile = new File(getWorkingDir(), "test.aar");
    Files.touch(anAarFile);

    assertThat(validator.validate("").getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.library.no.path"));
    assertThat(validator.validate("/not/a/real/path.jar").getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.path"));
    assertThat(validator.validate(notAJarOrAArFile.getAbsolutePath()).getMessage())
      .isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.extension"));

    assertThat(validator.validate(aJarFile.getAbsolutePath())).isEqualTo(Validator.Result.OK);
    assertThat(validator.validate(anAarFile.getAbsolutePath())).isEqualTo(Validator.Result.OK);
  }

  public void testValidateSubprojectName() throws IOException {
    ArchiveToGradleModuleStep.GradleValidator validator = new ArchiveToGradleModuleStep.GradleValidator(getProject());
    assertThat(validator.validate("").getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.library.no.name"));
    assertThat(validator.validate("not/valid").getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.name", "/"));
    assertThat(validator.validate("also\\invalid").getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.library.bad.name", "\\"));

    createArchiveInModuleWithinCurrentProject(false, String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY));
    assertThat(validator.validate(SOURCE_MODULE_NAME).getMessage()).isEqualTo(AndroidBundle.message("android.wizard.module.import.library.taken.name", SOURCE_MODULE_NAME));
  }

  public void testInitialiseNameFromPath() {
    myModel.archive().set("");
    myModel.gradlePath().set("");
    myInvokeStrategy.updateAllSteps();

    myModel.archive().set("/some/path/modulename.jar");
    myInvokeStrategy.updateAllSteps();

    assertThat(myModel.gradlePath().get()).isEqualTo("modulename");
  }
}
