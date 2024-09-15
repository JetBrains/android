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
package com.google.idea.blaze.android.libraries;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.base.Joiner;
import com.google.idea.blaze.android.filecache.ArtifactCache;
import com.google.idea.blaze.android.libraries.RenderJarCache.FileCacheAdapter;
import com.google.idea.blaze.android.sync.aspects.strategy.RenderResolveOutputGroupProvider;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifactWithoutDigest;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Kind.ApplicationState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.google.idea.blaze.java.sync.BlazeJavaSyncPlugin;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.google.idea.testing.IntellijRule;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Unit tests for {@link RenderJarCache} */
@RunWith(JUnit4.class)
public class RenderJarCacheTest {
  @Rule public final IntellijRule intellijRule = new IntellijRule();
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private ErrorCollector errorCollector;
  private WritingOutputSink outputSink;
  private BlazeContext context;
  private WorkspaceRoot workspaceRoot;
  private ArtifactLocationDecoder artifactLocationDecoder;
  private MockProjectViewManager projectViewManager;
  private ArtifactCache mockedArtifactCache;

  @Before
  public void initTest() throws IOException {
    errorCollector = new ErrorCollector();
    outputSink = new WritingOutputSink();
    context = BlazeContext.create();
    context.addOutputSink(PrintOutput.class, outputSink);
    workspaceRoot = new WorkspaceRoot(temporaryFolder.getRoot());
    artifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }
        };

    registerMockBlazeImportSettings();

    mockedArtifactCache = Mockito.mock(ArtifactCache.class);
    intellijRule.registerProjectService(
        RenderJarCache.class,
        new RenderJarCache(
            intellijRule.getProject(),
            RenderJarCache.getCacheDirForProject(intellijRule.getProject()),
            mockedArtifactCache));

    intellijRule.registerApplicationService(
        FileOperationProvider.class, new FileOperationProvider());
    intellijRule.registerApplicationService(
        RemoteArtifactPrefetcher.class, new DefaultPrefetcher());

    intellijRule.registerExtensionPoint(FileCache.EP_NAME, FileCache.class);
    intellijRule.registerExtension(FileCache.EP_NAME, new FileCacheAdapter());

    // Required to enable RenderJarClassFileFinder
    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setExperiment(RenderResolveOutputGroupProvider.buildOnSync, true);
    intellijRule.registerApplicationService(ExperimentService.class, experimentService);

    // Setup needed for setting a projectview
    intellijRule.registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
    intellijRule.registerExtension(BlazeSyncPlugin.EP_NAME, new BlazeJavaSyncPlugin());

    // RenderJarCache looks at targets of `Kind`s with LanguageClass.ANDROID
    // so we need to setup the framework for fetching a target's `Kind`
    intellijRule.registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    intellijRule.registerExtension(Kind.Provider.EP_NAME, new AndroidBlazeRules());
    intellijRule.registerApplicationService(ApplicationState.class, new ApplicationState());
    intellijRule.registerApplicationService(QuerySyncSettings.class, new QuerySyncSettings());

    // registered because `RenderJarCache` uses it to filter source targets
    projectViewManager = new MockProjectViewManager();
    intellijRule.registerProjectService(ProjectViewManager.class, projectViewManager);

    intellijRule.registerApplicationService(BlazeExecutor.class, new MockBlazeExecutor());

    setupProjectData();
    setProjectView(
        "directories:",
        "  com/foo/bar/baz",
        "  com/foo/bar/qux",
        "targets:",
        "  //com/foo/bar/baz:baz",
        "  //com/foo/bar/qux:quz");
  }

  /** Test that RenderJAR passes the correct JARs to ArtifactCache after a sync */
  @Test
  public void onSync_passesCorrectJarsToArtifactCache() {
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    intellijRule.getProject(),
                    context,
                    projectViewManager.getProjectViewSet(),
                    BlazeProjectDataManager.getInstance(intellijRule.getProject())
                        .getBlazeProjectData(),
                    null,
                    SyncMode.INCREMENTAL));

    @SuppressWarnings("unchecked") // irrelevant unchecked conversion warning for artifactsCaptor
    ArgumentCaptor<Collection<OutputArtifactWithoutDigest>> artifactsCaptor =
        ArgumentCaptor.forClass(Collection.class);

    ArgumentCaptor<BlazeContext> contextCaptor = ArgumentCaptor.forClass(BlazeContext.class);
    ArgumentCaptor<Boolean> removeCaptor = ArgumentCaptor.forClass(Boolean.class);

    verify(mockedArtifactCache, Mockito.times(1))
        .putAll(artifactsCaptor.capture(), contextCaptor.capture(), removeCaptor.capture());

    Collection<OutputArtifactWithoutDigest> passedArtifact = artifactsCaptor.getValue();
    assertThat(passedArtifact.stream().map(OutputArtifactWithoutDigest::getBazelOutRelativePath))
        .containsExactly(
            "k8-fast/bin/com/foo/bar/baz/baz_render_jar.jar", "k8-fast/bin/com/foo/bar/qux/qux_render_jar.jar");
  }

  /**
   * Sets up a mock {@link com.google.devtools.intellij.model.ProjectData} and creates the render
   * JARs in File System
   */
  private void setupProjectData() throws IOException {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//com/foo/bar/baz:baz")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setRenderResolveJar(
                                getArtifactLocation("com/foo/bar/baz/baz_render_jar.jar")))
                    .setKind(RuleTypes.KT_ANDROID_LIBRARY_HELPER.getKind()))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//com/foo/bar/qux:qux")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setRenderResolveJar(
                                getArtifactLocation("com/foo/bar/qux/qux_render_jar.jar")))
                    .setKind(RuleTypes.KT_ANDROID_LIBRARY_HELPER.getKind())
                    .build())
            .build();
    intellijRule.registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder()
                .setArtifactLocationDecoder(artifactLocationDecoder)
                .setTargetMap(targetMap)
                .build()));

    // Create baz_render_jar.jar in FS
    BlazeArtifact bazRenderJar =
        artifactLocationDecoder.resolveOutput(
            getArtifactLocation("com/foo/bar/baz/baz_render_jar.jar"));
    File bazRenderJarFile = ((LocalFileOutputArtifactWithoutDigest) bazRenderJar).getFile();
    assertThat(Paths.get(bazRenderJarFile.getParent()).toFile().mkdirs()).isTrue();
    assertThat(bazRenderJarFile.createNewFile()).isTrue();
    assertThat(bazRenderJarFile.setLastModified(100000L)).isTrue();

    // Create qux_render_jar.jar in FS
    BlazeArtifact quxRenderJar =
        artifactLocationDecoder.resolveOutput(
            getArtifactLocation("com/foo/bar/qux/qux_render_jar.jar"));
    File quxRenderJarFile = ((LocalFileOutputArtifactWithoutDigest) quxRenderJar).getFile();
    assertThat(Paths.get(quxRenderJarFile.getParent()).toFile().mkdirs()).isTrue();
    assertThat(quxRenderJarFile.createNewFile()).isTrue();
    assertThat(quxRenderJarFile.setLastModified(100000L)).isTrue();
  }

  /** Sets up {@link BlazeImportSettings} with a temporary directory for project data. */
  private void registerMockBlazeImportSettings() throws IOException {
    BlazeImportSettingsManager importSettingsManager =
        new BlazeImportSettingsManager(intellijRule.getProject());
    File projectDataDir = temporaryFolder.newFolder("project_data");
    importSettingsManager.setImportSettings(
        new BlazeImportSettings(
            /* workspaceRoot= */ "",
            intellijRule.getProject().getName(),
            projectDataDir.getAbsolutePath(),
            /* projectViewFile= */ "",
            BuildSystemName.Blaze,
            ProjectType.ASPECT_SYNC));
    intellijRule.registerProjectService(BlazeImportSettingsManager.class, importSettingsManager);
  }

  /** Utility method to create an {@link ArtifactLocation} for the given relative path */
  private ArtifactLocation getArtifactLocation(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment("bazel-out/k8-fast/bin")
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  /**
   * Updates the projectview for filtering source targets and clears the sync cache. Will fail if
   * {@code contents} is incorrectly formatted as a projectview file
   */
  private void setProjectView(String... contents) {
    BlazeContext context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    errorCollector.assertNoIssues();

    projectViewManager.setProjectView(result);
  }

  /** Utility class to log output from {@link RenderJarCache} for assertions */
  private static class WritingOutputSink implements OutputSink<PrintOutput> {

    private final Writer stringWriter = new StringWriter();
    private final PrintWriter writer = new PrintWriter(stringWriter);

    @Override
    public Propagation onOutput(PrintOutput output) {
      writer.println(output.getText());
      return Propagation.Continue;
    }
  }
}
