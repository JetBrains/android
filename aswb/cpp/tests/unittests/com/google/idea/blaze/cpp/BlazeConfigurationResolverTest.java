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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
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
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
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
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeConfigurationResolver}. */
@RunWith(JUnit4.class)
public class BlazeConfigurationResolverTest extends BlazeTestCase {
  private final BlazeContext context = BlazeContext.create();
  private final ErrorCollector errorCollector = new ErrorCollector();
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));

  private BlazeConfigurationResolver resolver;
  private BlazeConfigurationResolverResult resolverResult;
  private MockCompilerVersionChecker compilerVersionChecker;
  private LocalFileSystem mockFileSystem;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(QuerySyncSettings.class, new QuerySyncSettings());
    compilerVersionChecker = new MockCompilerVersionChecker("1234");
    applicationServices.register(CompilerVersionChecker.class, compilerVersionChecker);
    applicationServices.register(ProgressManager.class, new ProgressManagerImpl());
    applicationServices.register(CompilerWrapperProvider.class, new CompilerWrapperProviderImpl());
    applicationServices.register(VirtualFileManager.class, mock(VirtualFileManager.class));
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    mockFileSystem = mock(LocalFileSystem.class);
    applicationServices.register(
        VirtualFileSystemProvider.class, mock(VirtualFileSystemProvider.class));
    when(VirtualFileSystemProvider.getInstance().getSystem()).thenReturn(mockFileSystem);

    ExtensionPointImpl<Kind.Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new CppBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());

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
  public void testEmptyProject() {
    ProjectView projectView = projectView(directories(), targets());
    TargetMap targetMap = TargetMapBuilder.builder().build();
    assertThatResolving(projectView, targetMap).producesNoConfigurations();
  }

  @Test
  public void testTargetWithoutSources() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:library"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap).producesNoConfigurations();
  }

  @Test
  public void testTargetWithGeneratedSources() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:library"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(gen("foo/bar/library.cc")),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap).producesNoConfigurations();
  }

  @Test
  public void testTargetWithMixedSources() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc"), gen("foo/bar/generated.cc")),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
  }

  @Test
  public void testSingleSourceTarget() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
  }

  @Test
  public void testSingleSourceTargetWithLibraryDependencies() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                        "//foo/bar:binary",
                        CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                        ImmutableList.of(src("foo/bar/binary.cc")),
                        "//:toolchain")
                    .addDependency("//bar/baz:library")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//bar/baz:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("bar/baz/library.cc")),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//third_party:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("third_party/library.cc")),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
  }

  @Test
  public void testSingleSourceTargetWithSourceDependencies() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                        "//foo/bar:binary",
                        CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                        ImmutableList.of(src("foo/bar/binary.cc")),
                        "//:toolchain")
                    .addDependency("//foo/bar:library")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("foo/bar/library.cc")),
                    ImmutableList.of("-DSOME_DEFINE=1"),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//third_party:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("third_party/library.cc")),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap)
        .producesConfigurationsFor("//foo/bar:binary", "//foo/bar:library");
  }

  @Test
  public void withCcToolchainSuite_testSingleSourceCcBinaryTarget() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    CToolchainIdeInfo.Builder cToolchainIdeInfoBuilder =
        CToolchainIdeInfo.builder().setCppExecutable(new ExecutionRootPath("cc"));
    TargetIdeInfo ccTarget =
        createCcTarget(
                "//foo/bar:binary",
                CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                ImmutableList.of(src("foo/bar/binary.cc")),
                "//:toolchain_suite")
            .build();
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchainSuite(cToolchainIdeInfoBuilder))
            .addTarget(ccTarget)
            .build();
    assertThatResolving(projectView, targetMap)
        .producesCToolchainIdeInfoForTarget("//foo/bar:binary", cToolchainIdeInfoBuilder.build());
  }

  // Java cc_library depends directly on a cc_toolchain_suite target. Should use the
  // CToolchainIdeInfo from the cc_toolchain_suite to build the toolchainLookupMap.
  @Test
  public void withCcToolchainSuite_testSingleSourceCcLibraryTarget() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:library"));
    CToolchainIdeInfo.Builder cToolchainIdeInfoBuilder =
        CToolchainIdeInfo.builder().setCppExecutable(new ExecutionRootPath("cc"));
    TargetIdeInfo ccTarget =
        createCcTarget(
                "//foo/bar:library",
                CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                ImmutableList.of(src("foo/bar/library.cc")),
                ImmutableList.of(),
                "//:toolchain_suite")
            .build();
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchainSuite(cToolchainIdeInfoBuilder))
            .addTarget(ccTarget)
            .build();
    assertThatResolving(projectView, targetMap)
        .producesCToolchainIdeInfoForTarget("//foo/bar:library", cToolchainIdeInfoBuilder.build());
  }

  // Starlark cc_library depends on an intermediate cc_toolchain_alias target which has a direct
  // dependency on a cc_toolchain_suite target. Should use the CToolchainIdeInfo from the
  // cc_toolchain_alias to build the toolchainLookupMap.
  @Test
  public void withCcToolchainAlias_testSingleSourceTarget() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:library"));
    // The CToolchainIdeInfo extracted from cc_toolchain_alias and cc_toolchain_suite should be
    // identical, but we differentiate them here to verify that the CToolchainIdeInfo from
    // cc_toolchain_alias target is used to build the toolchainLookupMap.
    CToolchainIdeInfo.Builder cToolchainIdeInfoBuilderSuite =
        CToolchainIdeInfo.builder().setCppExecutable(new ExecutionRootPath("cc"));
    CToolchainIdeInfo.Builder cToolchainIdeInfoBuilderAlias =
        CToolchainIdeInfo.builder()
            .setCppExecutable(new ExecutionRootPath("cc"))
            .setTargetName("toolchain_alias");
    TargetIdeInfo ccTarget =
        createCcTarget(
                "//foo/bar:library",
                CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                ImmutableList.of(src("foo/bar/library.cc")),
                ImmutableList.of(),
                "//:toolchain_alias")
            .build();
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchainSuite(cToolchainIdeInfoBuilderSuite))
            .addTarget(
                createCcToolchainAlias(cToolchainIdeInfoBuilderAlias)
                    .addDependency("//:toolchain_suite"))
            .addTarget(ccTarget)
            .build();
    assertThatResolving(projectView, targetMap)
        .producesCToolchainIdeInfoForTarget(
            "//foo/bar:library", cToolchainIdeInfoBuilderAlias.build());
  }

  // This is for an intermediate state where the cc_library starlarkification is rolled out, and
  // the cc_library target directly depends on cc_toolchain_alias instead of cc_toolchain_suite, but
  // users haven't updated their cache yet and the IDE does not add the cc_toolchain_alias to target
  // map. In this case, we expect there to be a toolchain warning, but it should still produce
  // configuration for the cc_library target and use the CToolchainIdeInfo from the
  // cc_toolchain_suite target.
  @Test
  public void withCcToolchainAlias_testSingleSourceTarget_missingCcToolChainAliasTarget() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:library"));
    CToolchainIdeInfo.Builder cToolchainIdeInfoBuilder =
        CToolchainIdeInfo.builder().setCppExecutable(new ExecutionRootPath("cc"));
    TargetIdeInfo ccTarget =
        createCcTarget(
                "//foo/bar:library",
                CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                ImmutableList.of(src("foo/bar/library.cc")),
                ImmutableList.of(),
                "//:toolchain_alias")
            .build();
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchainSuite(cToolchainIdeInfoBuilder))
            .addTarget(ccTarget)
            .build();
    String errMessage =
        String.format(
            "cc target is expected to depend on exactly 1 cc toolchain."
                + " Found 0 toolchains for these targets: %s",
            ccTarget.getKey());
    assertThatResolving(projectView, targetMap, errMessage)
        .producesCToolchainIdeInfoForTarget("//foo/bar:library", cToolchainIdeInfoBuilder.build());
  }

  @Test
  public void testComplexProject() {
    ProjectView projectView =
        projectView(
            directories("foo/bar", "foo/baz"),
            targets("//foo:test", "//foo/bar:binary", "//foo/baz:test"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                        "//foo:test",
                        CppBlazeRules.RuleTypes.CC_TEST.getKind(),
                        ImmutableList.of(src("foo/test.cc")),
                        "//:toolchain")
                    .addDependency("//foo:library")
                    .addDependency("//foo/bar:library")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//foo:library",
                    CppBlazeRules.RuleTypes.CC_TEST.getKind(),
                    ImmutableList.of(src("foo/library.cc")),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                        "//foo/bar:binary",
                        CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                        ImmutableList.of(src("foo/bar/binary.cc")),
                        ImmutableList.of("-DSOME_DEFINE=1"),
                        "//:toolchain")
                    .addDependency("//foo/bar:library")
                    .addDependency("//foo/bar:empty")
                    .addDependency("//foo/bar:generated")
                    .addDependency("//foo/bar:mixed")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("foo/bar/library.cc")),
                    ImmutableList.of("-DSOME_DEFINE=2"),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//foo/bar:empty",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//foo/bar:generated",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(gen("foo/bar/generated.cc")),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//foo/bar:mixed",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("foo/bar/mixed_src.cc"), gen("foo/bar/mixed_gen.cc")),
                    ImmutableList.of("-DSOME_DEFINE=3"),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                        "//foo/baz:test",
                        CppBlazeRules.RuleTypes.CC_TEST.getKind(),
                        ImmutableList.of(src("foo/baz/test.cc")),
                        ImmutableList.of("-DSOME_DEFINE=4"),
                        "//:toolchain")
                    .addDependency("//foo/baz:binary")
                    .addDependency("//foo/baz:library")
                    .addDependency("//foo/qux:library"))
            .addTarget(
                createCcTarget(
                    "//foo/baz:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/baz/binary.cc")),
                    ImmutableList.of("-DSOME_DEFINE=5"),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//foo/baz:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("foo/baz/library.cc")),
                    ImmutableList.of("-DSOME_DEFINE=6"),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//foo/qux:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("foo/qux/library.cc")),
                    "//:toolchain"))
            .addTarget(
                createCcTarget(
                    "//third_party:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("third_party/library.cc")),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap)
        .producesConfigurationsFor(
            "//foo/bar:binary",
            "//foo/bar:library",
            "//foo/bar:mixed",
            "//foo/baz:test",
            "//foo/baz:binary",
            "//foo/baz:library",
            "//foo:test");
  }

  @Test
  public void firstResolve_testNotIncremental() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"))
            .build();
    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");
  }

  @Test
  public void identicalTargets_testIncrementalUpdateFullReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"))
            .build();

    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
    ImmutableList<BlazeResolveConfiguration> initialConfigurations =
        resolverResult.getAllConfigurations();
    BlazeConfigurationResolverResult oldResult = resolverResult;

    assertThatResolving(projectView, targetMap).reusedConfigurations(initialConfigurations);
    assertThat(resolverResult.isEquivalentConfigurations(oldResult)).isTrue();
  }

  @Test
  public void identicalTargets_addedSources_testNotIncremental() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:*"));
    TargetMapBuilder targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"));
    createVirtualFile("/root/foo/bar/binary.cc");
    createVirtualFile("/root/foo/bar/binary_helper.cc");

    assertThatResolving(projectView, targetMap.build())
        .producesConfigurationsFor("//foo/bar:binary");
    BlazeConfigurationResolverResult oldResult = resolverResult;

    targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc"), src("foo/bar/binary_helper.cc")),
                    "//:toolchain"));

    assertThatResolving(projectView, targetMap.build())
        .reusedConfigurations(ImmutableList.of(), "//foo/bar:binary");
    assertThat(resolverResult.isEquivalentConfigurations(oldResult)).isFalse();
  }

  @Test
  public void newTarget_testIncrementalUpdatePartlyReused() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:*"));
    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"));
    assertThatResolving(projectView, targetMapBuilder.build())
        .producesConfigurationsFor("//foo/bar:binary");
    Collection<BlazeResolveConfiguration> initialConfigurations =
        resolverResult.getAllConfigurations();
    BlazeConfigurationResolverResult oldResult = resolverResult;

    targetMapBuilder.addTarget(
        createCcTarget(
            "//foo/bar:library",
            CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
            ImmutableList.of(src("foo/bar/library.cc")),
            ImmutableList.of("-DOTHER=1"),
            "//:toolchain"));

    assertThatResolving(projectView, targetMapBuilder.build())
        .reusedConfigurations(initialConfigurations, "//foo/bar:library");
    assertThat(resolverResult.isEquivalentConfigurations(oldResult)).isFalse();
  }

  @Test
  public void completelyDifferentTargetsSameProjectView_testIncrementalUpdateNoReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:*"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"))
            .build();
    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");
    BlazeConfigurationResolverResult oldResult = resolverResult;

    TargetMap targetMap2 =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("foo/bar/library.cc")),
                    ImmutableList.of("-DOTHER=1"),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView, targetMap2)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:library");
    assertThat(resolverResult.isEquivalentConfigurations(oldResult)).isFalse();
  }

  @Test
  public void completelyDifferentTargetsDifferentProjectView_testIncrementalUpdateNoReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"))
            .build();
    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");
    BlazeConfigurationResolverResult oldResult = resolverResult;

    ProjectView projectView2 = projectView(directories("foo/zoo"), targets("//foo/zoo:library"));
    TargetMap targetMap2 =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/zoo:library",
                    CppBlazeRules.RuleTypes.CC_LIBRARY.getKind(),
                    ImmutableList.of(src("foo/zoo/library.cc")),
                    ImmutableList.of("-DOTHER=1"),
                    "//:toolchain"))
            .build();
    assertThatResolving(projectView2, targetMap2)
        .reusedConfigurations(noReusedConfigurations, "//foo/zoo:library");
    assertThat(resolverResult.isEquivalentConfigurations(oldResult)).isFalse();
  }

  @Test
  public void changeCompilerVersion_testIncrementalUpdateNoReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"))
            .build();

    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");
    BlazeConfigurationResolverResult oldResult = resolverResult;

    compilerVersionChecker.setCompilerVersion("cc modified version");
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");
    assertThat(resolverResult.isEquivalentConfigurations(oldResult)).isFalse();
  }

  @Test
  public void brokenCompiler_collectsIssues() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:*"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    CppBlazeRules.RuleTypes.CC_BINARY.getKind(),
                    ImmutableList.of(src("foo/bar/binary.cc")),
                    "//:toolchain"))
            .build();
    createVirtualFile("/root/foo/bar/binary.cc");

    compilerVersionChecker.setInjectFault(true);

    computeResolverResult(projectView, targetMap);
    errorCollector.assertIssueContaining(
        "Unable to check compiler version for \"/root/cc\".\n"
            + "injected fault\n"
            + "Check if running the compiler with --version works on the cmdline.");
  }

  @Test
  public void multipleToolchainsNoIssue() {
    // Technically, blaze returns multiple instances of native libs (one for each CPU from
    // fat APK). However, we just pick the first instance we run into for the target map.
    // So it may be that we have:
    //   Main TC: only build target1
    //   Other TC2: build target1 + target2
    // After merging the target map it might look like target1 and target2 are built with
    // inconsistent TCs, even though it was originally consistent.
    ProjectView projectView = projectView(directories("foo"), targets("//foo:*"));

    CToolchainIdeInfo.Builder aarch32Toolchain =
        CToolchainIdeInfo.builder()
            .setTargetName("arm-linux-androideabi")
            .setCppExecutable(new ExecutionRootPath("bin/arm-linux-androideabi-gcc"));
    TargetIdeInfo.Builder aarch32ToolchainTarget =
        TargetIdeInfo.builder()
            .setLabel("//toolchains:armv7a")
            .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN.getKind())
            .setCToolchainInfo(aarch32Toolchain);
    CToolchainIdeInfo.Builder aarch64Toolchain =
        CToolchainIdeInfo.builder()
            .setTargetName("aarch64-linux-android")
            .setCppExecutable(new ExecutionRootPath("bin/aarch64-linux-android-gcc"));
    TargetIdeInfo.Builder aarch64ToolchainTarget =
        TargetIdeInfo.builder()
            .setLabel("//toolchains:aarch64")
            .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN.getKind())
            .setCToolchainInfo(aarch64Toolchain);
    TargetIdeInfo.Builder targetWith32Dep =
        TargetIdeInfo.builder()
            .setLabel("//foo:native_lib")
            .setKind(CppBlazeRules.RuleTypes.CC_LIBRARY.getKind())
            .setCInfo(CIdeInfo.builder().addSource(src("foo/native.cc")))
            .addSource(src("foo/native.cc"))
            .addDependency("//foo:native_lib2")
            .addDependency("//toolchains:armv7a");
    TargetIdeInfo.Builder targetWith64Dep =
        TargetIdeInfo.builder()
            .setLabel("//foo:native_lib2")
            .setKind(CppBlazeRules.RuleTypes.CC_LIBRARY.getKind())
            .setCInfo(CIdeInfo.builder().addSource(src("foo/native2.cc")))
            .addSource(src("foo/native2.cc"))
            .addDependency("//toolchains:aarch64");
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(aarch32ToolchainTarget)
            .addTarget(aarch64ToolchainTarget)
            .addTarget(targetWith64Dep)
            .addTarget(targetWith32Dep)
            .build();

    computeResolverResult(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertCToolchainIdeInfoForTarget(
        targetWith32Dep.build().getKey().toString(), aarch32Toolchain.build());
    assertCToolchainIdeInfoForTarget(
        targetWith64Dep.build().getKey().toString(), aarch64Toolchain.build());
  }

  private static ArtifactLocation src(String path) {
    return ArtifactLocation.builder().setRelativePath(path).setIsSource(true).build();
  }

  private static ArtifactLocation gen(String path) {
    return ArtifactLocation.builder().setRelativePath(path).setIsSource(false).build();
  }

  private static TargetIdeInfo.Builder createCcTarget(
      String label, Kind kind, ImmutableList<ArtifactLocation> sources, String toolchainDep) {
    return createCcTarget(label, kind, sources, ImmutableList.of(), toolchainDep);
  }

  private static TargetIdeInfo.Builder createCcTarget(
      String label,
      Kind kind,
      ImmutableList<ArtifactLocation> sources,
      ImmutableList<String> copts,
      String toolchainDep) {
    TargetIdeInfo.Builder targetInfo =
        TargetIdeInfo.builder().setLabel(label).setKind(kind).addDependency(toolchainDep);
    sources.forEach(targetInfo::addSource);
    return targetInfo.setCInfo(CIdeInfo.builder().addSources(sources).addLocalCopts(copts));
  }

  private static TargetIdeInfo.Builder createCcToolchain() {
    return TargetIdeInfo.builder()
        .setLabel("//:toolchain")
        .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN.getKind())
        .setCToolchainInfo(
            CToolchainIdeInfo.builder()
                .setCppExecutable(new ExecutionRootPath("cc"))
                .setTargetName("toolchain"));
  }

  private static TargetIdeInfo.Builder createCcToolchainSuite(
      CToolchainIdeInfo.Builder cToolchainIdeInfoBuilder) {
    return TargetIdeInfo.builder()
        .setLabel("//:toolchain_suite")
        .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN_SUITE.getKind())
        .setCToolchainInfo(cToolchainIdeInfoBuilder);
  }

  private static TargetIdeInfo.Builder createCcToolchainAlias(
      CToolchainIdeInfo.Builder cToolchainIdeInfoBuilder) {
    return TargetIdeInfo.builder()
        .setLabel("//:toolchain_alias")
        .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN_ALIAS.getKind())
        .setCToolchainInfo(cToolchainIdeInfoBuilder);
  }

  private static ListSection<DirectoryEntry> directories(String... directories) {
    return ListSection.builder(DirectorySection.KEY)
        .addAll(
            stream(directories)
                .map(directory -> DirectoryEntry.include(WorkspacePath.createIfValid(directory)))
                .collect(toImmutableList()))
        .build();
  }

  private static ListSection<TargetExpression> targets(String... targets) {
    return ListSection.builder(TargetSection.KEY)
        .addAll(stream(targets).map(TargetExpression::fromStringSafe).collect(toImmutableList()))
        .build();
  }

  private static ProjectView projectView(
      ListSection<DirectoryEntry> directories, ListSection<TargetExpression> targets) {
    return ProjectView.builder().add(directories).add(targets).build();
  }

  private VirtualFile createVirtualFile(String path) {
    VirtualFile mockFile = mock(VirtualFile.class);
    when(mockFile.getPath()).thenReturn(path);
    when(mockFile.isValid()).thenReturn(true);
    File f = new File(path);
    when(mockFileSystem.findFileByIoFile(f)).thenReturn(mockFile);
    when(mockFile.getName()).thenReturn(f.getName());
    return mockFile;
  }

  private void computeResolverResult(ProjectView projectView, TargetMap targetMap) {
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build();
    resolverResult =
        resolver.update(
            context,
            workspaceRoot,
            ProjectViewSet.builder().add(projectView).build(),
            blazeProjectData,
            resolverResult);
  }

  private Subject assertThatResolving(ProjectView projectView, TargetMap targetMap) {
    return assertThatResolving(projectView, targetMap, "");
  }

  private Subject assertThatResolving(
      ProjectView projectView, TargetMap targetMap, String issueMessage) {
    computeResolverResult(projectView, targetMap);
    if (issueMessage.isEmpty()) {
      errorCollector.assertNoIssues();
    } else {
      errorCollector.assertIssues(issueMessage);
    }
    return new Subject() {
      @Override
      public void producesConfigurationsFor(String... expected) {
        List<String> targets =
            resolverResult.getAllConfigurations().stream()
                .map(configuration -> configuration.getDisplayName())
                .collect(Collectors.toList());
        assertThat(targets).containsExactly((Object[]) expected);
      }

      @Override
      public void producesCToolchainIdeInfoForTarget(
          String target, CToolchainIdeInfo expectedCToolchainIdeInfo) {
        assertCToolchainIdeInfoForTarget(target, expectedCToolchainIdeInfo);
      }

      @Override
      public void producesNoConfigurations() {
        assertThat(resolverResult.getAllConfigurations()).isEmpty();
      }

      @Override
      public void reusedConfigurations(
          Collection<BlazeResolveConfiguration> expectedReused, String... expectedNotReused) {
        Collection<BlazeResolveConfiguration> currentConfigurations =
            resolverResult.getAllConfigurations();
        for (BlazeResolveConfiguration expectedItem : expectedReused) {
          assertThat(
                  currentConfigurations.stream()
                      .anyMatch(actualItem -> actualItem.isEquivalentConfigurations(expectedItem)))
              .isTrue();
        }
        List<String> notReusedTargets =
            currentConfigurations.stream()
                .filter(
                    configuration ->
                        expectedReused.stream()
                            .noneMatch(configuration::isEquivalentConfigurations))
                .map(configuration -> configuration.getDisplayName())
                .collect(Collectors.toList());
        assertThat(notReusedTargets).containsExactly((Object[]) expectedNotReused);
      }
    };
  }

  private void assertCToolchainIdeInfoForTarget(String target, CToolchainIdeInfo expected) {
    ImmutableList<Map.Entry<BlazeResolveConfigurationData, BlazeResolveConfiguration>> entry =
        resolverResult.getConfigurationMap().entrySet().stream()
            .filter(e -> Objects.equals(e.getValue().getDisplayName(), target))
            .collect(toImmutableList());
    assertThat(entry).hasSize(1);
    assertThat(entry.get(0).getKey().getCToolchainIdeInfo()).isEqualTo(expected);
  }

  private interface Subject {
    void producesConfigurationsFor(String... expected);

    void producesCToolchainIdeInfoForTarget(String target, CToolchainIdeInfo expected);

    void producesNoConfigurations();

    void reusedConfigurations(Collection<BlazeResolveConfiguration> reused, String... notReused);
  }
}
