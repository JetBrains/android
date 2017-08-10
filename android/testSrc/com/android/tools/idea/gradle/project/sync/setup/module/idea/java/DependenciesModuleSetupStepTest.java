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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.project.sync.ModuleDependenciesSubject.moduleDependencies;
import static com.android.tools.idea.gradle.project.sync.setup.module.idea.java.DependenciesModuleSetupStep.getExported;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link DependenciesModuleSetupStep}.
 */
public class DependenciesModuleSetupStepTest extends IdeaTestCase {
  @Mock private JavaModuleDependenciesSetup myDependenciesSetup;
  @Mock private JavaModuleModel myJavaModuleModel;

  private IdeModifiableModelsProvider myModelsProvider;
  private DependenciesModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    mySetupStep = new DependenciesModuleSetupStep(myDependenciesSetup);
  }

  public void testSetUpModuleDependencyWithGradle3dot5() {
    // Verify that module dependency is not exported for gradle 3.5.
    verifySetupModuleDependency("3.5", false);
  }

  public void testSetUpModuleDependencyWithGradle2dot2() {
    // Verify that module dependency is exported for gradle 2.2.
    verifySetupModuleDependency("2.2", true);
  }

  private void verifySetupModuleDependency(@NotNull String gradleVersion, boolean exported) {
    String moduleName = "myLib";

    // These are the modules to add as dependency.
    createModule(moduleName);

    JavaModuleDependency exportableModuleDependency = new JavaModuleDependency(moduleName, "compile", true);

    Collection<JavaModuleDependency> moduleDependencies = new ArrayList<>();
    moduleDependencies.add(exportableModuleDependency);

    when(myJavaModuleModel.getJavaModuleDependencies()).thenReturn(moduleDependencies); // We only want module dependencies
    when(myJavaModuleModel.getJarLibraryDependencies()).thenReturn(Collections.emptyList());

    // Create GradleFacet and GradleModuleModel.
    createGradleFacetWithModuleModel(gradleVersion);

    Module mainModule = getModule();
    mySetupStep.setUpModule(mainModule, myModelsProvider, myJavaModuleModel, null, null);

    // needed to check if the module dependency was added.
    ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.commit());

    // See https://code.google.com/p/android/issues/detail?id=225923
    assertAbout(moduleDependencies()).that(mainModule).hasDependency(moduleName, COMPILE, exported);
  }

  public void testInvokeOnSkippedSync() {
    // Make sure this step is called even when sync was skipped see b/62292929
    assertTrue(mySetupStep.invokeOnSkippedSync());
  }

  public void testGetExportedWithoutModel() {
    // Verify exported is true when GradleModuleModel is null.
    assertTrue(getExported(createAndAddGradleFacet(myModule)));
  }

  public void testGetExportedWithGradle2_2() {
    // Verify exported is true when gradle version is 2.2.
    assertTrue(getExported(createGradleFacetWithModuleModel("2.2")));
  }

  public void testGetExportedWithGradle2_14() {
    // Verify exported is false when gradle version is 2.14.
    assertFalse(getExported(createGradleFacetWithModuleModel("2.14")));
  }

  public void testGetExportedWithGradle3_5() {
    // Verify exported is false when gradle version is 3.5.
    assertFalse(getExported(createGradleFacetWithModuleModel("3.5")));
  }

  @NotNull
  private GradleFacet createGradleFacetWithModuleModel(@NotNull String modelVersion) {
    GradleFacet facet = createAndAddGradleFacet(myModule);
    GradleModuleModel gradleModuleModel = mock(GradleModuleModel.class);
    facet.setGradleModuleModel(gradleModuleModel);
    when(gradleModuleModel.getGradleVersion()).thenReturn(modelVersion);
    return facet;
  }
}