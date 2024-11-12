/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.cc;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.project.ProjectPath.projectRelative;
import static com.google.idea.blaze.qsync.project.ProjectPath.workspaceRelative;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth8;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.CcCompilationInfo;
import com.google.idea.blaze.qsync.deps.CcToolchain;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings;
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage;
import com.google.idea.blaze.qsync.project.ProjectProto.CcSourceFile;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.testdata.BuildGraphs;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CcWorkspaceBuilderTest {

  @Test
  public void empty() throws Exception {
    CcWorkspaceBuilder builder =
        new CcWorkspaceBuilder(
            CcDependenciesInfo.EMPTY,
            BuildGraphs.forTestProject(TestData.CC_LIBRARY_QUERY),
            QuerySyncTestUtils.NOOP_CONTEXT);
    ProjectProto.Project project =
        builder.updateProjectProtoForCcDeps(ProjectProto.Project.getDefaultInstance());
    assertThat(project.hasCcWorkspace()).isFalse();
  }

  @Test
  public void basics() throws Exception {
    Label ccTargetLabel = getOnlyElement(TestData.CC_LIBRARY_QUERY.getAssumedLabels());
    CcDependenciesInfo ccDepsInfo =
        CcDependenciesInfo.create(
            ImmutableMap.of(
                ccTargetLabel,
                CcCompilationInfo.builder()
                    .target(ccTargetLabel)
                    .toolchainId("//my/cc_toolchain")
                    .defines("DEBUG")
                    .includeDirectories(
                        projectRelative("build-out/include/directory"),
                        workspaceRelative("src/include/directory"))
                    .quoteIncludeDirectories(
                        projectRelative("build-out/quote/include/directory"),
                        workspaceRelative("src/quote/include/directory"))
                    .systemIncludeDirectories(
                        projectRelative("build-out/system/include/directory"),
                        workspaceRelative("src/system/include/directory"))
                    .frameworkIncludeDirectories(
                        projectRelative("build-out/framework/include/directory"),
                        workspaceRelative("src/framework/include/directory"))
                    .genHeaders(
                        BuildArtifact.create(
                            "includedigest",
                            Path.of("build-out/include/directory/include_header.h"),
                            ccTargetLabel),
                        BuildArtifact.create(
                            "quotedigest",
                            Path.of("build-out/quote/include/directory/quote_include_header.h"),
                            ccTargetLabel),
                        BuildArtifact.create(
                            "systemdigest",
                            Path.of("build-out/system/include/directory/system_include_header.h"),
                            ccTargetLabel),
                        BuildArtifact.create(
                            "frameworkdigest",
                            Path.of(
                                "build-out/framework/include/directory/framework_include_header"),
                            ccTargetLabel),
                        BuildArtifact.create(
                            "bultindigest",
                            Path.of("build-out/builtin/include/directory/builtin_include.h"),
                            ccTargetLabel))
                    .build()),
            ImmutableMap.of(
                "//my/cc_toolchain",
                CcToolchain.builder()
                    .id("//my/cc_toolchain")
                    .compiler("clang")
                    .compilerExecutable(workspaceRelative("workspace/path/to/clang"))
                    .cpu("k8")
                    .targetGnuSystemName("k8-debug")
                    .builtInIncludeDirectories(
                        projectRelative("build-out/builtin/include/directory"),
                        workspaceRelative("src/builtin/include/directory"))
                    .cOptions("--sharedopt", "--conlyopt")
                    .cppOptions("--sharedopt", "--cppopt")
                    .build()));

    FlagResolver resolver =
        new FlagResolver(
            ProjectPath.Resolver.create(Path.of("/workspace"), Path.of("/project")), false);

    CcWorkspaceBuilder builder =
        new CcWorkspaceBuilder(
            ccDepsInfo,
            BuildGraphs.forTestProject(TestData.CC_LIBRARY_QUERY),
            QuerySyncTestUtils.NOOP_CONTEXT);

    ProjectProto.Project project =
        builder.updateProjectProtoForCcDeps(ProjectProto.Project.getDefaultInstance());
    assertThat(project.getActiveLanguagesList()).contains(LanguageClass.LANGUAGE_CLASS_CC);
    ProjectProto.CcWorkspace workspace = project.getCcWorkspace();
    CcCompilationContext context = getOnlyElement(workspace.getContextsList());
    assertThat(context.getHumanReadableName()).isNotEmpty();
    CcSourceFile sourceFile = getOnlyElement(context.getSourcesList());
    assertThat(sourceFile.getLanguage()).isEqualTo(CcLanguage.CPP);
    assertThat(sourceFile.getWorkspacePath())
        .isEqualTo(
            TestData.CC_LIBRARY_QUERY.getOnlySourcePath().resolve("TestClass.cc").toString());
    CcCompilerSettings compilerSettings = sourceFile.getCompilerSettings();
    assertThat(resolver.resolveAll(workspace.getFlagSetsOrThrow(compilerSettings.getFlagSetId())))
        .containsExactly(
            "-DDEBUG",
            "-I/project/build-out/builtin/include/directory",
            "-I/workspace/src/builtin/include/directory",
            "-I/project/build-out/include/directory",
            "-I/workspace/src/include/directory",
            "-iquote/project/build-out/quote/include/directory",
            "-iquote/workspace/src/quote/include/directory",
            "-isystem/project/build-out/system/include/directory",
            "-isystem/workspace/src/system/include/directory",
            "-F/project/build-out/framework/include/directory",
            "-F/workspace/src/framework/include/directory",
            "-w", // This is defined in `copts` of the test project build rule.
            "--sharedopt",
            "--cppopt");

    assertThat(compilerSettings.getCompilerExecutablePath().getBase()).isEqualTo(Base.WORKSPACE);
    assertThat(compilerSettings.getCompilerExecutablePath().getPath())
        .isEqualTo("workspace/path/to/clang");

    Truth8.assertThat(
            context.getLanguageToCompilerSettingsMap().keySet().stream()
                .map(l -> CcLanguage.valueOf(CcLanguage.getDescriptor().findValueByName(l))))
        .containsExactly(CcLanguage.CPP, CcLanguage.C);

    assertThat(
            resolver.resolveAll(
                workspace.getFlagSetsOrThrow(
                    context
                        .getLanguageToCompilerSettingsMap()
                        .get(CcLanguage.CPP.name())
                        .getFlagSetId())))
        .containsExactly(
            "-I/project/build-out/builtin/include/directory",
            "-I/workspace/src/builtin/include/directory",
            "--sharedopt",
            "--cppopt");

    assertThat(
            resolver.resolveAll(
                workspace.getFlagSetsOrThrow(
                    context
                        .getLanguageToCompilerSettingsMap()
                        .get(CcLanguage.C.name())
                        .getFlagSetId())))
        .containsExactly(
            "-I/project/build-out/builtin/include/directory",
            "-I/workspace/src/builtin/include/directory",
            "--sharedopt",
            "--conlyopt");
  }

  @Test
  public void multi_srcs_share_flagset() throws Exception {
    Path pkgPath = getOnlyElement(TestData.CC_MULTISRC_QUERY.getRelativeSourcePaths());
    ImmutableList<Label> labels =
        ImmutableList.of(
            Label.fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, pkgPath, "testclass"),
            Label.fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, pkgPath, "testclass2"));

    CcDependenciesInfo ccDepsInfo =
        CcDependenciesInfo.create(
            labels.stream()
                .map(
                    label ->
                        CcCompilationInfo.builder()
                            .target(label)
                            .defines("DEBUG")
                            .includeDirectories(workspaceRelative("src/include/directory"))
                            .quoteIncludeDirectories()
                            .systemIncludeDirectories()
                            .frameworkIncludeDirectories()
                            .genHeaders()
                            .toolchainId("//my/cc_toolchain")
                            .build())
                .collect(
                    ImmutableMap.toImmutableMap(CcCompilationInfo::target, Function.identity())),
            ImmutableMap.of(
                "//my/cc_toolchain",
                CcToolchain.builder()
                    .id("//my/cc_toolchain")
                    .compiler("clang")
                    .compilerExecutable(workspaceRelative("workspace/path/to/clang"))
                    .cpu("k8")
                    .targetGnuSystemName("k8-debug")
                    .builtInIncludeDirectories(workspaceRelative("src/builtin/include/directory"))
                    .cOptions()
                    .cppOptions()
                    .build()));

    CcWorkspaceBuilder builder =
        new CcWorkspaceBuilder(
            ccDepsInfo,
            BuildGraphs.forTestProject(TestData.CC_MULTISRC_QUERY),
            QuerySyncTestUtils.NOOP_CONTEXT);
    ProjectProto.Project project =
        builder.updateProjectProtoForCcDeps(ProjectProto.Project.getDefaultInstance());
    ProjectProto.CcWorkspace workspace = project.getCcWorkspace();

    assertThat(workspace.getContextsList()).hasSize(2);
    // Assert that both compilation contexts share a flagset ID (since the two targets share the
    // same flags):
    Truth8.assertThat(
            workspace.getContextsList().stream()
                .map(CcCompilationContext::getSourcesList)
                .flatMap(List::stream)
                .map(CcSourceFile::getCompilerSettings)
                .map(CcCompilerSettings::getFlagSetId)
                .distinct())
        .hasSize(1);
  }
}
