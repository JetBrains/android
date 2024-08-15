/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import static com.android.ide.common.repository.GoogleMavenArtifactIdCompat.APP_COMPAT_V7;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.mock.MockModule;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.editor.LazyRangeMarkerFactory;
import com.intellij.openapi.editor.impl.LazyRangeMarkerFactoryImpl;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Test cases for {@link BlazeModuleSystem}. */
@RunWith(JUnit4.class)
public class BlazeModuleSystemTest extends BlazeTestCase {
  WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/"));
  Module module;
  BlazeProjectSystem service;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    ExtensionPointImpl<Kind.Provider> kindProvider =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    kindProvider.registerExtension(new AndroidBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
    applicationServices.register(QuerySyncSettings.class, new QuerySyncSettings());

    module = new MockModule(project, () -> {});

    // For the 'blaze.class.file.finder.name' experiment.
    applicationServices.register(ExperimentService.class, new MockExperimentService());

    mockBlazeImportSettings(projectServices); // For Blaze.isBlazeProject.
    createMocksForAddDependency(applicationServices, projectServices);

    project.setBaseDir(new MockVirtualFile("/"));
    service = new BlazeProjectSystem(project);
  }

  @Test
  public void testAddDependencyWithBuildTargetPsi() throws Exception {
    PsiElement buildTargetPsi = mock(PsiElement.class);
    PsiFile psiFile = mock(PsiFile.class);

    BuildReferenceManager buildReferenceManager = BuildReferenceManager.getInstance(project);
    when(buildReferenceManager.resolveLabel(Label.create("//foo:bar"))).thenReturn(buildTargetPsi);
    when(buildTargetPsi.getContainingFile()).thenReturn(psiFile);
    when(buildTargetPsi.getTextOffset()).thenReturn(1337);

    VirtualFile buildFile =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByPath("/foo/BUILD");
    assertThat(buildFile).isNotNull();
    when(psiFile.getVirtualFile()).thenReturn(buildFile);

    BlazeModuleSystem.create(module).registerDependency(APP_COMPAT_V7);

    ArgumentCaptor<OpenFileDescriptor> descriptorCaptor =
        ArgumentCaptor.forClass(OpenFileDescriptor.class);
    verify(FileEditorManager.getInstance(project))
        .openTextEditor(descriptorCaptor.capture(), eq(true));
    OpenFileDescriptor descriptor = descriptorCaptor.getValue();
    assertThat(descriptor.getProject()).isEqualTo(project);
    assertThat(descriptor.getFile()).isEqualTo(buildFile);
    assertThat(descriptor.getOffset()).isEqualTo(1337);
    verifyNoMoreInteractions(FileEditorManager.getInstance(project));
  }

  @Test
  public void testAddDependencyWithoutBuildTargetPsi() throws Exception {
    // Can't find PSI for the target.
    when(BuildReferenceManager.getInstance(project).resolveLabel(Label.create("//foo:bar")))
        .thenReturn(null);

    VirtualFile buildFile =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByPath("/foo/BUILD");
    assertThat(buildFile).isNotNull();

    BlazeModuleSystem.create(module).registerDependency(APP_COMPAT_V7);

    verify(FileEditorManager.getInstance(project)).openFile(buildFile, true);
    verifyNoMoreInteractions(FileEditorManager.getInstance(project));
  }

  @Test
  public void testGetResolvedDependencyWithoutLocators() throws Exception {
    registerExtensionPoint(MavenArtifactLocator.EP_NAME, MavenArtifactLocator.class);
    assertThat(BlazeModuleSystem.create(module).getResolvedDependency(APP_COMPAT_V7)).isNull();
  }

  @Test
  public void testGetDesugaringConfigFilesWithoutLocators() throws Exception {
    registerExtensionPoint(
        DesugaringLibraryConfigFilesLocator.EP_NAME, DesugaringLibraryConfigFilesLocator.class);
    assertThat(BlazeModuleSystem.create(module).getDesugarLibraryConfigFilesKnown()).isFalse();
    assertThat(BlazeModuleSystem.create(module).getDesugarLibraryConfigFiles()).isEmpty();
  }

