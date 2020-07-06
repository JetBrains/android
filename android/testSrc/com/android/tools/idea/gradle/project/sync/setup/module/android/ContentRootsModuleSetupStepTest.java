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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacetType;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.NdkVariant;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link ContentRootsModuleSetupStep}.
 */
public class ContentRootsModuleSetupStepTest extends HeavyPlatformTestCase {
  @Mock private AndroidContentEntriesSetup.Factory myFactory;
  @Mock private AndroidContentEntriesSetup mySetup;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myAndroidProject;

  private IdeModifiableModelsProvider myModelsProvider;
  private VirtualFile myModuleFolder;
  private ContentRootsModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Path dir = getModule().getModuleNioFile().getParent();
    Files.createDirectories(dir);
    VirtualFile virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir);
    myModuleFolder = virtualDir;
    VirtualFile buildFolder = findOrCreateChildFolder(virtualDir, "build");

    when(myAndroidProject.getBuildFolder()).thenReturn(buildFolder.toNioPath().toFile());
    when(myAndroidModel.getAndroidProject()).thenReturn(myAndroidProject);
    when(myAndroidModel.getRootDirPath()).thenReturn(dir.toFile());

    addContentRoots("a", "b", "c");

    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    mySetupStep = new ContentRootsModuleSetupStep(myFactory);
  }

  private void addContentRoots(@NotNull String... names) throws IOException {
    assertThat(names).isNotEmpty();

    VirtualFile projectFolder = getOrCreateProjectBaseDir();

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

    ModuleSetupContext context = new ModuleSetupContext.Factory().create(getModule(), myModelsProvider);
    mySetupStep.setUpModule(context, myAndroidModel);
    ContentEntry[] entries = moduleModel.getContentEntries();

    // Content roots "a", "b", and "c" should not be there, since they got removed.
    assertThat(entries).hasLength(1);
    assertEquals(myModuleFolder, entries[0].getFile());

    ApplicationManager.getApplication().runWriteAction(myModelsProvider::commit);

    verify(mySetup).execute(Arrays.asList(entries));
  }

  public void testSetUpNativeModule() {
    // Simulate this is a native module
    NdkModuleModel ndkModuleModel = mock(NdkModuleModel.class);
    when(ndkModuleModel.getSelectedVariant()).thenReturn(mock(NdkVariant.class));

    NdkFacet facet = addNativeAndroidFacet(myModelsProvider);
    facet.setNdkModuleModel(ndkModuleModel);

    ModifiableRootModel moduleModel = myModelsProvider.getModifiableRootModel(getModule());
    when(myFactory.create(myAndroidModel, moduleModel, true)).thenReturn(mySetup);

    ModuleSetupContext context = new ModuleSetupContext.Factory().create(getModule(), myModelsProvider);
    mySetupStep.setUpModule(context, myAndroidModel);
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
  private NdkFacet addNativeAndroidFacet(@NotNull IdeModifiableModelsProvider modelsProvider) {
    Module module = getModule();

    ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
    NdkFacetType facetType = NdkFacet.getFacetType();
    NdkFacet facet =
      facetType.createFacet(module, NdkFacet.getFacetName(), facetType.createDefaultConfiguration(), null);

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