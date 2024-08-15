/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProviderWrapper;
import com.google.idea.blaze.base.bazel.FakeBuildSystem;
import com.google.idea.blaze.base.bazel.FakeBuildSystemProvider;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob.GlobSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.libraries.BlazeLibrarySorter;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncPlugin;
import com.google.idea.blaze.java.sync.importer.emptylibrary.EmptyJarTracker;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link JarCache}. */
@RunWith(JUnit4.class)
public class JarCacheTest extends BlazeTestCase {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private WorkspaceRoot workspaceRoot;
  private BlazeContext context;
  private FakeJarRepackager fakeJarRepackager;
  private static final String PLUGIN_PROCESSOR_JAR = "pluginProcessor.jar";

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    context = BlazeContext.create();
    context.addOutputSink(PrintOutput.class, new WritingOutputSink());
    workspaceRoot = new WorkspaceRoot(folder.getRoot());
    fakeJarRepackager = new FakeJarRepackager();

    BlazeImportSettingsManager blazeImportSettingsManager = new BlazeImportSettingsManager(project);
    try {
      File projectDataDirectory = folder.newFolder("projectdata");
      BlazeImportSettings dummyImportSettings =
          new BlazeImportSettings(
              "",
              "",
              projectDataDirectory.getAbsolutePath(),
              "",
              BuildSystemName.Blaze,
              ProjectType.ASPECT_SYNC);
      blazeImportSettingsManager.setImportSettings(dummyImportSettings);
    } catch (IOException e) {
      throw new AssertionError("Failed to create a directory for test", e);
    }
    projectServices.register(BlazeImportSettingsManager.class, blazeImportSettingsManager);
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    applicationServices.register(RemoteArtifactPrefetcher.class, new DefaultPrefetcher());
    projectServices.register(JarCacheFolderProvider.class, new JarCacheFolderProvider(project));
    applicationServices.register(JarRepackager.class, fakeJarRepackager);
    JarCache jarCache = new JarCache(project);
    jarCache.enableForTest();
    projectServices.register(JarCache.class, jarCache);
    registerExtensionPoint(FileCache.EP_NAME, FileCache.class)
        .registerExtension(new JarCache.FileCacheAdapter(), testDisposable);
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class)
        .registerExtension(new BlazeJavaSyncPlugin(), testDisposable);
    registerExtensionPoint(BlazeLibrarySorter.EP_NAME, BlazeLibrarySorter.class);
    applicationServices.register(BlazeJavaUserSettings.class, new BlazeJavaUserSettings());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());
  }

  @Override
  protected BuildSystemProvider createBuildSystemProvider() {
    return new BuildSystemProviderWrapper(
        FakeBuildSystemProvider.builder()
            .setBuildSystem(FakeBuildSystem.builder(BuildSystemName.Blaze).build())
            .build());
  }

  @Test
  public void refresh_incrementalSync_localArtifact_lintJarCached() throws IOException {
    ArtifactLocationDecoder localArtifactLocationDecoder =
        new MockArtifactLocationDecoder(workspaceRoot.directory(), /* isRemote= */ false);
    testRefreshLintJarCached(localArtifactLocationDecoder, SyncMode.INCREMENTAL);
  }

  @Test
  public void refresh_fullSync_localArtifact_lintJarCached() throws IOException {
    ArtifactLocationDecoder localArtifactLocationDecoder =
        new MockArtifactLocationDecoder(workspaceRoot.directory(), /* isRemote= */ false);
    testRefreshLintJarCached(localArtifactLocationDecoder, SyncMode.FULL);
  }

  @Test
  public void refresh_incrementalSync_remoteArtifact_lintJarCached() throws IOException {
    ArtifactLocationDecoder remoteArtifactLocationDecoder =
        new MockArtifactLocationDecoder(workspaceRoot.directory(), /* isRemote= */ true);
    testRefreshLintJarCached(remoteArtifactLocationDecoder, SyncMode.INCREMENTAL);
  }

  @Test
  public void refresh_fullSync_remoteArtifact_lintJarCached() throws IOException {
    ArtifactLocationDecoder remoteArtifactLocationDecoder =
        new MockArtifactLocationDecoder(workspaceRoot.directory(), /* isRemote= */ true);
    testRefreshLintJarCached(remoteArtifactLocationDecoder, SyncMode.FULL);
  }

  /**
   * Create a test project that has store jar in {@code PluginProcessorJars} of {@code
   * BlazeJavaImportResult}
   */
  private BlazeProjectData setupProjectWithLintRuleJar(File jar, ArtifactLocationDecoder decoder)
      throws IOException {

    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(jar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/gen/Gen.java"));
      zo.write("package gen; class Gen {}".getBytes(UTF_8));
      zo.closeEntry();
    }

    ArtifactLocation lintJarArtifactLocation =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment(workspaceRoot.directory().getAbsolutePath())
            .setRelativePath(PLUGIN_PROCESSOR_JAR)
            .setIsSource(false)
            .build();

    LibraryArtifact libraryArtifact =
        LibraryArtifact.builder().setInterfaceJar(lintJarArtifactLocation).build();

    BlazeJavaImportResult importResult =
        BlazeJavaImportResult.builder()
            .setContentEntries(ImmutableList.of())
            .setLibraries(ImmutableMap.of())
            .setBuildOutputJars(ImmutableList.of())
            .setJavaSourceFiles(ImmutableSet.of())
            .setSourceVersion(null)
            .setEmptyJarTracker(EmptyJarTracker.builder().build())
            .setPluginProcessorJars(ImmutableSet.of(libraryArtifact.jarForIntellijLibrary()))
            .build();
    BlazeJavaSyncData syncData =
        new BlazeJavaSyncData(importResult, new GlobSet(ImmutableList.of()));
    return MockBlazeProjectDataBuilder.builder(workspaceRoot)
        .setWorkspaceLanguageSettings(
            new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of(LanguageClass.JAVA)))
        .setSyncState(new SyncState.Builder().put(syncData).build())
        .setArtifactLocationDecoder(decoder)
        .build();
  }

  @Test
  public void refresh_lintJarCachedAndRepackaged()
      throws IOException, ExecutionException, InterruptedException {
    ArtifactLocationDecoder artifactLocationDecoder =
        new MockArtifactLocationDecoder(workspaceRoot.directory(), /* isRemote= */ true);
    // arrange: set up a project that have PluginProcessorJars and register a fake repackager that
    // will repackage jars
    fakeJarRepackager.setEnable(true);
    File jar = workspaceRoot.fileForPath(new WorkspacePath(PLUGIN_PROCESSOR_JAR));
    BlazeProjectData blazeProjectData = setupProjectWithLintRuleJar(jar, artifactLocationDecoder);

    // act: refresh all the file caches, which in turn will fetch the plugin processor jar to local
    // and use FakeJarRepackager to repackage it
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.FULL));

    // assert
    File cacheDir = JarCacheFolderProvider.getInstance(project).getJarCacheFolder();
    JarCache.getInstance(project).getRepackagingTasks().get();
    File[] cachedFiles = cacheDir.listFiles();

    assertThat(cachedFiles).hasLength(2);
    assertThat(
            stream(cachedFiles)
                .filter(
                    file ->
                        !file.getName().startsWith(fakeJarRepackager.getRepackagePrefix())
                            && new File(
                                    cacheDir,
                                    fakeJarRepackager.getRepackagePrefix() + file.getName())
                                .exists())
                .count())
        .isEqualTo(1);

    for (File file : cachedFiles) {
      byte[] actualJarContent = Files.readAllBytes(file.toPath());
      byte[] expectedJarContent = Files.readAllBytes(jar.toPath());
      assertThat(actualJarContent).isEqualTo(expectedJarContent);
    }
  }

  /**
   * This test sets up blaze project data with a single java import result. It verifies that when
   * the file caches are refreshed, the jar cache correctly caches the lint jars.
   */
  private void testRefreshLintJarCached(ArtifactLocationDecoder decoder, SyncMode syncMode)
      throws IOException {
    // arrange: set up a project that have PluginProcessorJars
    File jar = workspaceRoot.fileForPath(new WorkspacePath(PLUGIN_PROCESSOR_JAR));
    BlazeProjectData blazeProjectData = setupProjectWithLintRuleJar(jar, decoder);

    // act: refresh all the file caches, which in turn will fetch the plugin processor jar to local
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    syncMode));

    // assert: verify that the plugin processor jar was extracted from the cache, and that it has
    // the right contents.
    File cacheDir = JarCacheFolderProvider.getInstance(project).getJarCacheFolder();

    assertThat(cacheDir.list()).hasLength(1);
    byte[] actualJarContent = Files.readAllBytes(cacheDir.listFiles()[0].toPath());
    byte[] expectedJarContent = Files.readAllBytes(jar.toPath());
    assertThat(actualJarContent).isEqualTo(expectedJarContent);
  }

  private static class WritingOutputSink implements OutputSink<PrintOutput> {

    private final Writer writer = new StringWriter();

    @Override
    public Propagation onOutput(PrintOutput output) {
      try {
        writer.write(output.getText());
        return Propagation.Continue;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class FakeJarRepackager implements JarRepackager {
    private boolean enabled = false;
    public static final String PREFIX = "repackaged_";

    public void setEnable(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public String getRepackagePrefix() {
      return PREFIX;
    }

    @Override
    public void processJar(File jar) throws IOException {
      Path source = jar.toPath();
      Path destination = source.resolveSibling(PREFIX + jar.getName());
      Files.copy(
          source,
          destination,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
    }
  }
}