  @Test
  public void testGetDesugaringConfigFiles() throws Exception {
    ImmutableList<Path> desugaringFilePaths =
        ImmutableList.of(Paths.get("a/a.json"), Paths.get("b/b.json"));
    ExtensionPointImpl<DesugaringLibraryConfigFilesLocator> extensionPointImpl =
        registerExtensionPoint(
            DesugaringLibraryConfigFilesLocator.EP_NAME, DesugaringLibraryConfigFilesLocator.class);
    extensionPointImpl.registerExtension(
        new DesugaringLibraryConfigFilesLocator() {
          @Override
          public boolean getDesugarLibraryConfigFilesKnown() {
            return true;
          }

          @Override
          public ImmutableList<Path> getDesugarLibraryConfigFiles(Project project) {
            return desugaringFilePaths;
          }

          @Override
          public BuildSystemName buildSystem() {
            return BuildSystemName.Blaze;
          }
        });
    assertThat(BlazeModuleSystem.create(module).getDesugarLibraryConfigFilesKnown()).isTrue();
    assertThat(BlazeModuleSystem.create(module).getDesugarLibraryConfigFiles())
        .isEqualTo(desugaringFilePaths);
  }

  @Test
  public void testBlazeTargetNameToKotlinModuleName() {
    assertThat(
            BlazeModuleSystem.create(module)
                .blazeTargetNameToKotlinModuleName(
                    "//third_party/java_src/android_app/compose_samples/Rally:lib"))
        .isEqualTo("third_party_java_src_android_app_compose_samples_Rally_lib");
  }

  private void mockBlazeImportSettings(Container projectServices) {
    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager(project);
    importSettingsManager.setImportSettings(
        new BlazeImportSettings("", "", "", "", BuildSystemName.Blaze, ProjectType.ASPECT_SYNC));
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);
  }

  private void createMocksForAddDependency(
      Container applicationServices, Container projectServices) {
    projectServices.register(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(createMockBlazeProjectData()));
    projectServices.register(FileEditorManager.class, mock(FileEditorManager.class));
    projectServices.register(BuildReferenceManager.class, mock(BuildReferenceManager.class));
    projectServices.register(LazyRangeMarkerFactory.class, mock(LazyRangeMarkerFactoryImpl.class));
    projectServices.register(
        BlazeLightResourceClassService.class, mock(BlazeLightResourceClassService.class));

    applicationServices.register(
        VirtualFileSystemProvider.class, new MockVirtualFileSystemProvider("/foo/BUILD"));

    AndroidResourceModuleRegistry moduleRegistry = new AndroidResourceModuleRegistry();
    moduleRegistry.put(
        module,
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//foo:bar"))).build());
    projectServices.register(AndroidResourceModuleRegistry.class, moduleRegistry);
  }

  private BlazeProjectData createMockBlazeProjectData() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(Label.create("//foo:bar"))
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .setBuildFile(ArtifactLocation.builder().setRelativePath("foo/BUILD").build())
                    .build())
            .build();
    ArtifactLocationDecoder decoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File("/", artifactLocation.getRelativePath());
          }
        };
    return MockBlazeProjectDataBuilder.builder(workspaceRoot)
        .setTargetMap(targetMap)
        .setArtifactLocationDecoder(decoder)
        .build();
  }

  private static class MockFileSystem extends TempFileSystem {
    private Map<String, VirtualFile> files;

    public MockFileSystem(String... paths) {
      files = Maps.newHashMap();
      for (String path : paths) {
        files.put(path, new MockVirtualFile(path));
      }
    }

    @Override
    public VirtualFile findFileByPath(String path) {
      return files.get(path);
    }

    @Override
    public VirtualFile findFileByPathIfCached(String path) {
      return findFileByPath(path);
    }

    @Override
    public VirtualFile findFileByIoFile(File file) {
      return findFileByPath(file.getPath());
    }
  }

  private static class MockVirtualFileSystemProvider implements VirtualFileSystemProvider {

    private final LocalFileSystem fileSystem;

    MockVirtualFileSystemProvider(String... paths) {
      fileSystem = new MockFileSystem(paths);
    }

    @Override
    public LocalFileSystem getSystem() {
      return fileSystem;
    }
  }
}
