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

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.*;
import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;

public class SourceToGradleModuleStepTest extends AndroidGradleTestCase {

  private SourceToGradleModuleStep myPage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPage = new SourceToGradleModuleStep(new SourceToGradleModuleModel(getProject()));
  }

  @Override
  public void tearDown() throws Exception {
    myPage = null;
    super.tearDown();
  }

  public void testCheckPathValidInput() throws Exception {
    String path = new File(getTestDataPath(), IMPORTING).getPath();
    SourceToGradleModuleStep.PathValidationResult.ResultType status = myPage.checkPath(path).myStatus;
    assertEquals(OK, status);
  }

  public void testCheckPathDoesNotExist() throws IOException {
    String path = new File(getTestDataPath(), "path_that_does_not_exist").getPath();
    SourceToGradleModuleStep.PathValidationResult.ResultType status = myPage.checkPath(path).myStatus;
    assertEquals(DOES_NOT_EXIST, status);
  }

  public void testCheckPathEmptyPath() {
    String path = "";
    SourceToGradleModuleStep.PathValidationResult.ResultType status = myPage.checkPath(path).myStatus;
    assertEquals(EMPTY_PATH, status);
  }

  public void testCheckPathNotAProject() throws IOException {
    String path = getTestDataPath();
    SourceToGradleModuleStep.PathValidationResult.ResultType status = myPage.checkPath(path).myStatus;
    assertEquals(NOT_ADT_OR_GRADLE, status);
  }

  public void testCheckPathInProject() throws Exception {
    loadProject(IMPORTING);
    String path = getProjectFolderPath().getPath();
    SourceToGradleModuleStep.PathValidationResult.ResultType status = myPage.checkPath(path).myStatus;
    assertEquals(IS_PROJECT_OR_MODULE, status);
  }
}
