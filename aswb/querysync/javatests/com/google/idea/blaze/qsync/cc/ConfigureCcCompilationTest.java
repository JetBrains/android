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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.artifacts.AspectProtos.fileArtifacts;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth8;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.artifacts.DigestMap;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.ArtifactTracker.State;
import com.google.idea.blaze.qsync.deps.CcToolchain;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.PackageStatementParser;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcCompilationInfo;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcTargetInfo;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcToolchainInfo;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings;
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage;
import com.google.idea.blaze.qsync.project.ProjectProto.CcSourceFile;
import com.google.idea.blaze.qsync.project.ProjectProto.CcWorkspace;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigureCcCompilationTest {

  private final Context<?> context = new NoopContext();
  private final TestDataSyncRunner syncRunner =
      new TestDataSyncRunner(context, new PackageStatementParser());

  private static State toArtifactState(CcCompilationInfo proto) {
    DigestMap digestMap = DigestMap.ofFunction(p -> Integer.toHexString(p.hashCode()));
    return ArtifactTracker.State.create(
        proto.getTargetsList().stream()
            .map(t -> com.google.idea.blaze.qsync.deps.CcCompilationInfo.create(t, digestMap))
            .collect(
                toImmutableMap(
                    cc -> cc.target(),
                    cc -> TargetBuildInfo.forCcTarget(cc, DependencyBuildContext.NONE))),
        proto.getToolchainsList().stream()
            .map(CcToolchain::create)
            .collect(toImmutableMap(CcToolchain::id, Functions.identity())));
  }

  @Test
  public void empty() throws Exception {
    QuerySyncProjectSnapshot original = syncRunner.sync(TestData.CC_LIBRARY_QUERY);
    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), context);
    ConfigureCcCompilation ccConfig =
        new ConfigureCcCompilation(ArtifactTracker.State.EMPTY, update);
    ccConfig.update();
    ProjectProto.Project project = update.build();
    assertThat(project.getCcWorkspace()).isEqualTo(CcWorkspace.getDefaultInstance());
  }

  @Test
  public void basics() throws Exception {
    QuerySyncProjectSnapshot original = syncRunner.sync(TestData.CC_LIBRARY_QUERY);
    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), context);

    Label ccTargetLabel = getOnlyElement(TestData.CC_LIBRARY_QUERY.getAssumedLabels());
    CcCompilationInfo compilationInfo =
        CcCompilationInfo.newBuilder()
            .addToolchains(
                CcToolchainInfo.newBuilder()
                    .setId("//my/cc_toolchain")
                    .setCompiler("clang")
                    .setCompilerExecutable("workspace/path/to/clang")
                    .setCpu("k8")
                    .setTargetName("k8-debug")
                    .addBuiltInIncludeDirectories("bazel-out/builtin/include/directory")
                    .addBuiltInIncludeDirectories("src/builtin/include/directory")
                    .addAllCOptions(ImmutableList.of("--sharedopt", "--conlyopt"))
                    .addAllCppOptions(ImmutableList.of("--sharedopt", "--cppopt"))
                    .build())
            .addTargets(
                CcTargetInfo.newBuilder()
                    .setLabel(ccTargetLabel.toString())
                    .setToolchainId("//my/cc_toolchain")
                    .addDefines("DEBUG")
                    .addIncludeDirectories("bazel-out/include/directory")
                    .addIncludeDirectories("src/include/directory")
                    .addQuoteIncludeDirectories("bazel-out/quote/include/directory")
                    .addQuoteIncludeDirectories("src/quote/include/directory")
                    .addSystemIncludeDirectories("bazel-out/system/include/directory")
                    .addSystemIncludeDirectories("src/system/include/directory")
                    .addFrameworkIncludeDirectories("bazel-out/framework/include/directory")
                    .addFrameworkIncludeDirectories("src/framework/include/directory")
                    .addAllGenHdrs(
                        fileArtifacts(
                            "bazel-out/include/directory/include_header.h",
                            "bazel-out/quote/include/directory/quote_include_header.h",
                            "bazel-out/system/include/directory/system_include_header.h",
                            "bazel-out/framework/include/directory/framework_include_header",
                            "bazel-out/builtin/include/directory/builtin_include.h")))
            .build();

    ConfigureCcCompilation ccConfig =
        new ConfigureCcCompilation(toArtifactState(compilationInfo), update);
    ccConfig.update();

    ProjectProto.Project project = update.build();

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
    FlagResolver resolver =
        new FlagResolver(
            ProjectPath.Resolver.create(Path.of("/workspace"), Path.of("/project")), false);
    assertThat(resolver.resolveAll(workspace.getFlagSetsOrThrow(compilerSettings.getFlagSetId())))
        .containsExactly(
            "-DDEBUG",
            "-I/project/.bazel/buildout/bazel-out/builtin/include/directory",
            "-I/workspace/src/builtin/include/directory",
            "-I/project/.bazel/buildout/bazel-out/include/directory",
            "-I/workspace/src/include/directory",
            "-iquote/project/.bazel/buildout/bazel-out/quote/include/directory",
            "-iquote/workspace/src/quote/include/directory",
            "-isystem/project/.bazel/buildout/bazel-out/system/include/directory",
            "-isystem/workspace/src/system/include/directory",
            "-F/project/.bazel/buildout/bazel-out/framework/include/directory",
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
            "-I/project/.bazel/buildout/bazel-out/builtin/include/directory",
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
            "-I/project/.bazel/buildout/bazel-out/builtin/include/directory",
            "-I/workspace/src/builtin/include/directory",
            "--sharedopt",
            "--conlyopt");

    assertThat(
            project
                .getArtifactDirectories()
                .getDirectoriesMap()
                .get(".bazel/buildout")
                .getContentsMap()
                .keySet())
        .containsExactly(
            "bazel-out/include/directory/include_header.h",
            "bazel-out/quote/include/directory/quote_include_header.h",
            "bazel-out/system/include/directory/system_include_header.h",
            "bazel-out/framework/include/directory/framework_include_header",
            "bazel-out/builtin/include/directory/builtin_include.h");
  }

  @Test
  public void multi_srcs_share_flagset() throws Exception {
    QuerySyncProjectSnapshot original = syncRunner.sync(TestData.CC_MULTISRC_QUERY);
    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), context);
    Path pkgPath = getOnlyElement(TestData.CC_MULTISRC_QUERY.getRelativeSourcePaths());
    ImmutableList<Label> labels =
        ImmutableList.of(
            Label.fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, pkgPath, "testclass"),
            Label.fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, pkgPath, "testclass2"));

    CcCompilationInfo ccCi =
        CcCompilationInfo.newBuilder()
            .addAllTargets(
                labels.stream()
                    .map(
                        label ->
                            CcTargetInfo.newBuilder()
                                .setLabel(label.toString())
                                .addDefines("DEBUG")
                                .addIncludeDirectories("src/include/directory")
                                .setToolchainId("//my/cc_toolchain")
                                .build())
                    .collect(toImmutableList()))
            .addToolchains(
                CcToolchainInfo.newBuilder()
                    .setId("//my/cc_toolchain")
                    .setCompiler("clang")
                    .setCompilerExecutable("workspace/path/to/clang")
                    .setCpu("k8")
                    .setTargetName("k8-debug")
                    .addBuiltInIncludeDirectories("src/builtin/include/directory")
                    .build())
            .build();

    ConfigureCcCompilation ccConfig = new ConfigureCcCompilation(toArtifactState(ccCi), update);
    ccConfig.update();

    ProjectProto.Project project = update.build();

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
