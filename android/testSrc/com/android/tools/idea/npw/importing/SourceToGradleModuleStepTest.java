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
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.*;
import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.google.common.truth.Truth.assertThat;

@Ignore("http://b/35788310")
public class SourceToGradleModuleStepTest extends AndroidGradleTestCase {
  public void testFake() {
  }

  private SourceToGradleModuleStep myPage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPage = new SourceToGradleModuleStep(new SourceToGradleModuleModel(getProject()));
  }

  public void /*test*/CheckPathValidInput() throws Exception {
    assertEquals(OK, myPage.checkPath(new File(getTestDataPath(), IMPORTING).getPath()).myStatus);
  }

  public void /*test*/CheckPathDoesNotExist() throws IOException {
    assertThat(myPage.checkPath(new File(getTestDataPath(), "path_that_does_not_exist").getPath()).myStatus)
      .isEqualTo(DOES_NOT_EXIST);
  }

  public void /*test*/CheckPathEmptyPath() {
    assertEquals(EMPTY_PATH, myPage.checkPath("").myStatus);
  }

  public void /*test*/CheckPathNotAProject() throws IOException {
    assertEquals(NOT_ADT_OR_GRADLE, myPage.checkPath(getTestDataPath()).myStatus);
  }

  public void /*test*/CheckPathInProject() throws Exception {
    loadProject(IMPORTING);
    assertEquals(IS_PROJECT_OR_MODULE, myPage.checkPath(getProjectFolderPath().getPath()).myStatus);
  }
}
