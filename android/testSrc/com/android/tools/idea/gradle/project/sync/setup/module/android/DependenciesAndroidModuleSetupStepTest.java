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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependenciesExtractor;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesAndroidModuleSetupStep.isSelfDependencyByTest;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link DependenciesAndroidModuleSetupStep}.
 */
public class DependenciesAndroidModuleSetupStepTest extends IdeaTestCase {
  @Mock private DependenciesExtractor myDependenciesExtractor;
  @Mock private AndroidModuleDependenciesSetup myDependenciesSetup;

  private DependenciesAndroidModuleSetupStep mySetupStep;
  private VirtualFile myBuildFolder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySetupStep = new DependenciesAndroidModuleSetupStep(myDependenciesExtractor, myDependenciesSetup);
    myBuildFolder = createFolder(getModuleFolder(myModule), "build");
  }

  public void testUpdateLibraryDependencyWithPlugin2dot3() throws IOException {
    updateLibraryDependency("2.3.0", true);
  }

  public void testUpdateLibraryDependencyWithPlugin3dot0() throws IOException {
    updateLibraryDependency("3.0.0", false);
  }

  private void updateLibraryDependency(@NotNull String modelVersion, boolean exported) throws IOException {
    // Create gradle facet and mock AndroidModuleModel.
    AndroidModuleModel moduleModel = createAndroidFacetAndModuleModel(modelVersion);

    // Create "jars" folder inside the module's "build" folder.
    VirtualFile jarsFolder = createFolder(myBuildFolder, "jars");
    File jarsFolderPath = virtualToIoFile(jarsFolder);

    // We simulate the dependency to set up being inside the "jars" folder. We expect the "jars" folder to be excluded to avoid unnecessary
    // indexing of the jar files.
    LibraryDependency dependency = createFakeLibraryDependency(jarsFolderPath);

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    mySetupStep.updateLibraryDependency(myModule, modelsProvider, dependency, moduleModel);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Make sure DependenciesSetup#setUpLibraryDependency was invoked.
    verify(myDependenciesSetup).setUpLibraryDependency(myModule, modelsProvider, dependency.getName(), COMPILE, dependency.getArtifactPath(),
                                                       dependency.getBinaryPaths(), exported);
  }

  @NotNull
  private AndroidModuleModel createAndroidFacetAndModuleModel(@NotNull String modelVersion) {
    // Create mock IdeAndroidProject.
    createContentRoot(myModule);
    IdeAndroidProject androidProject = mock(IdeAndroidProject.class);
    when(androidProject.getBuildFolder()).thenReturn(virtualToIoFile(myBuildFolder));

    // Create mock AndroidModuleModel.
    AndroidFacet androidFacet = createAndAddAndroidFacet(myModule);
    AndroidModuleModel moduleModel = mock(AndroidModuleModel.class);
    GradleVersion version = GradleVersion.parse(modelVersion);
    when(moduleModel.getFeatures()).thenReturn(new AndroidModelFeatures(version));
    androidFacet.getConfiguration().setModel(moduleModel);
    when(moduleModel.getAndroidProject()).thenReturn(androidProject);
    return moduleModel;
  }

  private static void createContentRoot(@NotNull Module module) {
    VirtualFile moduleFolder = getModuleFolder(module);
    ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    modifiableRootModel.addContentEntry(moduleFolder);
    ApplicationManager.getApplication().runWriteAction(modifiableRootModel::commit);
  }

  @NotNull
  private VirtualFile createFolder(@NotNull VirtualFile parent, @NotNull String name) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(
      (ThrowableComputable<VirtualFile, IOException>)() -> parent.createChildDirectory(this, name));
  }

  @NotNull
  private static LibraryDependency createFakeLibraryDependency(@NotNull File jarsFolderPath) {
    return new LibraryDependency(new File(jarsFolderPath, "myLibrary.jar"), COMPILE);
  }

  @NotNull
  private static VirtualFile getModuleFolder(@NotNull Module module) {
    VirtualFile moduleFile = module.getModuleFile();
    assertNotNull(moduleFile);
    return moduleFile.getParent();
  }

  public void testUpdateModuleDependencyWithPlugin2dot3() {
    // Verify that module dependency is exported for plugin 2.3.
    updateModuleDependency("2.3.0", true);
  }

  public void testUpdateModuleDependencyWithPlugin3dot0() {
    // Verify that module dependency is not exported for plugin 3.0.
    updateModuleDependency("3.0.0", false);
  }

  private void updateModuleDependency(@NotNull String modelVersion, boolean exported) {
    String libModulePath = "mylib";
    // Create a lib module.
    Module libModule = createModule(libModulePath);
    GradleFacet facet = createAndAddGradleFacet(libModule);
    facet.getConfiguration().GRADLE_PROJECT_PATH = libModulePath;

    // Create gradle facet and mock AndroidModuleModel.
    AndroidModuleModel moduleModel = createAndroidFacetAndModuleModel(modelVersion);

    // Create module dependency on lib module.
    ModuleDependency dependency = new ModuleDependency(libModulePath, COMPILE, libModule);
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    mySetupStep.updateModuleDependency(myModule, modelsProvider, dependency, moduleModel);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Verify that there's only one module dependency.
    List<ModuleOrderEntry> moduleOrderEntries = getModuleOrderEntries(myModule);
    assertThat(moduleOrderEntries).hasSize(1);

    // Verify that module dependency is not exported. b/62265305.
    ModuleOrderEntry orderEntry = moduleOrderEntries.get(0);
    assertEquals(exported, orderEntry.isExported());
  }

  @NotNull
  private static List<ModuleOrderEntry> getModuleOrderEntries(@NotNull Module module) {
    List<ModuleOrderEntry> moduleOrderEntries = new ArrayList<>();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();

    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof ModuleOrderEntry) {
        moduleOrderEntries.add((ModuleOrderEntry)orderEntry);
      }
    }
    return moduleOrderEntries;
  }

  public void testIsSelfDependencyByTest() {
    String libModulePath = "lib";

    // Create a lib module.
    Module libModule = createModule(libModulePath);
    GradleFacet facet = createAndAddGradleFacet(libModule);
    facet.getConfiguration().GRADLE_PROJECT_PATH = libModulePath;

    // Create module dependency on lib module.
    ModuleDependency selfCompileDependency = new ModuleDependency(libModulePath, COMPILE, libModule);
    ModuleDependency selfTestDependency = new ModuleDependency(libModulePath, TEST, libModule);
    // Create module dependency on some other module.
    ModuleDependency otherDependency = new ModuleDependency("other", TEST, createModule("other"));

    // Verify that isSelfDependencyByTest is false for compile scope, and true for test scope.
    assertFalse(isSelfDependencyByTest(libModule, selfCompileDependency));
    assertTrue(isSelfDependencyByTest(libModule, selfTestDependency));
    // Verify that isSelfDependencyByTest is false non-self dependency.
    assertFalse(isSelfDependencyByTest(libModule, otherDependency));
  }
}