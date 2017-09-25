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
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependenciesExtractor;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.BINARY;
import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.DOCUMENTATION;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link DependenciesModuleSetupStep}.
 */
public class DependenciesModuleSetupStepTest extends IdeaTestCase {
  @Mock private DependenciesExtractor myDependenciesExtractor;
  @Mock private AndroidModuleDependenciesSetup myDependenciesSetup;

  private DependenciesModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySetupStep = new DependenciesModuleSetupStep(myDependenciesExtractor, myDependenciesSetup);
  }

  public void testGetInstance() {
    DependenciesModuleSetupStep actual = DependenciesModuleSetupStep.getInstance();
    assertNotNull(actual);

    DependenciesModuleSetupStep expected = null;
    for (AndroidModuleSetupStep step : AndroidModuleSetupStep.getExtensions()) {
      if (step instanceof DependenciesModuleSetupStep) {
        expected = (DependenciesModuleSetupStep)step;
        break;
      }
    }
    assertSame(expected, actual);
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
    verify(myDependenciesSetup).setUpLibraryDependency(module, modelsProvider, "Gradle: myLibrary", COMPILE, dependency.getArtifactPath(),
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
    return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return parent.createChildDirectory(DependenciesModuleSetupStepTest.this, name);
      }
    });
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
}