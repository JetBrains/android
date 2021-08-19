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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.io.File;

import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.getContainingModule;
import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.intellij.openapi.util.io.FileUtil.join;

public class AndroidGradleModuleUtilsTest extends AndroidGradleTestCase {

  public void testGetContainingModule() throws Exception {
    Project project = getProject();

    loadProject(IMPORTING);
    File archiveToImport = new File(project.getBasePath(), join("simple", "lib", "library.jar"));

    assertEquals(getModule("simple"), getContainingModule(archiveToImport, project));
  }

  public void testGetContainingModuleNested() throws Exception {
    Project project = getProject();

    loadProject(IMPORTING);
    File archiveToImport = new File(project.getBasePath(), join("nested", "sourcemodule", "lib", "library.jar"));

    assertEquals(getModule("sourcemodule"), getContainingModule(archiveToImport, project));
  }

  public void testGetContainingModuleNotInModule() throws Exception {
    Module module = getContainingModule(new File(getTestDataPath(), join(IMPORTING, "simple", "lib", "library.jar")), getProject());
    assertEquals(null, module);
  }
}
