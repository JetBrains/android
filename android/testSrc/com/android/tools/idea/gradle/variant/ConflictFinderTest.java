/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.stubs.android.*;
import com.google.common.collect.ImmutableList;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.List;

/**
 * Tests for {@link ConflictFinder}
 */
public class ConflictFinderTest extends IdeaTestCase {
  private Module myLibModule;
  private IdeaAndroidProject myApp;
  private IdeaAndroidProject myLib;
  private String myLibGradlePath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myLibModule = createModule("lib");
    myLibGradlePath = ":lib";

    setUpApp();
    setUpLib();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        setUpMainModuleAsApp();
        setUpLibModule();
        setUpModuleDependencies();
      }
    });
  }

  private void setUpApp() {
    File rootDirPath = new File(myProject.getBasePath());

    AndroidProjectStub project = new AndroidProjectStub("app");
    VariantStub variant = project.addVariant("debug");

    myApp = new IdeaAndroidProject(myModule.getName(), rootDirPath, project, variant.getName());
  }

  private void setUpLib() {
    File moduleFilePath = new File(myLibModule.getModuleFilePath());

    AndroidProjectStub project = new AndroidProjectStub("lib");
    project.setIsLibrary(true);
    VariantStub variant = project.addVariant("debug");

    myLib = new IdeaAndroidProject(myModule.getName(), moduleFilePath.getParentFile(), project, variant.getName());
  }

  private void setUpMainModuleAsApp() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet facet = createFacet(facetManager, false);
      facet.setIdeaAndroidProject(myApp);
      facetModel.addFacet(facet);
    }
    finally {
      facetModel.commit();
    }
  }

  private void setUpLibModule() {
    FacetManager facetManager = FacetManager.getInstance(myLibModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet androidFacet = createFacet(facetManager, true);
      androidFacet.setIdeaAndroidProject(myLib);
      facetModel.addFacet(androidFacet);

      AndroidGradleFacet gradleFacet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
      gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = myLibGradlePath;
      facetModel.addFacet(gradleFacet);
    }
    finally {
      facetModel.commit();
    }
  }

  @NotNull
  private static AndroidFacet createFacet(@NotNull FacetManager facetManager, boolean library) {
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    JpsAndroidModuleProperties facetState = facet.getProperties();
    facetState.ALLOW_USER_CONFIGURATION = false;
    facetState.LIBRARY_PROJECT = library;
    return facet;
  }

  private void setUpModuleDependencies() {
    // Make myModule depend on myLibModule.
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      rootModel.addModuleOrderEntry(myLibModule);
    }
    finally {
      rootModel.commit();
    }
  }

  public void testFindSelectionConflictsWithoutConflict() {
    setUpDependencyOnLibrary("debug");
    ImmutableList<Conflict> conflicts = ConflictFinder.findConflicts(myProject).getSelectionConflicts();
    assertTrue(conflicts.isEmpty());
  }

  public void testFindSelectionConflictsWithoutEmptyVariantDependency() {
    setUpDependencyOnLibrary("");
    ImmutableList<Conflict> conflicts = ConflictFinder.findConflicts(myProject).getSelectionConflicts();
    assertTrue(conflicts.isEmpty());
  }

  public void testFindSelectionConflictsWithoutNullVariantDependency() {
    setUpDependencyOnLibrary(null);
    ImmutableList<Conflict> conflicts = ConflictFinder.findConflicts(myProject).getSelectionConflicts();
    assertTrue(conflicts.isEmpty());
  }

  public void testFindSelectionConflictsWithConflict() {
    setUpDependencyOnLibrary("release");
    ImmutableList<Conflict> conflicts = ConflictFinder.findConflicts(myProject).getSelectionConflicts();
    assertEquals(1, conflicts.size());

    Conflict conflict = conflicts.get(0);
    assertSame(myLibModule, conflict.getSource());
    assertSame("debug", conflict.getSelectedVariant());

    List<Conflict.AffectedModule> affectedModules = conflict.getAffectedModules();
    assertEquals(1, affectedModules.size());

    Conflict.AffectedModule affectedModule = affectedModules.get(0);
    assertSame(myModule, affectedModule.getTarget());
    assertSame("release", affectedModule.getExpectedVariant());
  }

  private void setUpDependencyOnLibrary(@Nullable String projectVariant) {
    VariantStub selectedVariant = (VariantStub)myApp.getSelectedVariant();
    AndroidArtifactStub mainArtifact = selectedVariant.getMainArtifact();
    DependenciesStub dependencies = mainArtifact.getDependencies();
    File jarFile = new File(myProject.getBasePath(), "file.jar");
    AndroidLibraryStub lib = new AndroidLibraryStub(jarFile, jarFile, myLibGradlePath, projectVariant);
    dependencies.addLibrary(lib);
  }
}
