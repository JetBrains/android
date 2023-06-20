/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;

/**
 * Tests for {@link TopLevelModuleFactory}.
 */
public class TopLevelModuleFactoryTest extends AndroidGradleTestCase {
  private TopLevelModuleFactory myTopLevelModuleFactory;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myTopLevelModuleFactory = new TopLevelModuleFactory();
  }

  public void testCreateTopLevelModule() throws Exception {
    File projectRootFolderPath = prepareProjectForImport(SIMPLE_APPLICATION);

    Project project = getProject();
    GradleProjectImporter.Companion.configureNewProject(project);
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    assertThat(modules).isEmpty(); // Just make sure we start with a project with no modules.

    ApplicationManager.getApplication().runWriteAction(() -> { myTopLevelModuleFactory.createOrConfigureTopLevelModule(project); });

    modules = moduleManager.getModules();

    // Verify we have a top-level module.
    assertThat(modules).hasLength(1);
    Module module = modules[0];
    assertEquals(getProject().getName(), module.getName());
    File moduleFilePath = AndroidRootUtil.findModuleRootFolderPath(module);
    assertEquals(projectRootFolderPath.getPath(), moduleFilePath.getPath());

    // Verify the module has a JDK.
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    assertNotNull(sdk);

    ExternalSystemModulePropertyManager externalSystemProperties = ExternalSystemModulePropertyManager.getInstance(module);
    assertEquals(GRADLE_SYSTEM_ID.getId(), externalSystemProperties.getExternalSystemId());
    assertEquals(":", externalSystemProperties.getLinkedProjectId());
    assertEquals(FileUtil.toSystemIndependentName(projectRootFolderPath.getPath()), externalSystemProperties.getRootProjectPath());

    // Verify the module has a "Gradle" facet.
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    assertNotNull(gradleFacet);

    // Verify the module does not have an "Android" facet.
    assertNull(AndroidFacet.getInstance(module));
  }
}