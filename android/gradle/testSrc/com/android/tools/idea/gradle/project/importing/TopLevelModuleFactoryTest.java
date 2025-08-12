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

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

/**
 * Tests for {@link TopLevelModuleFactory}.
 */
@RunsInEdt
public class TopLevelModuleFactoryTest {
  AndroidProjectRule projectRule = AndroidProjectRule.withAndroidModels();
  @Rule
  public TestRule rule = RuleChain.outerRule(projectRule).around(new EdtRule());

  private TopLevelModuleFactory myTopLevelModuleFactory;

  @Before
  public void setup() throws Exception {
    myTopLevelModuleFactory = new TopLevelModuleFactory();
  }

  @Test
  public void testCreateTopLevelModule() throws Exception {
    Project project = projectRule.getProject();
    File projectRootFolderPath = new File(project.getBasePath());
    GradleProjectImporter.Companion.configureNewProject(project);
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    assertThat(modules).isEmpty(); // Just make sure we start with a project with no modules.

    ApplicationManager.getApplication().runWriteAction(() -> { myTopLevelModuleFactory.createOrConfigureTopLevelModule(project); });

    modules = moduleManager.getModules();

    // Verify we have a top-level module.
    assertThat(modules).hasLength(1);
    Module module = modules[0];
    assertThat(module.getName()).isEqualTo(project.getName());
    File moduleFilePath = AndroidRootUtil.findModuleRootFolderPath(module);
    assertThat(moduleFilePath.getPath()).isEqualTo(projectRootFolderPath.getPath());

    // Verify the module has a JDK.
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    assertThat(sdk).isNotNull();

    ExternalSystemModulePropertyManager externalSystemProperties = ExternalSystemModulePropertyManager.getInstance(module);
    assertThat(externalSystemProperties.getExternalSystemId()).isEqualTo(GRADLE_SYSTEM_ID.getId());
    assertThat(externalSystemProperties.getLinkedProjectId()).isEqualTo(":");
    assertThat(externalSystemProperties.getRootProjectPath()).isEqualTo(FileUtil.toSystemIndependentName(projectRootFolderPath.getPath()));

    // Verify the module does not have a "Gradle" facet.
    assertThat(GradleFacet.getInstance(module)).isNull();

    // Verify the module does not have an "Android" facet.
    assertThat(AndroidFacet.getInstance(module)).isNull();
  }
}