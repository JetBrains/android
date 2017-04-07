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

import com.android.tools.idea.npw.importing.AndroidGradleImportTestCase;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;

import java.io.File;

import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.getContainingModule;

public class AndroidGradleModuleUtilsTest extends AndroidGradleImportTestCase {

  public void testGetContainingModule() {
    Project project = getProject();
    File archiveToImport = createArchiveInModuleWithinCurrentProject(false, String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY));

    assertEquals(getContainingModule(archiveToImport, project), ModuleManager.getInstance(project).findModuleByName(SOURCE_MODULE_NAME));
  }

  public void testGetContainingModuleNested() {
    Project project = getProject();
    File archiveToImport = createArchiveInModuleWithinCurrentProject(true, String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY));

    assertEquals(getContainingModule(archiveToImport, project), ModuleManager.getInstance(project).findModuleByName(SOURCE_MODULE_NAME));
  }

  public void testGetContainingModuleNotInModule() {
    assertEquals(getContainingModule(getJarNotInProject(), getProject()), null);
  }
}
