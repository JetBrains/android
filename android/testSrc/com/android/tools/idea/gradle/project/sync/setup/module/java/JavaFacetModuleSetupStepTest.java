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
package com.android.tools.idea.gradle.project.sync.setup.module.java;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacetConfiguration;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JavaFacetModuleSetupStep}.
 */
public class JavaFacetModuleSetupStepTest extends IdeaTestCase {
  private IdeModifiableModelsProvider myModelsProvider;
  private JavaFacetModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    mySetupStep = new JavaFacetModuleSetupStep();
  }

  public void testGradleModelNotFound() {
    createAndAddJavaFacet();

    Module module = getModule();
    mySetupStep.gradleModelNotFound(module, myModelsProvider);

    ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.commit());

    // JavaFacet should have been removed.
    assertNull(findJavaFacet(module));
  }

  public void testDoSetUpModuleWithExistingJavaFacet() throws IOException {
    createAndAddGradleFacet();

    JavaFacet facet = createAndAddJavaFacet();
    File buildFolderPath = createTempDir("build", true);
    boolean buildable = true;

    JavaModuleModel javaModel = mock(JavaModuleModel.class);
    when(javaModel.getBuildFolderPath()).thenReturn(buildFolderPath);
    when(javaModel.isBuildable()).thenReturn(buildable);

    Module module = getModule();
    mySetupStep.doSetUpModule(module, myModelsProvider, javaModel, null, null);

    ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.commit());

    // JavaFacet should be reused.
    assertSame(facet, findJavaFacet(module));

    verifyFacetConfiguration(facet, javaModel, buildFolderPath, buildable);
  }

  public void testDoSetUpModuleWithNewJavaFacet() throws IOException {
    createAndAddGradleFacet();

    File buildFolderPath = createTempDir("build", true);
    boolean buildable = true;

    JavaModuleModel javaModel = mock(JavaModuleModel.class);
    when(javaModel.getBuildFolderPath()).thenReturn(buildFolderPath);
    when(javaModel.isBuildable()).thenReturn(buildable);

    Module module = getModule();
    mySetupStep.doSetUpModule(module, myModelsProvider, javaModel, null, null);

    ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.commit());

    JavaFacet facet = findJavaFacet(module);
    assertNotNull(facet);

    verifyFacetConfiguration(facet, javaModel, buildFolderPath, buildable);
  }

  @Nullable
  private static JavaFacet findJavaFacet(@NotNull Module module) {
    FacetManager facetManager = FacetManager.getInstance(module);
    return facetManager.findFacet(JavaFacet.getFacetTypeId(), JavaFacet.getFacetName());
  }

  private static void verifyFacetConfiguration(@NotNull JavaFacet facet,
                                               @NotNull JavaModuleModel javaModel,
                                               @NotNull File buildFolderPath,
                                               boolean buildable) {
    assertSame(javaModel, facet.getJavaModuleModel());
    JavaFacetConfiguration configuration = facet.getConfiguration();
    assertEquals(toSystemIndependentName(buildFolderPath.getPath()), configuration.BUILD_FOLDER_PATH);
    assertEquals(buildable, configuration.BUILDABLE);

    verify(javaModel).getBuildFolderPath();
    verify(javaModel).isBuildable();
  }

  @NotNull
  private JavaFacet createAndAddJavaFacet() {
    // Add AndroidFacet to verify that is removed.
    FacetManager facetManager = FacetManager.getInstance(getModule());
    JavaFacet facet = facetManager.createFacet(JavaFacet.getFacetType(), JavaFacet.getFacetName(), null);
    addFacet(facet);
    return facet;
  }

  @NotNull
  private GradleFacet createAndAddGradleFacet() {
    // Add AndroidFacet to verify that is removed.
    FacetManager facetManager = FacetManager.getInstance(getModule());
    GradleFacet facet = facetManager.createFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null);
    addFacet(facet);
    return facet;
  }

  private void addFacet(@NotNull Facet<?> facet) {
    FacetManager facetManager = FacetManager.getInstance(getModule());
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableFacetModel facetModel = facetManager.createModifiableModel();
      facetModel.addFacet(facet);
      facetModel.commit();
    });
  }

  public void testDoSetUpModuleWithoutGradleFacet() throws IOException {
    File buildFolderPath = createTempDir("build", true);
    boolean buildable = true;

    JavaModuleModel javaModel = mock(JavaModuleModel.class);
    when(javaModel.getBuildFolderPath()).thenReturn(buildFolderPath);
    when(javaModel.isBuildable()).thenReturn(buildable);

    Module module = getModule();
    mySetupStep.doSetUpModule(module, myModelsProvider, javaModel, null, null);

    ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.commit());

    JavaFacet facet = findJavaFacet(module);
    assertNotNull(facet);
    assertNull(facet.getJavaModuleModel());
  }
}