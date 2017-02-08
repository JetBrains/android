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
package com.android.tools.idea.model;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.*;
import com.intellij.testFramework.UsefulTestCase;

import java.util.Collections;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Tests for {@link IdeAndroidProject}.
 */
public class IdeAndroidProjectTest extends UsefulTestCase {
  private AndroidProject project = TestProjects.createFlavorsProject();
  private AndroidProject mySpyAndroidProject = spy(project);
  private IdeAndroidProject myIdeAndroidProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // Add objects to not supported methods on AndroidProjectStub
    ProductFlavorContainer spyDefaultConfig = spy(mySpyAndroidProject.getDefaultConfig());
    ProductFlavor ideProductFlavorStub = new IdeProductFlavorStub(spyDefaultConfig.getProductFlavor().getName());
    doReturn(ideProductFlavorStub).when(spyDefaultConfig).getProductFlavor();

    SourceProvider ideSourceProviderStub = new IdeSourceProviderStub(((SourceProviderStub)spyDefaultConfig.getSourceProvider()).getFileStructure(), "stubSourceProvider");
    doReturn(ideSourceProviderStub).when(spyDefaultConfig).getSourceProvider();

    doReturn(Collections.emptyList()).when(spyDefaultConfig).getExtraSourceProviders();

    doReturn(spyDefaultConfig).when(mySpyAndroidProject).getDefaultConfig();

    doReturn(Collections.emptyList()).when(mySpyAndroidProject).getBuildTypes();

    doReturn(Collections.emptyList()).when(mySpyAndroidProject).getProductFlavors();

    doReturn("BuildToolsVersion").when(mySpyAndroidProject).getBuildToolsVersion();

    doReturn(Collections.emptyList()).when(mySpyAndroidProject).getVariants();

    doReturn(Collections.emptyList()).when(mySpyAndroidProject).getExtraArtifacts();

    doReturn("CompileTarget").when(mySpyAndroidProject).getCompileTarget();

    doReturn(Collections.emptyList()).when(mySpyAndroidProject).getBootClasspath();

    doReturn(Collections.emptyList()).when(mySpyAndroidProject).getFrameworkSources();

    doReturn(Collections.emptyList()).when(mySpyAndroidProject).getNativeToolchains();

    AaptOptions ideAaptOptionsStub = new IdeAaptOptionsStub("AndroidProject_");
    doReturn(ideAaptOptionsStub).when(mySpyAndroidProject).getAaptOptions();

    LintOptions ideLintOptionsStub = new IdeLintOptionsStub();
    doReturn(ideLintOptionsStub).when(mySpyAndroidProject).getLintOptions();

    myIdeAndroidProject = new IdeAndroidProject(mySpyAndroidProject);

  }

  public void testCopyAndroidProject() throws AssertionError{
    //ToDo: Verify all getters return the same value
    assertEquals(mySpyAndroidProject.getAaptOptions(), myIdeAndroidProject.getAaptOptions());
    assertEquals(mySpyAndroidProject.getApiVersion(), myIdeAndroidProject.getApiVersion());
    assertSameElements(mySpyAndroidProject.getBootClasspath(), myIdeAndroidProject.getBootClasspath());
    assertEquals(mySpyAndroidProject.getBuildFolder(), myIdeAndroidProject.getBuildFolder());
    assertEquals(mySpyAndroidProject.getBuildToolsVersion(), myIdeAndroidProject.getBuildToolsVersion());
    assertSameElements(mySpyAndroidProject.getBuildTypes(), myIdeAndroidProject.getBuildTypes());
    assertEquals(mySpyAndroidProject.getCompileTarget(), myIdeAndroidProject.getCompileTarget());

    //assertEquals(mySpyAndroidProject.getDefaultConfig(), myIdeAndroidProject.getDefaultConfig());
    assertSameElements(mySpyAndroidProject.getDefaultConfig().getExtraSourceProviders(), myIdeAndroidProject.getDefaultConfig().getExtraSourceProviders());
    assertEquals(mySpyAndroidProject.getDefaultConfig().getProductFlavor(), myIdeAndroidProject.getDefaultConfig().getProductFlavor());
    assertEquals(mySpyAndroidProject.getDefaultConfig().getSourceProvider(), myIdeAndroidProject.getDefaultConfig().getSourceProvider());

    assertSameElements(mySpyAndroidProject.getExtraArtifacts(), myIdeAndroidProject.getExtraArtifacts());
    assertSameElements(mySpyAndroidProject.getFlavorDimensions(), myIdeAndroidProject.getFlavorDimensions());
    assertSameElements(mySpyAndroidProject.getFrameworkSources(), myIdeAndroidProject.getFrameworkSources());
    assertEquals(myIdeAndroidProject.getJavaCompileOptions(), mySpyAndroidProject.getJavaCompileOptions());
    assertEquals(myIdeAndroidProject.getLintOptions(), mySpyAndroidProject.getLintOptions());

    assertEquals(mySpyAndroidProject.getModelVersion(), myIdeAndroidProject.getModelVersion());
    assertEquals(mySpyAndroidProject.getName(), myIdeAndroidProject.getName());
    assertSameElements(mySpyAndroidProject.getNativeToolchains(), myIdeAndroidProject.getNativeToolchains());
    assertEquals(mySpyAndroidProject.getPluginGeneration(), myIdeAndroidProject.getPluginGeneration());
    assertSameElements(mySpyAndroidProject.getProductFlavors(), myIdeAndroidProject.getProductFlavors());
    assertEquals(mySpyAndroidProject.getProjectType(), myIdeAndroidProject.getProjectType());
    assertEquals(mySpyAndroidProject.getResourcePrefix(), myIdeAndroidProject.getResourcePrefix());
    assertSameElements(mySpyAndroidProject.getSigningConfigs(), myIdeAndroidProject.getSigningConfigs());
    assertSameElements(mySpyAndroidProject.getSyncIssues(), myIdeAndroidProject.getSyncIssues());
    assertSameElements(mySpyAndroidProject.getUnresolvedDependencies(), myIdeAndroidProject.getUnresolvedDependencies());
    assertSameElements(mySpyAndroidProject.getVariants(), myIdeAndroidProject.getVariants());
    assertEquals(mySpyAndroidProject.isLibrary(), myIdeAndroidProject.isLibrary());
  }
}
