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

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
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
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.BINARY;
import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.DOCUMENTATION;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
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

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySetupStep = new DependenciesAndroidModuleSetupStep(myDependenciesExtractor, myDependenciesSetup);
  }

  public void testUpdateLibraryDependencyWithLibraryInModule() throws IOException {
    Module module = getModule();
    createContentRoot(module);

    VirtualFile moduleFolder = getModuleFolder(module);

    // Create "build" folder inside the module.
    String name = "build";
    VirtualFile buildFolder = createFolder(moduleFolder, name);

    AndroidProject androidProject = mock(AndroidProject.class);
    when(androidProject.getBuildFolder()).thenReturn(virtualToIoFile(buildFolder));

    // Create "jars" folder inside the module's "build" folder.
    VirtualFile jarsFolder = createFolder(buildFolder, "jars");
    File jarsFolderPath = virtualToIoFile(jarsFolder);

    // We simulate the dependency to set up being inside the "jars" folder. We expect the "jars" folder to be excluded to avoid unnecessary
    // indexing of the jar files.
    LibraryDependency dependency = createFakeLibraryDependency(jarsFolderPath);

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    mySetupStep.updateLibraryDependency(module, modelsProvider, dependency, androidProject);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Make sure DependenciesSetup#setUpLibraryDependency was invoked.
    verify(myDependenciesSetup).setUpLibraryDependency(module, modelsProvider, "myLibrary", COMPILE, dependency.getArtifactPath(),
                                                       dependency.getPaths(BINARY), dependency.getPaths(DOCUMENTATION));
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
    LibraryDependency dependency = new LibraryDependency(new File(jarsFolderPath, "myLibrary.jar"), COMPILE);
    dependency.addPath(DOCUMENTATION, new File(jarsFolderPath, "myLibrary-javadoc.jar"));
    return dependency;
  }

  @NotNull
  private static VirtualFile getModuleFolder(@NotNull Module module) {
    VirtualFile moduleFile = module.getModuleFile();
    assertNotNull(moduleFile);
    return moduleFile.getParent();
  }

  public void testUpdateModuleDependency() throws IOException {
    String libModulePath = ":lib";
    // Create a lib module.
    Module libModule = createModule(libModulePath);
    GradleFacet facet = createAndAddGradleFacet(libModule);
    facet.getConfiguration().GRADLE_PROJECT_PATH = libModulePath;

    // Create mock AndroidProject.
    createContentRoot(myModule);
    VirtualFile buildFolder = createFolder(getModuleFolder(myModule), "build");
    AndroidProject androidProject = mock(AndroidProject.class);
    when(androidProject.getBuildFolder()).thenReturn(virtualToIoFile(buildFolder));

    // Create module dependency on lib module.
    ModuleDependency dependency = new ModuleDependency(libModulePath, COMPILE);
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    mySetupStep.updateModuleDependency(myModule, modelsProvider, dependency, androidProject);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Verify that there's only one module dependency.
    List<ModuleOrderEntry> moduleOrderEntries = getModuleOrderEntries(myModule);
    assertThat(moduleOrderEntries).hasSize(1);

    // Verify that module dependency is not exported. b/62265305.
    ModuleOrderEntry orderEntry = moduleOrderEntries.get(0);
    assertFalse(orderEntry.isExported());
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
}