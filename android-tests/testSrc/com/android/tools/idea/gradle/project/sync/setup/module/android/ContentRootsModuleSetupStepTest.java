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
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.project.facet.cpp.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.project.facet.cpp.NativeAndroidGradleFacetType;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ContentRootsModuleSetupStep}.
 */
public class ContentRootsModuleSetupStepTest extends IdeaTestCase {
  @Mock private AndroidContentEntriesSetup.Factory myFactory;
  @Mock private AndroidContentEntriesSetup mySetup;
  @Mock private AndroidGradleModel myAndroidModel;
  @Mock private AndroidProject myAndroidProject;

  private IdeModifiableModelsProvider myModelsProvider;
  private VirtualFile myModuleFolder;
  private ContentRootsModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myModuleFolder = getModuleFolder();
    VirtualFile buildFolder = findOrCreateChildFolder(myModuleFolder, "build");

    when(myAndroidProject.getBuildFolder()).thenReturn(virtualToIoFile(buildFolder));

    when(myAndroidModel.getAndroidProject()).thenReturn(myAndroidProject);
    when(myAndroidModel.getRootDir()).thenReturn(myModuleFolder);
    when(myAndroidModel.getRootDirPath()).thenReturn(virtualToIoFile(myModuleFolder));

    addContentRoots("a", "b", "c");

    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    mySetupStep = new ContentRootsModuleSetupStep(myFactory);
  }

  @NotNull
  private VirtualFile getModuleFolder() {
    VirtualFile imlFile = getModule().getModuleFile();
    assertNotNull(imlFile);
    return imlFile.getParent();
  }

  private void addContentRoots(@NotNull String... names) throws IOException {
    assertThat(names).isNotEmpty();

    VirtualFile projectFolder = getProject().getBaseDir();

    Module module = getModule();
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    ModifiableRootModel modifiableModel = manager.getModifiableModel();

    for (String name : names) {
      VirtualFile folder = findOrCreateChildFolder(projectFolder, name);
      modifiableModel.addContentEntry(folder);
    }

    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);

    ContentEntry[] entries = manager.getContentEntries();
    assertThat(entries).hasLength(names.length);
  }

  @NotNull
  private VirtualFile findOrCreateChildFolder(@NotNull VirtualFile folder, @NotNull String name) throws IOException {
    VirtualFile child = folder.findChild(name);
    if (child != null) {
      return child;
    }
    return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return folder.createChildDirectory(this, name);
      }
    });
  }

  public void testSetUpModule() {
    ModifiableRootModel moduleModel = myModelsProvider.getModifiableRootModel(getModule());
    when(myFactory.create(myAndroidModel, moduleModel, false)).thenReturn(mySetup);

    mySetupStep.setUpModule(getModule(), myModelsProvider, myAndroidModel, null, null);
    ContentEntry[] entries = moduleModel.getContentEntries();

    // Content roots "a", "b", and "c" should not be there, since they got removed.
    assertThat(entries).hasLength(1);
    assertEquals(myModuleFolder, entries[0].getFile());

    ApplicationManager.getApplication().runWriteAction(myModelsProvider::commit);

    verify(mySetup).execute(Arrays.asList(entries));
  }

  public void testSetUpNativeModule() {
    // Simulate this is a native module
    NativeAndroidGradleModel nativeAndroidModel = mock(NativeAndroidGradleModel.class);
    when(nativeAndroidModel.getSelectedVariant()).thenReturn(mock(NativeAndroidGradleModel.NativeVariant.class));

    NativeAndroidGradleFacet facet = addNativeAndroidFacet(myModelsProvider);
    facet.setNativeAndroidGradleModel(nativeAndroidModel);

    ModifiableRootModel moduleModel = myModelsProvider.getModifiableRootModel(getModule());
    when(myFactory.create(myAndroidModel, moduleModel, true)).thenReturn(mySetup);

    mySetupStep.setUpModule(getModule(), myModelsProvider, myAndroidModel, null, null);
    Map<String, ContentEntry> entriesByName = indexByName(moduleModel.getContentEntries());

    // Content roots "a", "b", and "c" should be there, since they did not get removed because this is a native model.
    assertThat(entriesByName).hasSize(4);
    assertThat(entriesByName).containsKey("a");
    assertThat(entriesByName).containsKey("b");
    assertThat(entriesByName).containsKey("c");

    ContentEntry newContentEntry = entriesByName.get(myModuleFolder.getName());
    assertNotNull(newContentEntry);

    ApplicationManager.getApplication().runWriteAction(myModelsProvider::commit);

    verify(mySetup).execute(Collections.singletonList(newContentEntry));
  }

  @NotNull
  NativeAndroidGradleFacet addNativeAndroidFacet(@NotNull IdeModifiableModelsProvider modelsProvider) {
    Module module = getModule();

    ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
    NativeAndroidGradleFacetType facetType = NativeAndroidGradleFacet.getFacetType();
    NativeAndroidGradleFacet facet =
      facetType.createFacet(module, NativeAndroidGradleFacet.NAME, facetType.createDefaultConfiguration(), null);

    model.addFacet(facet);

    return facet;
  }

  @NotNull
  private static Map<String, ContentEntry> indexByName(@NotNull ContentEntry[] contentEntries) {
    Map<String, ContentEntry> entriesByName = new HashMap<>();
    for (ContentEntry entry : contentEntries) {
      VirtualFile file = entry.getFile();
      assertNotNull(file);
      entriesByName.put(file.getName(), entry);
    }

    return entriesByName;
  }
}