/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run

import com.android.tools.idea.run.ApkProvisionException
import com.google.common.truth.Expect
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep
import com.google.idea.blaze.android.run.runner.DeployInfoExtractor
import com.google.idea.blaze.base.BlazeIntegrationTestCase
import com.google.idea.blaze.base.bazel.FakeBuildInvoker
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.ErrorCollector
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.common.Label
import java.io.File
import java.io.IOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.VerificationCollector

/** Tests for [BlazeApkBuildStep].  */
@RunWith(JUnit4::class)
class BlazeApkBuildStepTest : BlazeIntegrationTestCase() {
  @get:Rule
  var folder: TemporaryFolder = TemporaryFolder()

  @get:Rule
  val expect: Expect = Expect.create()

  @get:Rule
  var collector: VerificationCollector = MockitoJUnit.collector()
  private var errorCollector: ErrorCollector? = null
  private var context: BlazeContext? = null

  @Before
  fun setup() {
    context = BlazeContext.create()

    errorCollector = ErrorCollector()
    context!!.addOutputSink<IssueOutput?>(IssueOutput::class.java, errorCollector)
  }

  @Test
  fun bepParseError_terminatesLaunch() {
    // Set up a build step with the deploy info extractor throwing an error.
    val mockDeployInfoExtractor = DeployInfoExtractor { _, _, _, _, _, _ -> throw IOException("some error") }
    val buildStep = createBlazeApkBuildStep(mockDeployInfoExtractor)

    // Issue the build
    buildStep.build(context!!, null)

    // Verify that the launch is terminated with the appropriate error.
    errorCollector!!.assertHasErrors()
    errorCollector!!.assertIssueContaining(
      "Error retrieving deployment info from build results: some error"
    )
    expect.that(context!!.shouldContinue()).isFalse()
  }

  /** Returns a [BlazeApkBuildStep] with some default data set up.  */
  private fun createBlazeApkBuildStep(deployInfoExtractor: DeployInfoExtractor): BlazeApkBuildStep {
    return BlazeApkBuildStep(
      project = getProject(),
      targets = listOf(Label.of("//default/test:target")),
      blazeFlags = emptyList<String>(),
      exeFlags = emptyList<String>(),
      useMobileInstall = true,
      nativeDebuggingEnabled = false,
      launchId = "some-random-id",
      buildInvoker = newFakeInvoker()!!,
      deployInfoExtractor = deployInfoExtractor
    )
  }

  companion object {
    var nativeSymbols: List<File> = listOf(File("symbols.so"))

    private fun newFakeInvoker(): FakeBuildInvoker? {
      return FakeBuildInvoker.builder().build()
    }
  }
}
