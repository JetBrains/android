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
package com.android.tools.idea.gradle.variant.conflict;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.Projects.getBaseDirPath;

import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.stubs.android.AndroidArtifactStub;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.DependenciesStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;



public class ConflictResolutionTest extends PlatformTestCase {
  private AndroidProjectStub myAppModel;

  // All possible variants for the :app module.
  private VariantStub myAppDebugVariant;
  private VariantStub myAppReleaseVariant;

  private Module myLibModule;
  private String myLibGradlePath;
  private AndroidProjectStub myLibModel;

  // All possible variants for the :lib module.
  private VariantStub myLibDebugVariant;
  private VariantStub myLibReleaseVariant;

  private IdeDependenciesFactory myDependenciesFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myLibModule = createModule("lib");
    myLibGradlePath = ":lib";
    myDependenciesFactory = new IdeDependenciesFactory();
  }

  @Override
  protected void tearDown() throws Exception {
    myLibModule = null;
    super.tearDown();
  }

  public void testSolveSelectionConflict() {
    setUpModels();
    setUpDependencyOnLibrary();
    setUpModules(myAppReleaseVariant, myLibDebugVariant);

    // Variant selections before identifying the conflict.
    assertEquals("release", AndroidModuleModel.get(myModule).getSelectedVariant().getName());
    assertEquals("debug", AndroidModuleModel.get(myLibModule).getSelectedVariant().getName());

    List<Conflict> conflicts = ConflictSet.findConflicts(myProject).getSelectionConflicts();
    assertEquals(1, conflicts.size());

    // Source is the :lib module, which has "debug".
    Conflict conflict = conflicts.get(0);
    assertSame(myLibModule, conflict.getSource());
    assertSame("debug", conflict.getSelectedVariant());

    List<Conflict.AffectedModule> affectedModules = conflict.getAffectedModules();
    assertEquals(1, affectedModules.size());

    // Affected is the :app module, which has "release".
    Conflict.AffectedModule affectedModule = affectedModules.get(0);
    assertSame(myModule, affectedModule.getTarget());
    assertSame("release", affectedModule.getExpectedVariant());

    // We should fix the "source" (i.e., ":lib") and make it "release".
    assertTrue(ConflictResolution.solveSelectionConflict(conflict));

    // After fixing the conflict, the selected variants match.
    assertEquals("release", AndroidModuleModel.get(myModule).getSelectedVariant().getName());
    assertEquals("release", AndroidModuleModel.get(myLibModule).getSelectedVariant().getName());

    // After fixing the conflict, there are no more conflicts left.
    conflicts = ConflictSet.findConflicts(myProject).getSelectionConflicts();
    assertEquals(0, conflicts.size());
  }

  public void testSolveSelectionConflictWithABIs() {
    // TODO: Add tests for modules with NdkModuleModels.
  }

  private void setUpModels() {
    // Setup the model for app.
    myAppModel = new AndroidProjectStub("app");
    myAppDebugVariant = myAppModel.addVariant("debug");
    myAppReleaseVariant = myAppModel.addVariant("release");

    // Setup the model for lib.
    myLibModel = new AndroidProjectStub("lib");
    myLibModel.setProjectType(PROJECT_TYPE_LIBRARY);
    myLibDebugVariant = myLibModel.addVariant("debug");
    myLibReleaseVariant = myLibModel.addVariant("release");
  }

  /**
   * Makes the :app module depend on the :lib module, for both "debug" and "release" variants.
   */
  private void setUpDependencyOnLibrary() {
    // Debug variant.
    AndroidArtifactStub mainDebugArtifact = myAppDebugVariant.getMainArtifact();
    DependenciesStub debugDependencies = mainDebugArtifact.getDependencies();
    File debugJarFile = new File(myProject.getBasePath(), "file.jar");
    AndroidLibraryStub debugLib = new AndroidLibraryStub(debugJarFile, debugJarFile, myLibGradlePath, "debug");
    debugDependencies.addLibrary(debugLib);

    // Release variant.
    AndroidArtifactStub mainReleaseArtifact = myAppReleaseVariant.getMainArtifact();
    DependenciesStub releaseDependencies = mainReleaseArtifact.getDependencies();
    File releaseJarFile = new File(myProject.getBasePath(), "file.jar");
    AndroidLibraryStub lib = new AndroidLibraryStub(releaseJarFile, releaseJarFile, myLibGradlePath, "release");
    releaseDependencies.addLibrary(lib);
  }

  private void setUpModules(@NotNull VariantStub appVariant, @NotNull VariantStub libVariant) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      setUpMainModuleAsApp(appVariant);
      setUpLibModule(libVariant);
      setUpModuleDependencies();
    });
  }

  private void setUpMainModuleAsApp(@NotNull VariantStub appVariant) {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet facet = createFacet(facetManager, PROJECT_TYPE_APP);

      File rootDirPath = getBaseDirPath(myProject);
      AndroidModuleModel model =
        AndroidModuleModel.create(myModule.getName(), rootDirPath, myAppModel, appVariant.getName(), myDependenciesFactory);
      AndroidModel.set(facet, model);
      facetModel.addFacet(facet);
    }
    finally {
      facetModel.commit();
    }
  }

  private void setUpLibModule(@NotNull VariantStub libVariant) {
    FacetManager facetManager = FacetManager.getInstance(myLibModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet androidFacet = createFacet(facetManager, PROJECT_TYPE_LIBRARY);

      File moduleFilePath = new File(myLibModule.getModuleFilePath());
      AndroidModuleModel model = AndroidModuleModel
        .create(myModule.getName(), moduleFilePath.getParentFile(), myLibModel, libVariant.getName(), myDependenciesFactory);
      AndroidModel.set(androidFacet, model);

      facetModel.addFacet(androidFacet);

      GradleFacet gradleFacet = facetManager.createFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null);
      gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = myLibGradlePath;
      facetModel.addFacet(gradleFacet);
    }
    finally {
      facetModel.commit();
    }
  }

  @NotNull
  private static AndroidFacet createFacet(@NotNull FacetManager facetManager, int projectType) {
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    JpsAndroidModuleProperties facetState = facet.getProperties();
    facetState.ALLOW_USER_CONFIGURATION = false;
    facetState.PROJECT_TYPE = projectType;
    return facet;
  }

  private void setUpModuleDependencies() {
    // Make module depend on myLibModule.
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      rootModel.addModuleOrderEntry(myLibModule);
    }
    finally {
      rootModel.commit();
    }
  }
}
