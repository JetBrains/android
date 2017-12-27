/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import java.io.File;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleSyncFailureHandler}.
 */
public class GradleSyncFailureHandlerTest extends AndroidGradleTestCase {
  @Mock private IdeInfo myIdeInfo;

  private IdeSdks myIdeSdks;
  private GradleSyncFailureHandler mySyncFailureHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myIdeSdks = IdeSdks.getInstance();
    mySyncFailureHandler = new GradleSyncFailureHandler(myIdeInfo, myIdeSdks);
  }

  public void testCreateTopLevelModule() throws Exception {
    File projectRootFolderPath = prepareProjectForImport(SIMPLE_APPLICATION);

    Project project = getProject();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    assertThat(modules).isEmpty(); // Just make sure we start with a project with no modules.

    when(myIdeInfo.isAndroidStudio()).thenReturn(true); // Simulate is Android Studio.

    ApplicationManager.getApplication().runWriteAction(() -> mySyncFailureHandler.createTopLevelModule(project));

    modules = moduleManager.getModules();

    // Verify we have a top-level module.
    assertThat(modules).hasLength(1);
    Module module = modules[0];
    File moduleFilePath = new File(module.getModuleFilePath());
    assertEquals(projectRootFolderPath.getPath(), moduleFilePath.getParent());

    // Verify the module has a JDK.
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    assertNotNull(sdk);
    assertEquals(myIdeSdks.getJdk().getHomePath(), sdk.getHomePath());

    // Verify module was marked as a "Gradle" module.
    String systemId = module.getOptionValue(EXTERNAL_SYSTEM_ID_KEY);
    assertEquals(GRADLE_SYSTEM_ID.getId(), systemId);

    // Verify the module has a "Gradle" facet.
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    assertNotNull(gradleFacet);
    assertEquals(":", gradleFacet.getConfiguration().GRADLE_PROJECT_PATH); // ':' is the Gradle path of the root module.

    // Verify the module does not have an "Android" facet.
    assertNull(AndroidFacet.getInstance(module));
  }
}