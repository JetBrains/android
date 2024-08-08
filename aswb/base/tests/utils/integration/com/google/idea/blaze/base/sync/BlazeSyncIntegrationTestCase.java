/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.MockEventLoggingService;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.AspectSyncProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.testing.ServiceHelper;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.lang.JavaVersion;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;

/** Sets up mocks required for integration tests of the blaze sync process. */
public abstract class BlazeSyncIntegrationTestCase extends BlazeIntegrationTestCase {

  private static final String DEFAULT_CONFIGURATION =
      "gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild";
  // Tests involving Java workspaces should operate on this fixed Java version.
  private static final JavaVersion JAVA_VERSION = LanguageLevel.JDK_1_9.toJavaVersion();
  // Mimic JavaLanguageLevelSection.KEY as we don't want to add a dependency on the whole :java
  // package for this language-independent class.
  private static final SectionKey<Integer, ScalarSection<Integer>> JAVA_LANGUAGE_LEVEL_SECTION_KEY =
      new SectionKey<>("java_language_level");

  private Disposable thisClassDisposable; // disposed prior to calling parent class's @After methods
  private MockProjectViewManager projectViewManager;
  private MockBlazeInfoRunner blazeInfoData;
  private MockBlazeIdeInterface blazeIdeInterface;
  private MockEventLoggingService eventLogger;
  @Nullable private ProjectModuleMocker moduleMocker; // this will be null for heavy test cases

  protected ErrorCollector errorCollector;
  protected String execRoot;

