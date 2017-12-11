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

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Computable;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.gradle.project.sync.LibraryDependenciesSubject.libraryDependencies;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.createIfDoesntExist;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ArtifactsByConfigurationModuleSetupStep}.
 */
public class ArtifactsByConfigurationModuleSetupStepTest extends IdeaTestCase {

  private ArtifactsByConfigurationModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    mySetupStep = new ArtifactsByConfigurationModuleSetupStep();
  }

  public void testDoSetUpModule() throws IOException {
    Module module = getModule();

    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    File jarFilePath = createTempFile("fake.jar", "");
    Map<String, Set<File>> artifactsByConfiguration = new HashMap<>();
    artifactsByConfiguration.put("default", Collections.singleton(jarFilePath));

    JavaModuleModel model = new JavaModuleModel(module.getName(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                                artifactsByConfiguration, null, null, null, true, false);
    mySetupStep.doSetUpModule(module, modelsProvider, model, null, null);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    assertJarIsLibrary(jarFilePath);
  }

  public void testDoSetUpModuleWithExistingLibrary() throws IOException {
    File jarFilePath = createTempFile("fake.jar", "");

    // Create the library, to ensure that is marked as "used".
    Library library = createLibrary(jarFilePath);

    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    Map<String, Set<File>> artifactsByConfiguration = new HashMap<>();
    artifactsByConfiguration.put("default", Collections.singleton(jarFilePath));

    Module module = getModule();

    JavaModuleModel model = new JavaModuleModel(module.getName(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                                artifactsByConfiguration, null, null, null, true, false);
    mySetupStep.doSetUpModule(module, modelsProvider, model, null, null);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    assertJarIsLibrary(jarFilePath);
  }

  private Library createLibrary(@NotNull File jarFilePath) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(getProject());
    return ApplicationManager.getApplication().runWriteAction((Computable<Library>)() -> {
      Library library1 = libraryTable.createLibrary(createLibraryName(jarFilePath));
      Library.ModifiableModel libraryModel = library1.getModifiableModel();
      String url = pathToIdeaUrl(jarFilePath);
      libraryModel.addRoot(url, CLASSES);
      libraryModel.commit();
      return library1;
    });
  }

  private void assertJarIsLibrary(@NotNull File jarFilePath) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(getProject());
    Library[] libraries = libraryTable.getLibraries();
    assertThat(libraries).hasLength(1);

    Library library = libraries[0];
    String libraryName = library.getName();
    assertNotNull(libraryName);
    assertEquals(createLibraryName(jarFilePath), libraryName);

    String[] urls = library.getUrls(CLASSES);
    assertThat(urls).hasLength(1);
    assertEquals(pathToIdeaUrl(jarFilePath), urls[0]);

    assertAbout(libraryDependencies()).that(getModule()).contains(libraryName, COMPILE);
  }

  @NotNull
  private String createLibraryName(@NotNull File jarFilePath) {
    return "Gradle: " + getModule().getName() + "." + getNameWithoutExtension(jarFilePath);
  }

  public void testDoSetUpModuleWithCompiledJar() throws IOException {
    Module module = getModule();
    String moduleName = module.getName();

    File buildFolderPath = createTempDir("build");
    File jarFilePath = new File(buildFolderPath, moduleName + ".jar");
    createIfDoesntExist(jarFilePath);

    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    Map<String, Set<File>> artifactsByConfiguration = new HashMap<>();
    artifactsByConfiguration.put("default", Collections.singleton(jarFilePath));

    JavaModuleModel model =
      new JavaModuleModel(moduleName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), artifactsByConfiguration,
                          null, buildFolderPath, null, true, false);
    mySetupStep.doSetUpModule(module, modelsProvider, model, null, null);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    Library[] libraries = libraryTable.getLibraries();
    assertThat(libraries).isEmpty();

    assertAbout(libraryDependencies()).that(module).doesNotHaveDependencies();
  }
}