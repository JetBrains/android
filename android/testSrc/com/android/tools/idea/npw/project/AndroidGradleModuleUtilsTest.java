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
package com.android.tools.idea.npw.project;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.junit.Ignore;

import java.io.File;

import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.getContainingModule;
import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.intellij.openapi.util.io.FileUtil.join;

@Ignore("http://b/35788310")
public class AndroidGradleModuleUtilsTest extends AndroidGradleTestCase {
  public void testFake() {
  }

  private static final String ARCHIVE_JAR_PATH = join("lib", "library.jar");
  private static final String NESTED_MODULE_NAME = "sourcemodule";
  private static final String NESTED_MODULE_ARCHIVE_PATH = join("nested", NESTED_MODULE_NAME, ARCHIVE_JAR_PATH);
  private static final String SIMPLE_MODULE_NAME = "simple";
  private static final String SIMPLE_MODULE_ARCHIVE_PATH = join(SIMPLE_MODULE_NAME, ARCHIVE_JAR_PATH);

  public void /*test*/GetContainingModule() throws Exception {
    Project project = getProject();

    loadProject(IMPORTING);
    File archiveToImport = new File(project.getBasePath(), SIMPLE_MODULE_ARCHIVE_PATH);

    assertEquals(ModuleManager.getInstance(project).findModuleByName(SIMPLE_MODULE_NAME), getContainingModule(archiveToImport, project));
  }

  public void /*test*/GetContainingModuleNested() throws Exception {
    Project project = getProject();

    loadProject(IMPORTING);
    File archiveToImport = new File(project.getBasePath(), NESTED_MODULE_ARCHIVE_PATH);

    assertEquals(ModuleManager.getInstance(project).findModuleByName(NESTED_MODULE_NAME), getContainingModule(archiveToImport, project));
  }

  public void /*test*/GetContainingModuleNotInModule() throws Exception {
    assertEquals(null, getContainingModule(new File(getTestDataPath(), join(IMPORTING, SIMPLE_MODULE_ARCHIVE_PATH)), getProject()));
  }
}
