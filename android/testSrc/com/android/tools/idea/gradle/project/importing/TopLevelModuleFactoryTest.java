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
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import java.io.File;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.mockito.Mock;

/**
 * Tests for {@link TopLevelModuleFactory}.
 */
public class TopLevelModuleFactoryTest extends AndroidGradleTestCase {
  @Mock private IdeInfo myIdeInfo;

  private IdeSdks myIdeSdks;
  private TopLevelModuleFactory myTopLevelModuleFactory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myIdeSdks = IdeSdks.getInstance();
    myTopLevelModuleFactory = new TopLevelModuleFactory(myIdeInfo, myIdeSdks);
  }

  public void testCreateTopLevelModule() throws Exception {
    File projectRootFolderPath = prepareProjectForImport(SIMPLE_APPLICATION);

    Project project = getProject();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    assertThat(modules).isEmpty(); // Just make sure we start with a project with no modules.

    when(myIdeInfo.isAndroidStudio()).thenReturn(true); // Simulate is Android Studio.

    ApplicationManager.getApplication().runWriteAction(() -> myTopLevelModuleFactory.createTopLevelModule(project));

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
    assertEquals(myIdeSdks.getJdk().getHomePath(), sdk.getHomePath());

    // Verify module was marked as a "Gradle" module.
    String systemId = module.getOptionValue(EXTERNAL_SYSTEM_ID_KEY);
    assertEquals(GRADLE_SYSTEM_ID.getId(), systemId);

    ExternalSystemModulePropertyManager externalSystemProperties = ExternalSystemModulePropertyManager.getInstance(module);
    assertEquals(GRADLE_SYSTEM_ID.getId(), externalSystemProperties.getExternalSystemId());
    assertEquals(":", externalSystemProperties.getLinkedProjectId());
    assertEquals(projectRootFolderPath.getPath(), externalSystemProperties.getRootProjectPath());

    // Verify the module has a "Gradle" facet.
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    assertNotNull(gradleFacet);
    assertEquals(":", gradleFacet.getConfiguration().GRADLE_PROJECT_PATH); // ':' is the Gradle path of the root module.

    // Verify the module does not have an "Android" facet.
    assertNull(AndroidFacet.getInstance(module));
  }
}