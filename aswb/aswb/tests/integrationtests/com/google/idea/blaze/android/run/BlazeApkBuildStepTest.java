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
package com.google.idea.blaze.android.run;

import static org.mockito.Mockito.mock;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Expect;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.android.run.runner.DeployInfoExtractor;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.bazel.BazelExitCode;
import com.google.idea.blaze.base.bazel.FakeBlazeCommandRunner;
import com.google.idea.blaze.base.bazel.FakeBuildInvoker;
import com.google.idea.blaze.base.bazel.FakeBuildResultHelperBep;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import java.io.IOException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.VerificationCollector;

/** Tests for {@link BlazeApkBuildStep}. */
@RunWith(JUnit4.class)
public final class BlazeApkBuildStepTest extends BlazeIntegrationTestCase {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  @Rule public final Expect expect = Expect.create();
  @Rule public VerificationCollector collector = MockitoJUnit.collector();

  private ErrorCollector errorCollector;
  private BlazeContext context;

  @Before
  public void setup() {
    context = BlazeContext.create();

    errorCollector = new ErrorCollector();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  @Test
  @Ignore("TODO: b/374906681 - broken by the build invokers cleanup.")
  public void command_usesMobileInstall() throws BuildEventStreamException, IOException {
    // Set up a build step with nominal output and mobile-install=true
    FakeBuildInvoker invoker = newFakeInvoker();
    BlazeApkBuildStep buildStep =
        defaultBuildStepBuilder().setUseMobileInstall(true).setBuildInvoker(invoker).build();

    // Issue the build
    buildStep.build(context, null);

    // Verify that the issued blaze command was mobile-install
    errorCollector.assertNoIssues();
    expect
        .that(invoker.getCommandRunner().getIssuedCommand().getName())
        .isEqualTo(BlazeCommandName.MOBILE_INSTALL);
  }

  @Test
  @Ignore("TODO: b/374906681 - broken by the build invokers cleanup.")
  public void command_withMultipleTargets() throws BuildEventStreamException, IOException {
    // Set up a build step set to build two targets
    FakeBuildInvoker invoker = newFakeInvoker();
    ImmutableList<Label> targets =
        ImmutableList.of(
            Label.create("//com/foo/test:target"), Label.create("//com/foo/app:target"));
    BlazeApkBuildStep buildStep =
        defaultBuildStepBuilder().setTargets(targets).setBuildInvoker(invoker).build();

    // Issue the build
    buildStep.build(context, null);

    // Verify that the issued blaze command was building both the targets
    errorCollector.assertNoIssues();
    expect
        .that(invoker.getCommandRunner().getIssuedCommand().toString())
        .contains("//com/foo/test:target //com/foo/app:target");
  }

  @Test
  public void bepParseError_terminatesLaunch() {
    // Set up a builder with the deploy info extractor throwing an error.
    DeployInfoExtractor mockDeployInfoExtractor =
        (buildOutputs, deployInfoOutputGroup, apksOutputGroup, context) -> {
          throw new IOException("some error");
        };
    BlazeApkBuildStep buildStep =
        defaultBuildStepBuilder().setDeployInfoExtractor(mockDeployInfoExtractor).build();

    // Issue the build
    buildStep.build(context, null);

    // Verify that the launch is terminated with the appropriate error.
    errorCollector.assertHasErrors();
    errorCollector.assertIssueContaining(
        "Error retrieving deployment info from build results: some error");
    expect.that(context.shouldContinue()).isFalse();
  }

  @Test
  public void build_savesDeployInfo() throws ApkProvisionException {
    // Set up a build step with a mock deploy info extractor
    BlazeAndroidDeployInfo deployInfo =
        new BlazeAndroidDeployInfo(
            new ParsedManifest("some.pkg.name", ImmutableList.of(), null),
            null,
            ImmutableList.of());
    DeployInfoExtractor mockDeployInfoExtractor =
        (buildOutputs, deployInfoOutputGroup, apksOutputGroup, context) -> deployInfo;
    BlazeApkBuildStep buildStep =
        defaultBuildStepBuilder().setDeployInfoExtractor(mockDeployInfoExtractor).build();

    // Issue the build
    buildStep.build(context, null);

    // Verify post-condition: android deploy info should've been extracted using deploy info parser
    expect.that(buildStep.getDeployInfo()).isEqualTo(deployInfo);
  }

  @Test
  @Ignore("TODO: b/374906681 - broken by the build invokers cleanup.")
  public void build_buildFailed() {
    FakeBuildInvoker invoker =
        FakeBuildInvoker.builder()
            .commandRunner(
                new FakeBlazeCommandRunner(
                    helper ->
                        BlazeBuildOutputs.noOutputs(
                            BuildResult.fromExitCode(BazelExitCode.BUILD_FAILED)),
                    helper ->
                        BlazeBuildOutputs.noOutputsForLegacy(
                            BuildResult.fromExitCode(BazelExitCode.BUILD_FAILED))))
            .build();
    DeployInfoExtractor deployInfoExtractor = mock(DeployInfoExtractor.class);
    BlazeApkBuildStep buildStep =
        defaultBuildStepBuilder()
            .setBuildInvoker(invoker)
            .setDeployInfoExtractor(deployInfoExtractor)
            .build();

    buildStep.build(context, null);

    expect.that(context.hasErrors()).isTrue();
    Mockito.verifyNoInteractions(deployInfoExtractor);
  }

  /** Returns a {@link BlazeApkBuildStep.Builder} with some default data set up. */
  private BlazeApkBuildStep.Builder defaultBuildStepBuilder() {
    return BlazeApkBuildStep.blazeApkBuildStepBuilder()
        .setProject(getProject())
        .setTargets(ImmutableList.of(Label.create("//default/test:target")))
        .setBlazeFlags(ImmutableList.of())
        .setExeFlags(ImmutableList.of())
        .setUseMobileInstall(true)
        .setLaunchId("some-random-id")
        .setBuildInvoker(newFakeInvoker())
        .setDeployInfoExtractor(
            (buildOutputs, deployInfoOutputGroup, apksOutputGroup, context) -> null);
  }

  private static FakeBuildInvoker newFakeInvoker() {
    FakeBuildResultHelperBep buildResultHelper = new FakeBuildResultHelperBep();
    return FakeBuildInvoker.builder().buildResultHelperSupplier(() -> buildResultHelper).build();
  }
}
