/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static java.util.Collections.singletonList;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LeakHunter;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Integration tests for 'Gradle Sync'.
 */
public class GradleSyncWithJpsPluginIntegrationTest extends GradleSyncIntegrationTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    String externalProjectPath = toCanonicalPath(project.getBasePath());
    projectSettings.setExternalProjectPath(externalProjectPath);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(singletonList(projectSettings));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Regression test: check the model doesn't hold on to dynamic proxies for Gradle Tooling API classes.
      Object model = DataNodeCaches.getInstance(getProject()).getCachedProjectData();
      if (model != null) {
        LeakHunter.checkLeak(model, Proxy.class, o -> Arrays.stream(
                o.getClass().getInterfaces()).anyMatch(clazz -> clazz.getName().contains("gradle.tooling")));
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testLegacySourceGenerationIsDisabled() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    AndroidFacet facet = AndroidFacet.getInstance(appModule);
    assertNotNull(facet);

    try {
      ModuleSourceAutogenerating.getInstance(facet);
      fail("Shouldn't be able to construct a source generator for Gradle projects");
    }
    catch (IllegalArgumentException e) {
      assertEquals(TestModuleUtil.findAppModule(getProject()).getName() +
                   " is built by an external build system and should not require the IDE to generate sources", e.getMessage());
    }
  }
}