  @Before
  public void doSetup() throws Throwable {
    thisClassDisposable = Disposer.newDisposable();
    projectViewManager = new MockProjectViewManager(getProject());
    ServiceHelper.registerExtension(
        BlazeVcsHandlerProvider.EP_NAME, new MockBlazeVcsHandlerProvider(), thisClassDisposable);
    blazeInfoData = new MockBlazeInfoRunner();
    blazeIdeInterface = new MockBlazeIdeInterface();
    eventLogger = new MockEventLoggingService(thisClassDisposable);
    if (isLightTestCase()) {
      moduleMocker = new ProjectModuleMocker(getProject(), thisClassDisposable);
    }
    registerApplicationService(BlazeInfoRunner.class, blazeInfoData);
    registerApplicationService(BlazeIdeInterface.class, blazeIdeInterface);

    errorCollector = new ErrorCollector();

    fileSystem.createDirectory(projectDataDirectory.getPath() + "/.blaze/modules");

    // absolute file paths depend on #isLightTestCase, so can't be determined statically
    String outputBase = fileSystem.createDirectory("output_base").getPath();
    execRoot = fileSystem.createDirectory("execroot/root").getPath();
    String outputPath = execRoot + "/blaze-out";
    String blazeBin = String.format("%s/%s/bin", outputPath, DEFAULT_CONFIGURATION);
    String blazeGenfiles = String.format("%s/%s/genfiles", outputPath, DEFAULT_CONFIGURATION);
    String blazeTestlogs = String.format("%s/%s/testlogs", outputPath, DEFAULT_CONFIGURATION);

    blazeInfoData.setResults(
        ImmutableMap.<String, String>builder()
            .put(BlazeInfo.blazeBinKey(Blaze.getBuildSystemName(getProject())), blazeBin)
            .put(BlazeInfo.blazeGenfilesKey(Blaze.getBuildSystemName(getProject())), blazeGenfiles)
            .put(BlazeInfo.blazeTestlogsKey(Blaze.getBuildSystemName(getProject())), blazeTestlogs)
            .put(BlazeInfo.EXECUTION_ROOT_KEY, execRoot)
            .put(BlazeInfo.OUTPUT_BASE_KEY, outputBase)
            .put(BlazeInfo.OUTPUT_PATH_KEY, outputPath)
            .put(BlazeInfo.PACKAGE_PATH_KEY, workspaceRoot.toString())
            .build());

    // The tests run a full sync and hence also include the JDK setup part (if the workspace is
    // Java). To ensure that the plugin can find a suitable JDK, we need to set it up here and
    // ensure that the same Java language level is set in the project view file (see
    // setProjectViewSet() below).
    Sdk jdk = IdeaTestUtil.getMockJdk(JAVA_VERSION);
    EdtTestUtil.runInEdtAndWait(
        () ->
            WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(jdk, thisClassDisposable)));
  }

  @After
  public void doTearDown() {
    Disposer.dispose(thisClassDisposable);
  }

  /** The workspace content entries created during sync */
  protected ImmutableList<ContentEntry> getWorkspaceContentEntries() {
    if (moduleMocker != null) {
      return moduleMocker.getWorkspaceContentEntries();
    }

    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module workspaceModule = moduleManager.findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();

    ContentEntry[] entries = ModuleRootManager.getInstance(workspaceModule).getContentEntries();
    return ImmutableList.copyOf(entries);
  }

  /** Search the workspace module's {@link ContentEntry}s for one with the given file. */
  @Nullable
  protected ContentEntry findContentEntry(VirtualFile root) {
    for (ContentEntry entry : getWorkspaceContentEntries()) {
      if (root.equals(entry.getFile())) {
        return entry;
      }
    }
    return null;
  }

  /** The absolute file path for the execution root in the {@link TestFileSystem}. */
  protected String getExecRoot() {
    return execRoot;
  }

  protected static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  protected void setProjectView(String... contents) {
    BlazeContext context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    errorCollector.assertNoIssues();
    setProjectViewSet(result);
  }

  protected void setProjectViewSet(ProjectViewSet projectViewSet) {
    ProjectViewSet adjustedProjectViewSet = addJavaLanguageLevelIfNecessary(projectViewSet);
    projectViewManager.setProjectView(adjustedProjectViewSet);
  }

  private static ProjectViewSet addJavaLanguageLevelIfNecessary(ProjectViewSet projectViewSet) {
    if (isJavaWorkspace(projectViewSet)
        && projectViewSet.getScalarValue(JAVA_LANGUAGE_LEVEL_SECTION_KEY).isEmpty()) {
      ProjectView additionalProjectView =
          ProjectView.builder()
              .add(ScalarSection.builder(JAVA_LANGUAGE_LEVEL_SECTION_KEY).set(JAVA_VERSION.feature))
              .build();
      return ProjectViewSet.builder()
          .add(additionalProjectView)
          .addAll(projectViewSet.getProjectViewFiles())
          .build();
    }
    return projectViewSet;
  }

  private static boolean isJavaWorkspace(ProjectViewSet projectViewSet) {
    WorkspaceType workspaceType =
        projectViewSet
            .getScalarValue(WorkspaceTypeSection.KEY)
            .orElseGet(LanguageSupport::getDefaultWorkspaceType);
    return workspaceType == WorkspaceType.JAVA;
  }

  protected ProjectViewSet getProjectViewSet() {
    return projectViewManager.getProjectViewSet();
  }

  protected void setTargetMap(TargetMap targetMap) {
    blazeIdeInterface.targetMap = targetMap;
  }

  protected void runBlazeSync(BlazeSyncParams syncParams) {
    BlazeContext context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);

    // We need to run sync off EDT to keep IntelliJ's transaction system happy
    // Because the sync task itself wants to run occasional EDT tasks, we'll have
    // to keep flushing the event queue.
    Future<?> future =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  SyncPhaseCoordinator.getInstance(getProject()).runSync(syncParams, true, context);
                  context.close();
                });
    while (!future.isDone()) {
      IdeEventQueue.getInstance().flushQueue();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected List<SyncStats> getSyncStats() {
    return eventLogger.getSyncStats();
  }

  private static class MockBlazeInfoRunner extends BlazeInfoRunner {
    private final Map<String, String> results = Maps.newHashMap();

    @Override
    public ListenableFuture<String> runBlazeInfo(
        Project project,
        BuildInvoker invoker,
        BlazeContext context,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(results.get(key));
    }

    @Override
    public ListenableFuture<BlazeInfo> runBlazeInfo(
        Project project,
        BuildInvoker invoker,
        BlazeContext context,
        BuildSystemName buildSystemName,
        List<String> blazeFlags) {
      return Futures.immediateFuture(
          BlazeInfo.create(buildSystemName, ImmutableMap.copyOf(results)));
    }

    @Override
    public ListenableFuture<byte[]> runBlazeInfoGetBytes(
        Project project,
        BuildInvoker invoker,
        BlazeContext context,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(null);
    }

    public void setResults(Map<String, String> results) {
      this.results.clear();
      this.results.putAll(results);
    }
  }
  private static class MockBlazeIdeInterface implements BlazeIdeInterface {
    private TargetMap targetMap = new TargetMap(ImmutableMap.of());

    @Override
    public ProjectTargetData updateTargetData(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        SyncProjectState projectState,
        BlazeSyncBuildResult buildResult,
        boolean mergeWithOldState,
        @Nullable AspectSyncProjectData oldProjectData) {
      return new ProjectTargetData(targetMap, null, RemoteOutputArtifacts.fromProjectData(null));
    }

    @Override
    public BlazeBuildOutputs build(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        BlazeVersionData blazeVersion,
        BuildInvoker invoker,
        ProjectViewSet projectViewSet,
        ShardedTargetList shardedTargets,
        WorkspaceLanguageSettings workspaceLanguageSettings,
        ImmutableSet<OutputGroup> outputGroups,
        BlazeInvocationContext blazeInvocationContext,
        boolean invokeParallel) {
      return BlazeBuildOutputs.noOutputs(BuildResult.SUCCESS);
    }
  }
}
