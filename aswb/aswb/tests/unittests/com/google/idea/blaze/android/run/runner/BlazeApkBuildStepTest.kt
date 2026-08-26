/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner

import com.google.common.truth.Truth.assertThat
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos
import com.google.idea.blaze.android.run.BazelApkBuildStepProvider
import com.google.idea.blaze.android.run.NativeSymbolFinder
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.bazel.BuildSystemProvider
import com.google.idea.blaze.base.bazel.FakeBuildSystem
import com.google.idea.blaze.base.bazel.FakeBuildSystemProvider
import com.google.idea.blaze.base.bazel.TestBuildInvoker
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BazelImportSettingsManager
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.common.Label
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.intellij.mock.MockFileDocumentManagerImpl
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Unit tests for [BlazeApkBuildStep]. */
@RunWith(JUnit4::class)
class BlazeApkBuildStepTest : BlazeTestCase() {

  private val mockExperimentService = MockExperimentService()
  private var symbolFinderEp: ExtensionPointImpl<NativeSymbolFinder>? = null

  override fun createBuildSystemProvider(): BuildSystemProvider {
    return FakeBuildSystemProvider.builder()
      .setBuildSystem(FakeBuildSystem.builder(BuildSystemName.Bazel).setBuildInvoker(createMockInvoker()).build())
      .build()
  }

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)
    applicationServices.register(ExperimentService::class.java, mockExperimentService)
    applicationServices.register(FileDocumentManager::class.java, MockFileDocumentManagerImpl(null) { null })
    projectServices.register(BazelImportSettingsManager::class.java, BlazeImportSettingsManager(project))
    BlazeImportSettingsManager.getInstanceForTestingOnly(project)
      .setImportSettingsForTests(Path.of("/path/to/workspace"), BuildSystemName.Bazel)
    symbolFinderEp = registerExtensionPoint(NativeSymbolFinder.EP_NAME, NativeSymbolFinder::class.java)
  }

  private fun createMockInvoker(): TestBuildInvoker {
    return TestBuildInvoker(
      bepStreamProvider = { _, _ ->
        val started =
          BuildEventStreamProtos.BuildEvent.newBuilder()
            .setId(
              BuildEventStreamProtos.BuildEventId.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildEventId.BuildStartedId.getDefaultInstance())
            )
            .setStarted(BuildEventStreamProtos.BuildStarted.newBuilder().setUuid("buildId"))
            .build()
        val finished =
          BuildEventStreamProtos.BuildEvent.newBuilder()
            .setId(
              BuildEventStreamProtos.BuildEventId.newBuilder()
                .setBuildFinished(BuildEventStreamProtos.BuildEventId.BuildFinishedId.getDefaultInstance())
            )
            .setFinished(BuildEventStreamProtos.BuildFinished.newBuilder())
            .build()
        val bytesOut = ByteArrayOutputStream()
        started.writeDelimitedTo(bytesOut)
        finished.writeDelimitedTo(bytesOut)
        BuildEventStreamProvider.fromInputStream(ByteArrayInputStream(bytesOut.toByteArray()))
      }
    )
  }

  private fun registerMockSymbolFinder() {
    val mockSymbolFinder =
      object : NativeSymbolFinder {
        override val additionalBuildFlags: String = "--output_groups=+test_ndk_symbols"

        override fun getNativeSymbolsForBuild(
          project: Project,
          context: BlazeContext,
          target: Label,
          buildOutputs: BlazeBuildOutputs,
        ): List<File> = emptyList()
      }
    symbolFinderEp!!.registerExtension(mockSymbolFinder)
  }

  @Test
  fun testBuild_standardBuild_fetchNativeSymbolsTrue_includesNativeSymbolFinderFlags() {
    registerMockSymbolFinder()
    val mockInvoker = createMockInvoker()

    val buildStep =
      BlazeApkBuildStep(
        project = project,
        targets = listOf(Label.of("//java/com/example:app")),
        blazeFlags = emptyList(),
        exeFlags = emptyList(),
        useMobileInstall = false,
        fetchNativeSymbols = true,
        liveEditDataExtractor = null,
        launchId = "test-launch",
        buildInvoker = mockInvoker,
        deployInfoExtractor = DeployInfoExtractor { _, _, _ -> throw UnsupportedOperationException() },
      )

    buildStep.build(BlazeContext.create())

    assertThat(mockInvoker.invocations).hasSize(1)
    val commandArgs = mockInvoker.invocations.first().blazeCommand
    assertThat(commandArgs).contains("build")
    assertThat(commandArgs).contains("--output_groups=+android_deploy_info")
    assertThat(commandArgs).contains("--output_groups=+test_ndk_symbols")
  }

  @Test
  fun testBuild_standardBuild_fetchNativeSymbolsFalse_omitsNativeSymbolFinderFlags() {
    registerMockSymbolFinder()
    val mockInvoker = createMockInvoker()

    val buildStep =
      BlazeApkBuildStep(
        project = project,
        targets = listOf(Label.of("//java/com/example:app")),
        blazeFlags = emptyList(),
        exeFlags = emptyList(),
        useMobileInstall = false,
        fetchNativeSymbols = false,
        liveEditDataExtractor = null,
        launchId = "test-launch",
        buildInvoker = mockInvoker,
        deployInfoExtractor = DeployInfoExtractor { _, _, _ -> throw UnsupportedOperationException() },
      )

    buildStep.build(BlazeContext.create())

    assertThat(mockInvoker.invocations).hasSize(1)
    val commandArgs = mockInvoker.invocations.first().blazeCommand
    assertThat(commandArgs).contains("build")
    assertThat(commandArgs).contains("--output_groups=+android_deploy_info")
    assertThat(commandArgs).doesNotContain("--output_groups=+test_ndk_symbols")
  }

  @Test
  fun testProvider_getBinaryBuildStep_nativeDebugTrue_experimentEnabled_setsFetchNativeSymbolsTrue() {
    val buildStep =
      BazelApkBuildStepProvider.getBinaryBuildStep(
        project = project,
        useMobileInstall = false,
        isDebug = false,
        nativeDebuggingEnabled = true,
        liveEditDataExtractor = null,
        label = Label.of("//java/com/example:app"),
        blazeFlags = emptyList(),
        exeFlags = emptyList(),
        launchId = "test-launch",
      )

    assertThat(buildStep.fetchNativeSymbols).isTrue()
  }

  @Test
  fun testProvider_getBinaryBuildStep_nativeDebugTrue_isDebugTrue_experimentDisabled_setsFetchNativeSymbolsTrue() {
    mockExperimentService.setExperiment(BlazeApkBuildStep.FETCH_NATIVE_SYMBOLS_ON_DEPLOY, false)

    val buildStep =
      BazelApkBuildStepProvider.getBinaryBuildStep(
        project = project,
        useMobileInstall = false,
        isDebug = true,
        nativeDebuggingEnabled = true,
        liveEditDataExtractor = null,
        label = Label.of("//java/com/example:app"),
        blazeFlags = emptyList(),
        exeFlags = emptyList(),
        launchId = "test-launch",
      )

    assertThat(buildStep.fetchNativeSymbols).isTrue()
  }

  @Test
  fun testProvider_getBinaryBuildStep_nativeDebugTrue_isDebugFalse_experimentDisabled_setsFetchNativeSymbolsFalse() {
    mockExperimentService.setExperiment(BlazeApkBuildStep.FETCH_NATIVE_SYMBOLS_ON_DEPLOY, false)

    val buildStep =
      BazelApkBuildStepProvider.getBinaryBuildStep(
        project = project,
        useMobileInstall = false,
        isDebug = false,
        nativeDebuggingEnabled = true,
        liveEditDataExtractor = null,
        label = Label.of("//java/com/example:app"),
        blazeFlags = emptyList(),
        exeFlags = emptyList(),
        launchId = "test-launch",
      )

    assertThat(buildStep.fetchNativeSymbols).isFalse()
  }

  @Test
  fun testProvider_getBinaryBuildStep_nativeDebugFalse_setsFetchNativeSymbolsFalse() {
    val buildStep =
      BazelApkBuildStepProvider.getBinaryBuildStep(
        project = project,
        useMobileInstall = false,
        isDebug = true,
        nativeDebuggingEnabled = false,
        liveEditDataExtractor = null,
        label = Label.of("//java/com/example:app"),
        blazeFlags = emptyList(),
        exeFlags = emptyList(),
        launchId = "test-launch",
      )

    assertThat(buildStep.fetchNativeSymbols).isFalse()
  }

  @Test
  fun testBuild_mobileInstall_addsMobileInstallFlags() {
    val mockInvoker = createMockInvoker()

    val buildStep =
      BlazeApkBuildStep(
        project = project,
        targets = listOf(Label.of("//java/com/example:app")),
        blazeFlags = emptyList(),
        exeFlags = emptyList(),
        useMobileInstall = true,
        fetchNativeSymbols = false,
        liveEditDataExtractor = null,
        launchId = "test-launch",
        buildInvoker = mockInvoker,
        deployInfoExtractor = DeployInfoExtractor { _, _, _ -> throw UnsupportedOperationException() },
      )

    buildStep.build(BlazeContext.create())

    assertThat(mockInvoker.invocations).hasSize(1)
    val commandArgs = mockInvoker.invocations.first().blazeCommand
    assertThat(commandArgs).contains("mobile-install")
    assertThat(commandArgs).contains("--nolaunch_app")
    assertThat(commandArgs).contains("--nodeploy")
  }
}
