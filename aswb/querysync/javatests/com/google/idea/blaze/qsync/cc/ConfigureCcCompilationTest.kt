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
package com.google.idea.blaze.qsync.cc

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.Label.Companion.fromWorkspacePackageAndName
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.JavaPackagePrefixReaderImpl
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.artifacts.AspectProtos
import com.google.idea.blaze.qsync.artifacts.DigestMap
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.CcCompilationInfo
import com.google.idea.blaze.qsync.deps.CcToolchain
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.PackageStatementParser
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.Companion.workspaceRelativeForTests
import com.google.idea.blaze.qsync.project.ProjectPath.ExternalRepositoryFinder.Companion.createEmptyForTests
import com.google.idea.blaze.qsync.project.ProjectPath.Resolver.Companion.create
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.CcWorkspace
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.google.idea.testing.IntellijRule
import java.nio.file.Path
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ConfigureCcCompilationTest {
  @get:Rule
  val intellij: IntellijRule = IntellijRule()

  @Before
  fun setUp() {
    intellij.registerApplicationService(ExperimentService::class.java, MockExperimentService())
  }

  private val context: Context<*> = NoopContext()
  private val externalRepositoryFinder = createEmptyForTests()
  private val syncRunner = TestDataSyncRunner(
    context,
    JavaPackagePrefixReaderImpl(
      workspaceRoot = Path.of("/"),
      packageReader = PackageStatementParser(),
      parallelPackageReader = QuerySyncTestUtils.SIMPLE_PARALLEL_PACKAGE_READER,
      fileExistenceCheck = { true }
    )
  )

  private fun toArtifactState(proto: CcCompilationInfoOuterClass.CcCompilationInfo): ArtifactTracker.State {
    val digestMap = DigestMap.ofFunction { Integer.toHexString(it.hashCode()) }
    return ArtifactTracker.State.create(
      proto.targetsList
        .map { CcCompilationInfo.create(it, digestMap, externalRepositoryFinder) }
        .associate { it.target() to TargetBuildInfo.forCcTarget(it, DependencyBuildContext.NONE) },
      proto.toolchainsList
        .map { CcToolchain.create(it, externalRepositoryFinder) }
        .associateBy { it.id() }
    )
  }

  @Test
  fun empty() {
    val original = syncRunner.sync(TestData.CC_LIBRARY_QUERY)
    val update = ProjectProtoUpdate(original.project)
    ConfigureCcSources().update(update, BuildGraphData.EMPTY, context)
    ConfigureCcCompilation().update(
      update,
      ArtifactTracker.State.EMPTY,
      context,
      ProjectPath.ExternalRepositoryFinder.createFailingForTests()
    )
    val project = update.build()
    assertThat(project.ccWorkspace).isEqualTo(CcWorkspace.getDefaultInstance())
  }

  @Test
  fun emptyArtifactTracker() {
    val original = syncRunner.sync(TestData.CC_LIBRARY_QUERY)
    val update = ProjectProtoUpdate(original.project)
    ConfigureCcSources().update(update, original.graph, context)
    ConfigureCcCompilation().update(
      update,
      ArtifactTracker.State.EMPTY,
      context,
      ProjectPath.ExternalRepositoryFinder.createFailingForTests()
    )
    val project = update.build()
    val ccTarget = Label.of("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc:cc")
    val testClassCcPath = ProjectPath.WorkspaceRelativeProjectPath(
      Path.of("tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc/TestClass.cc"), Path.of(""))
    assertThat(project.ccWorkspace).isEqualTo(
      CcWorkspace.getDefaultInstance()
        .copy(
          targets = mapOf(
            ccTarget to ProjectProto.CcTarget(
              target = ccTarget,
              sources = mapOf(testClassCcPath to ProjectProto.CcSourceFile(testClassCcPath, ProjectProto.CcLanguage.CPP)),
              contexts = emptyMap()
            )
          )
        )
    )
  }

  @Test
  fun basics() {
    val original = syncRunner.sync(TestData.CC_LIBRARY_QUERY)
    val update = ProjectProtoUpdate(original.project)

    val ccTargetLabel = TestData.CC_LIBRARY_QUERY.assumedLabels.single()
    val compilationInfo =
      CcCompilationInfoOuterClass.CcCompilationInfo.newBuilder()
        .addToolchains(
          CcCompilationInfoOuterClass.CcToolchainInfo.newBuilder()
            .setId("//my/cc_toolchain")
            .setCompiler("clang")
            .setCompilerExecutable("workspace/path/to/clang")
            .setCpu("k8")
            .setTargetName("k8-debug")
            .addBuiltInIncludeDirectories("bazel-out/builtin/include/directory")
            .addBuiltInIncludeDirectories("src/builtin/include/directory")
            .addAllCOptions(listOf("--sharedopt", "--conlyopt"))
            .addAllCppOptions(listOf("--sharedopt", "--cppopt"))
            .build()
        )
        .addTargets(
          CcCompilationInfoOuterClass.CcTargetInfo.newBuilder()
            .setLabel(ccTargetLabel.toString())
            .setToolchainId("//my/cc_toolchain")
            .addCopts("-w")
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
              AspectProtos.fileArtifacts(
                "bazel-out/include/directory/include_header.h",
                "bazel-out/quote/include/directory/quote_include_header.h",
                "bazel-out/system/include/directory/system_include_header.h",
                "bazel-out/framework/include/directory/framework_include_header",
                "bazel-out/builtin/include/directory/builtin_include.h"
              )
            )
        )
        .build()

    ConfigureCcSources().update(update, original.graph, context)
    ConfigureCcCompilation().update(
      update,
      toArtifactState(compilationInfo),
      context,
      ProjectPath.ExternalRepositoryFinder.createFailingForTests()
    )

    val project = update.build()

    assertThat(project.activeLanguages).contains(QuerySyncLanguage.CC)
    val workspace = project.ccWorkspace
    val ccTarget = workspace.targets.values.single()
    val context = ccTarget.contexts.values.single()
    assertThat(context.humanReadableName).isNotEmpty()
    val sourceFile = ccTarget.sources.values.single()
    assertThat<ProjectProto.CcLanguage>(sourceFile.language)
      .isEqualTo(ProjectProto.CcLanguage.CPP)
    assertThat(sourceFile.workspacePath)
      .isEqualTo(
        workspaceRelativeForTests(
          TestData.CC_LIBRARY_QUERY.onlySourcePath.resolve("TestClass.cc")
        )
      )
    val resolver =
      FlagResolver(
        create(Path.of("/workspace"), Path.of("/project"), Path.of("/project/external")), false
      )
    val cppCompilerSettings = context.languageToCompilerSettings[ProjectProto.CcLanguage.CPP]!!
    val cCompilerSettings = context.languageToCompilerSettings[ProjectProto.CcLanguage.C]!!
    assertThat(resolver.resolveAll(workspace.flagSets[cppCompilerSettings.flagSetId]))
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
        "-w",  // This is defined in `copts` of the test project build rule and extracted via the aspect.
        "--sharedopt",
        "--cppopt"
      )

    assertThat(resolver.resolveAll(workspace.flagSets[cCompilerSettings.flagSetId]))
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
        "-w",  // This is defined in `copts` of the test project build rule and extracted via the aspect.
        "--sharedopt",
        "--conlyopt"
      )

    assertThat(cppCompilerSettings.compilerExecutablePath)
      .isEqualTo(workspaceRelativeForTests(Path.of("workspace/path/to/clang")))

    assertThat(context.languageToCompilerSettings.keys)
      .containsExactly(ProjectProto.CcLanguage.CPP, ProjectProto.CcLanguage.C)

    assertThat(
      project
        .artifactDirectories
        .directoriesMap[com.google.idea.blaze.qsync.deps.ArtifactDirectories.DEFAULT]!!
        .contents
        .keys
    )
      .containsExactly(
        "bazel-out/include/directory/include_header.h",
        "bazel-out/quote/include/directory/quote_include_header.h",
        "bazel-out/system/include/directory/system_include_header.h",
        "bazel-out/framework/include/directory/framework_include_header",
        "bazel-out/builtin/include/directory/builtin_include.h"
      )
  }

  @Test
  fun repeatedApplication() {
    val original = syncRunner.sync(TestData.CC_LIBRARY_QUERY)

    val ccTargetLabel = TestData.CC_LIBRARY_QUERY.assumedLabels.single()
    val compilationInfo =
      CcCompilationInfoOuterClass.CcCompilationInfo.newBuilder()
        .addToolchains(
          CcCompilationInfoOuterClass.CcToolchainInfo.newBuilder()
            .setId("//my/cc_toolchain")
            .setCompiler("clang")
            .setCompilerExecutable("workspace/path/to/clang")
            .setCpu("k8")
            .setTargetName("k8-debug")
            .build()
        )
        .addTargets(
          CcCompilationInfoOuterClass.CcTargetInfo.newBuilder()
            .setLabel(ccTargetLabel.toString())
            .setToolchainId("//my/cc_toolchain")
        )
        .build()

    val configureCcCompilation = ConfigureCcCompilation()

    val project1 = let {
      val update = ProjectProtoUpdate(original.project)
      configureCcCompilation.update(
        update,
        toArtifactState(compilationInfo),
        context,
        ProjectPath.ExternalRepositoryFinder.createFailingForTests()
      )
      update.build()
    }

    ConfigureCcCompilation.resetFlagIdsForTestingOnly()
    val project2 = let {
      val update = ProjectProtoUpdate(original.project)
      configureCcCompilation.update(
        update,
        toArtifactState(compilationInfo),
        context,
        ProjectPath.ExternalRepositoryFinder.createFailingForTests()
      )
      update.build()
    }

    assertThat(project1).isEqualTo(project2)
  }

  @Test
  fun multi_srcs_share_flagset() {
    val original = syncRunner.sync(TestData.CC_MULTISRC_QUERY)
    val update = ProjectProtoUpdate(original.project)
    val pkgPath = TestData.CC_MULTISRC_QUERY.relativeSourcePaths.single()
    val labels =
      listOf(
        fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, pkgPath, "testclass"),
        fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, pkgPath, "testclass2")
      )

    val ccCi =
      CcCompilationInfoOuterClass.CcCompilationInfo.newBuilder()
        .addAllTargets(
          labels.map { label: Label? ->
            CcCompilationInfoOuterClass.CcTargetInfo.newBuilder()
              .setLabel(label.toString())
              .addDefines("DEBUG")
              .addIncludeDirectories("src/include/directory")
              .setToolchainId("//my/cc_toolchain")
              .build()
          })
        .addToolchains(
          CcCompilationInfoOuterClass.CcToolchainInfo.newBuilder()
            .setId("//my/cc_toolchain")
            .setCompiler("clang")
            .setCompilerExecutable("workspace/path/to/clang")
            .setCpu("k8")
            .setTargetName("k8-debug")
            .addBuiltInIncludeDirectories("src/builtin/include/directory")
            .build()
        )
        .build()

    ConfigureCcSources().update(update, original.graph, context)
    ConfigureCcCompilation().update(
      update,
      toArtifactState(ccCi),
      context,
      ProjectPath.ExternalRepositoryFinder.createFailingForTests()
    )

    val project = update.build()

    val workspace = project.ccWorkspace

    val context1 = workspace.targets[labels[0]]!!.contexts.values.single()
    val context2 = workspace.targets[labels[1]]!!.contexts.values.single()
    // Assert that both compilation contexts share a flagset ID (since the two targets share the
    // same flags):
    assertThat(
      listOf(context1, context2)
        .map { it.languageToCompilerSettings[ProjectProto.CcLanguage.CPP] }
        .map { it?.flagSetId }
        .distinct())
      .hasSize(1)
  }
}
