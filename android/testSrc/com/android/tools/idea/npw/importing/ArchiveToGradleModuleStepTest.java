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
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.adtui.validation.Validator;
import com.google.common.io.Files;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;
import static com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents;

public class ArchiveToGradleModuleStepTest extends AndroidGradleTestCase {

  private TestInvokeStrategy myInvokeStrategy;
  private ArchiveToGradleModuleModel myModel;
  private ArchiveToGradleModuleStep.ArchiveValidator myArchiveValidator;
  private ArchiveToGradleModuleStep.GradleValidator myGradleValidator;

  private static File createFileOutsideOfProject(@NotNull String filename) throws IOException {
    File newFile = new File(createTempDirectory("directoryOutsideOfProject", null), filename);
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
      dispatchAllInvocationEvents();
      BatchInvoker.clearOverrideStrategy();
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testValidateArchivePathEmpty() throws IOException {
    String message = myArchiveValidator.validate("").getMessage();
    assertEquals(AndroidBundle.message("android.wizard.module.import.library.no.path"), message);
  }

  public void testValidateArchivePathInvalidPath() throws IOException {
    String message = myArchiveValidator.validate("/not/a/real/path.jar").getMessage();
    assertEquals(AndroidBundle.message("android.wizard.module.import.library.bad.path"), message);
  }

  public void testValidateArchivePathNotAJarOrAar() throws IOException {
    File notAJarOrAArFile = createFileOutsideOfProject("test.abc");
    String message = myArchiveValidator.validate(notAJarOrAArFile.getPath()).getMessage();
    assertEquals(AndroidBundle.message("android.wizard.module.import.library.bad.extension"), message);
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
    String message = myGradleValidator.validate("").getMessage();
    assertEquals(AndroidBundle.message("android.wizard.module.import.library.no.name"), message);
  }

  public void testValidateSubprojectNameInvalidForwardSlash() throws Exception {
    String message = myGradleValidator.validate("not/valid").getMessage();
    assertEquals(AndroidBundle.message("android.wizard.module.import.library.bad.name", "/"), message);
  }

  public void testValidateSubprojectNameInvalidBackslash() throws Exception {
    String message = myGradleValidator.validate("also\\invalid").getMessage();
    assertEquals(AndroidBundle.message("android.wizard.module.import.library.bad.name", "\\"), message);
  }

  public void testValidateSubprojectNameAlreadyUsed() throws Exception {
    String moduleName = "simple";
    loadProject(IMPORTING);
    String message = myGradleValidator.validate(moduleName).getMessage();
    assertEquals(AndroidBundle.message("android.wizard.module.import.library.taken.name", moduleName), message);
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
