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
package com.android.tools.idea.project;

import com.android.tools.idea.apk.debugging.ApkDebugging;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.List;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddApkFacet;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidProjectInfo}.
 */
public class AndroidProjectInfoTest extends HeavyPlatformTestCase {
  private AndroidProjectInfo myProjectInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectInfo = AndroidProjectInfo.getInstance(getProject());
  }

  public void testGetAllModulesofProjectType() {
    createAndAddAndroidFacet(getModule());
    List<Module> applicationModules = myProjectInfo.getAllModulesOfProjectType(PROJECT_TYPE_APP);
    assertThat(applicationModules).hasSize(1);
    assertEquals(getModule(), applicationModules.get(0));
  }

  public void testGetAllModulesofProjectTypeWithNone() {
    createAndAddAndroidFacet(getModule());
    assertEmpty(myProjectInfo.getAllModulesOfProjectType(PROJECT_TYPE_LIBRARY));
  }

  public void testGetAllModulesofProjectTypeWithNonAndroidModule() {
    assertEmpty(myProjectInfo.getAllModulesOfProjectType(PROJECT_TYPE_APP));
  }

  public void testRequiresAndroidModel() {
    AndroidFacet facet = createAndAddAndroidFacet(getModule());
    //noinspection deprecation
    facet.getProperties().ALLOW_USER_CONFIGURATION = false;
    assertTrue(ProjectSystemUtil.requiresAndroidModel(getProject()));
  }

  public void testRequiresAndroidModelWithUserConfigurableAndroidFacet() {
    AndroidFacet facet = createAndAddAndroidFacet(getModule());
    //noinspection deprecation
    facet.getProperties().ALLOW_USER_CONFIGURATION = true;
    assertFalse(ProjectSystemUtil.requiresAndroidModel(getProject()));
  }

  public void testRequiresAndroidModelWithModuleWithoutAndroidFacet() {
    assertFalse(ProjectSystemUtil.requiresAndroidModel(getProject()));
  }

  public void testDoesNotRequireAndroidModelWithApkFacet() {
    //Note: ApkFacet modules are configured with ALLOW_USER_CONFIGURATION = true and do not require android model.
    Module module = getModule();
    AndroidFacet facet = createAndAddAndroidFacet(module);

    createAndAddApkFacet(module);

    assertFalse(ProjectSystemUtil.requiresAndroidModel(getProject()));
  }

  public void testIsApkProject() {
    createAndAddApkFacet(getModule());
    assertTrue(myProjectInfo.isApkProject());
    assertTrue(ApkDebugging.isMarkedAsApkDebuggingProject(getProject()));
  }

  public void testIsApkProjectWithoutApkModules() {
    // Similar to the case causing http://b/64766060
    ApkDebugging.markAsApkDebuggingProject(getProject());
    assertTrue(myProjectInfo.isApkProject());
  }

  public void testIsApkProjectWithoutApkFacet() {
    assertFalse(myProjectInfo.isApkProject());
  }
}
