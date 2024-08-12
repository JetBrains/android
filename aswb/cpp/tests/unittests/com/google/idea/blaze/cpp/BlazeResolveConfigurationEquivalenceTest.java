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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Kind.Provider;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.mock.MockPsiManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that we group equivalent {@link BlazeResolveConfiguration}s. */
@RunWith(JUnit4.class)
public class BlazeResolveConfigurationEquivalenceTest extends BlazeTestCase {
  private final BlazeContext context = BlazeContext.create();
  private final ErrorCollector errorCollector = new ErrorCollector();
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));

  private BlazeConfigurationResolver resolver;
  private BlazeConfigurationResolverResult resolverResult;
  private LocalFileSystem mockFileSystem;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(QuerySyncSettings.class, new QuerySyncSettings());
    applicationServices.register(
        CompilerVersionChecker.class, new MockCompilerVersionChecker("1234"));

    applicationServices.register(ProgressManager.class, new ProgressManagerImpl());
    applicationServices.register(CompilerWrapperProvider.class, new CompilerWrapperProviderImpl());
    applicationServices.register(VirtualFileManager.class, mock(VirtualFileManager.class));
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    mockFileSystem = mock(LocalFileSystem.class);
    applicationServices.register(
        VirtualFileSystemProvider.class, mock(VirtualFileSystemProvider.class));
    when(VirtualFileSystemProvider.getInstance().getSystem()).thenReturn(mockFileSystem);

    ExtensionPointImpl<Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new CppBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());

    projectServices.register(PsiManager.class, new MockPsiManager(project));
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));

    BlazeImportSettingsManager.getInstance(getProject())
        .setImportSettings(
            new BlazeImportSettings(
                "",
                "",
                "",
                "",
                getBuildSystemProvider().getBuildSystem().getName(),
                ProjectType.ASPECT_SYNC));

    registerExtensionPoint(
        BlazeCompilerFlagsProcessor.EP_NAME, BlazeCompilerFlagsProcessor.Provider.class);

    context.addOutputSink(IssueOutput.class, errorCollector);

    resolver = new BlazeConfigurationResolver(project);
    resolverResult = BlazeConfigurationResolverResult.empty();
  }

  @Override
  protected BuildSystemProvider createBuildSystemProvider() {
    return new BazelBuildSystemProvider();
  }

  @Test
  public void testEmptyConfigurations() {
    ProjectView projectView =
        projectView(
            directories("foo/bar"), targets("//foo/bar:one", "//foo/bar:two", "//foo/bar:three"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:one",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/one.cc"),
                    copts(),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:two",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/two.cc"),
                    copts(),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:three",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/three.cc"),
                    copts(),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(1);
    assertThat(get(configurations, "//foo/bar:one and 2 other target(s)")).isNotNull();
    for (BlazeResolveConfiguration configuration : configurations) {
      assertThat(getHeaders(configuration)).isEmpty();
      assertThat(configuration.getTargetCopts()).isEmpty();
    }
  }

  @Test
  public void testDefines() {
    ProjectView projectView =
        projectView(
            directories("foo/bar"), targets("//foo/bar:one", "//foo/bar:two", "//foo/bar:three"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:one",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/one.cc"),
                    copts("-DSAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:two",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/two.cc"),
                    copts("-DSAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:three",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/three.cc"),
                    copts("-DDIFFERENT=1"),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:one and 1 other target(s)").getTargetCopts())
        .containsExactly("-DSAME=1");
    assertThat(get(configurations, "//foo/bar:three").getTargetCopts())
        .containsExactly("-DDIFFERENT=1");
    for (BlazeResolveConfiguration configuration : configurations) {
      assertThat(getHeaders(configuration)).isEmpty();
    }
  }

  @Test
  public void testIncludes() {
    ProjectView projectView =
        projectView(
            directories("foo/bar"), targets("//foo/bar:one", "//foo/bar:two", "//foo/bar:three"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:one",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/one.cc"),
                    copts(),
                    includes("foo/same")))
            .addTarget(
                createCcTarget(
                    "//foo/bar:two",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/two.cc"),
                    copts(),
                    includes("foo/same")))
            .addTarget(
                createCcTarget(
                    "//foo/bar:three",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/three.cc"),
                    copts(),
                    includes("foo/different")))
            .build();
    createVirtualFile("/root/foo/same");
    createVirtualFile("/root/foo/different");

    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(getHeaders(get(configurations, "//foo/bar:one and 1 other target(s)")))
        .containsExactly(header("foo/same"));
    assertThat(getHeaders(get(configurations, "//foo/bar:three")))
        .containsExactly(header("foo/different"));
    for (BlazeResolveConfiguration configuration : configurations) {
      assertThat(configuration.getTargetCopts()).isEmpty();
    }
  }

  // Test a series of permutations of labels a, b, c, d.
  // Initial state is {a=1, b=1, c=1, d=0}, and we flip some of the 1 to 0.
  private TargetMap incrementalUpdateTestCaseInitialTargetMap() {
    return TargetMapBuilder.builder()
        .addTarget(createCcToolchain())
        .addTarget(
            createCcTarget(
                "//foo/bar:a",
                CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                sources("foo/bar/a.cc"),
                copts("-DSAME=1"),
                includes()))
        .addTarget(
            createCcTarget(
                "//foo/bar:b",
                CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                sources("foo/bar/b.cc"),
                copts("-DSAME=1"),
                includes()))
        .addTarget(
            createCcTarget(
                "//foo/bar:c",
                CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                sources("foo/bar/c.cc"),
                copts("-DSAME=1"),
                includes()))
        .addTarget(
            createCcTarget(
                "//foo/bar:d",
                CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                sources("foo/bar/d.cc"),
                copts("-DDIFFERENT=1"),
                includes()))
        .build();
  }

  // TODO(jvoung): This could be a separate Parameterized test.
  private static final Map<List<String>, ImmutableList<String>>
      // Nothing can really be reused, since we're changing the sources in each
      // configuration, and configurations are tied to the sources now.
      permutationsAndExpectations =
      ImmutableMap.<List<String>, ImmutableList<String>>builder()
          .put(
              ImmutableList.of("a"),
              ImmutableList.of(
                  "//foo/bar:a and 1 other target(s)", "//foo/bar:b and 1 other target(s)"))
          .put(
              ImmutableList.of("b"),
              ImmutableList.of(
                  "//foo/bar:a and 1 other target(s)", "//foo/bar:b and 1 other target(s)"))
          .put(
              ImmutableList.of("c"),
              ImmutableList.of(
                  "//foo/bar:a and 1 other target(s)", "//foo/bar:c and 1 other target(s)"))
          .put(
              ImmutableList.of("a", "b"),
              ImmutableList.of("//foo/bar:a and 2 other target(s)", "//foo/bar:c"))
          .put(
              ImmutableList.of("b", "c"),
              ImmutableList.of("//foo/bar:a", "//foo/bar:b and 2 other target(s)"))
          .put(
              ImmutableList.of("a", "c"),
              ImmutableList.of("//foo/bar:a and 2 other target(s)", "//foo/bar:b"))
          .put(
              ImmutableList.of("a", "b", "c"),
              ImmutableList.of("//foo/bar:a and 3 other target(s)"))
          .build();

  @Test
  public void changeDefines_testIncrementalUpdate_0() {
    Map.Entry<List<String>, ImmutableList<String>> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 0);
    doChangeDefinesTestIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_1() {
    Map.Entry<List<String>, ImmutableList<String>> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 1);
    doChangeDefinesTestIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_2() {
    Map.Entry<List<String>, ImmutableList<String>> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 2);
    doChangeDefinesTestIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_3() {
    Map.Entry<List<String>, ImmutableList<String>> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 3);
    doChangeDefinesTestIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_4() {
    Map.Entry<List<String>, ImmutableList<String>> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 4);
    doChangeDefinesTestIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_5() {
    Map.Entry<List<String>, ImmutableList<String>> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 5);
    doChangeDefinesTestIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_6() {
    Map.Entry<List<String>, ImmutableList<String>> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 6);
    doChangeDefinesTestIncrementalUpdate(testCase.getKey(), testCase.getValue());
    assertThat(permutationsAndExpectations.size()).isEqualTo(7);
  }

  private void doChangeDefinesTestIncrementalUpdate(
      List<String> labelsToFlip, ImmutableList<String> newConfigurationLabels) {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:...:all"));
    List<BlazeResolveConfiguration> configurations =
        resolve(projectView, incrementalUpdateTestCaseInitialTargetMap());
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:a and 2 other target(s)")).isNotNull();
    assertThat(get(configurations, "//foo/bar:d")).isNotNull();

    TargetMapBuilder targetMapBuilder = TargetMapBuilder.builder().addTarget(createCcToolchain());
    for (String target : ImmutableList.of("a", "b", "c")) {
      if (labelsToFlip.contains(target)) {
        targetMapBuilder.addTarget(
            createCcTarget(
                String.format("//foo/bar:%s", target),
                CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                sources(String.format("foo/bar/%s.cc", target)),
                copts("-DDIFFERENT=1"),
                includes()));
      } else {
        targetMapBuilder.addTarget(
            createCcTarget(
                String.format("//foo/bar:%s", target),
                CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                sources(String.format("foo/bar/%s.cc", target)),
                copts("-DSAME=1"),
                includes()));
      }
    }
    targetMapBuilder.addTarget(
        createCcTarget(
            "//foo/bar:d",
            CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
            sources("foo/bar/d.cc"),
            copts("-DDIFFERENT=1"),
            includes()));
    List<BlazeResolveConfiguration> newConfigurations =
        resolve(projectView, targetMapBuilder.build());
    assertThat(newConfigurations.size()).isEqualTo(newConfigurationLabels.size());
    for (String label : newConfigurationLabels) {
      assertThat(get(newConfigurations, label)).isNotNull();
    }
  }

  @Test
  public void changeDefinesWithSameStructure_testIncrementalUpdate() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:...:all"));
    TargetMap targetMap = incrementalUpdateTestCaseInitialTargetMap();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:a and 2 other target(s)")).isNotNull();
    assertThat(get(configurations, "//foo/bar:d")).isNotNull();

    targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:a",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/a.cc"),
                    copts("-DCHANGED=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:b",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/b.cc"),
                    copts("-DCHANGED=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:c",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/c.cc"),
                    copts("-DCHANGED=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:d",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/d.cc"),
                    copts("-DDIFFERENT=1"),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> newConfigurations = resolve(projectView, targetMap);
    assertThat(newConfigurations).hasSize(2);
    assertReusedConfigs(
        configurations,
        newConfigurations,
        new ReusedConfigurationExpectations(
            ImmutableList.of("//foo/bar:d"),
            ImmutableList.of("//foo/bar:a and 2 other target(s)")));
  }

  @Test
  public void changeDefinesMakeAllSame_notReused() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:...:all"));
    TargetMap targetMap = incrementalUpdateTestCaseInitialTargetMap();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:a and 2 other target(s)")).isNotNull();
    assertThat(get(configurations, "//foo/bar:d")).isNotNull();

    targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:a",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/a.cc"),
                    copts("-DSAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:b",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/b.cc"),
                    copts("-DSAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:c",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/c.cc"),
                    copts("-DSAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:d",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    sources("foo/bar/d.cc"),
                    copts("-DSAME=1"),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> newConfigurations = resolve(projectView, targetMap);
    assertThat(newConfigurations).hasSize(1);
    // We can't actually reuse the configurations, since they they have a different
    // set of sources covered, and CLion ties the sources to the configuration.
    assertThat(get(newConfigurations, "//foo/bar:a and 3 other target(s)")).isNotNull();
  }

  private static List<ArtifactLocation> sources(String... paths) {
    return Arrays.stream(paths)
        .map(path -> ArtifactLocation.builder().setRelativePath(path).setIsSource(true).build())
        .collect(Collectors.toList());
  }

  private static List<String> copts(String... copts) {
    return Arrays.asList(copts);
  }

  private static List<ExecutionRootPath> includes(String... paths) {
    return Arrays.stream(paths).map(ExecutionRootPath::new).collect(Collectors.toList());
  }

  private static TargetIdeInfo.Builder createCcTarget(
      String label,
      Kind kind,
      List<ArtifactLocation> sources,
      List<String> copts,
      List<ExecutionRootPath> includes) {
    TargetIdeInfo.Builder targetInfo =
        TargetIdeInfo.builder().setLabel(label).setKind(kind).addDependency("//:toolchain");
    sources.forEach(targetInfo::addSource);
    return targetInfo.setCInfo(
        CIdeInfo.builder()
            .addSources(sources)
            .addLocalCopts(copts)
            .addTransitiveIncludeDirectories(includes));
  }

  private static TargetIdeInfo.Builder createCcToolchain() {
    return TargetIdeInfo.builder()
        .setLabel("//:toolchain")
        .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN.getKind())
        .setCToolchainInfo(
            CToolchainIdeInfo.builder().setCppExecutable(new ExecutionRootPath("cc")));
  }

  private static ListSection<DirectoryEntry> directories(String... directories) {
    return ListSection.builder(DirectorySection.KEY)
        .addAll(
            Arrays.stream(directories)
                .map(directory -> DirectoryEntry.include(WorkspacePath.createIfValid(directory)))
                .collect(Collectors.toList()))
        .build();
  }

  private static ListSection<TargetExpression> targets(String... targets) {
    return ListSection.builder(TargetSection.KEY)
        .addAll(
            Arrays.stream(targets)
                .map(TargetExpression::fromStringSafe)
                .collect(Collectors.toList()))
        .build();
  }

  private static ProjectView projectView(
      ListSection<DirectoryEntry> directories, ListSection<TargetExpression> targets) {
    return ProjectView.builder().add(directories).add(targets).build();
  }

  private List<BlazeResolveConfiguration> resolve(ProjectView projectView, TargetMap targetMap) {
    resolverResult =
        resolver.update(
            context,
            workspaceRoot,
            ProjectViewSet.builder().add(projectView).build(),
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build(),
            resolverResult);
    errorCollector.assertNoIssues();
    return resolverResult.getAllConfigurations();
  }

  private static BlazeResolveConfiguration get(
      List<BlazeResolveConfiguration> configurations, String name) {
    List<BlazeResolveConfiguration> filteredConfigurations =
        configurations.stream()
            .filter(c -> c.getDisplayName().equals(name))
            .collect(Collectors.toList());
    assertWithMessage(
            String.format(
                "%s contains %s",
                configurations.stream()
                    .map(BlazeResolveConfiguration::getDisplayName)
                    .collect(Collectors.toList()),
                name))
        .that(filteredConfigurations)
        .hasSize(1);
    return filteredConfigurations.get(0);
  }

  private ExecutionRootPath header(String path) {
    return new ExecutionRootPath(path);
  }

  private static List<ExecutionRootPath> getHeaders(BlazeResolveConfiguration configuration) {
    return configuration.getLibraryHeadersRootsInternal();
  }

  private void createVirtualFile(String path) {
    VirtualFile stub =
        new StubVirtualFile(mockFileSystem) {
          @Override
          public boolean isValid() {
            return true;
          }
        };
    when(mockFileSystem.findFileByIoFile(new File(path))).thenReturn(stub);
  }

  private static void assertReusedConfigs(
      List<BlazeResolveConfiguration> oldConfigurations,
      List<BlazeResolveConfiguration> newConfigurations,
      ReusedConfigurationExpectations expected) {
    for (String label : expected.reusedLabels) {
      assertWithMessage(String.format("Checking that %s is reused", label))
          .that(
              get(newConfigurations, label)
                  .isEquivalentConfigurations(get(oldConfigurations, label)))
          .isTrue();
    }
    for (String label : expected.notReusedLabels) {
      assertWithMessage(String.format("Checking that %s is NOT reused", label))
          .that(
              get(newConfigurations, label)
                  .isEquivalentConfigurations(get(oldConfigurations, label)))
          .isFalse();
    }
  }

  private static class ReusedConfigurationExpectations {
    final ImmutableCollection<String> reusedLabels;
    final ImmutableCollection<String> notReusedLabels;

    ReusedConfigurationExpectations(
        ImmutableCollection<String> reusedLabels, ImmutableCollection<String> notReusedLabels) {
      this.reusedLabels = reusedLabels;
      this.notReusedLabels = notReusedLabels;
    }
  }
}
