/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.functional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidInstrumentationTestTarget.android_instrumentation_test;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MessageCollector;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.android.run.runner.BlazeInstrumentationTestApkBuildStep;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.ExternalTaskProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProviderWrapper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeInstrumentationTestApkBuildStep} */
@RunWith(JUnit4.class)
public class BlazeInstrumentationTestApkBuildStepIntegrationTest
    extends BlazeAndroidIntegrationTestCase {
  private void setupProject() {
    setProjectView(
        "directories:",
        "  java/com/foo/app",
        "targets:",
        "  //java/com/foo/app:instrumentation_test",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");

    workspace.createFile(
        new WorkspacePath("java/com/foo/app/MainActivity.java"),
        "package com.foo.app",
        "import android.app.Activity;",
        "public class MainActivity extends Activity {}");

    workspace.createFile(
        new WorkspacePath("java/com/foo/app/Test.java"),
        "package com.foo.app",
        "public class Test {}");

    setTargetMap(
        android_binary("//java/com/foo/app:app").src("MainActivity.java"),
        android_binary("//java/com/foo/app:test_app")
            .setResourceJavaPackage("com.foo.app.androidtest")
            .src("Test.java")
            .instruments("//java/com/foo/app:app"),
        android_binary("//java/com/foo/app:test_app_self_instrumenting")
            .setResourceJavaPackage("com.foo.app.androidtest.selfinstrumenting")
            .src("Test.java"),
        android_instrumentation_test("//java/com/foo/app:instrumentation_test")
            .test_app("//java/com/foo/app:test_app"),
        android_instrumentation_test("//java/com/foo/app:self_instrumenting_test")
            .test_app("//java/com/foo/app:test_app_self_instrumenting"));
    runFullBlazeSyncWithNoIssues();
  }

  /** Setup build system provider with {@link BuildSystemProviderWrapper} */
  @Before
  public void setupBuildSystemProvider() {
    BuildSystemProviderWrapper buildSystem = new BuildSystemProviderWrapper(() -> getProject());
    registerExtension(BuildSystemProvider.EP_NAME, buildSystem);
  }

  @Test
  public void deployInfoBuiltCorrectly() throws GetDeployInfoException, ApkProvisionException {
    setupProject();
    Label instrumentorTarget = Label.create("//java/com/foo/app:test_app");
    Label appTarget = Label.create("//java/com/foo/app:app");
    InstrumentationInfo info = new InstrumentationInfo(appTarget, instrumentorTarget);

    BlazeContext context = BlazeContext.create();
    ImmutableList<String> blazeFlags = ImmutableList.of("some_blaze_flag", "some_other_flag");

    // Setup interceptor for fake running of blaze commands and capture details.
    ExternalTaskInterceptor externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

    // Return fake deploy info proto and mocked deploy info data object.
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);

    AndroidDeployInfo fakeInstrumentorProto = AndroidDeployInfo.newBuilder().build();
    AndroidDeployInfo fakeAppProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    when(helper.readDeployInfoProtoForTarget(
            eq(instrumentorTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeInstrumentorProto);
    when(helper.readDeployInfoProtoForTarget(eq(appTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeAppProto);
    when(helper.extractInstrumentationTestDeployInfoAndInvalidateManifests(
            eq(getProject()),
            eq(new File(getExecRoot())),
            eq(fakeInstrumentorProto),
            eq(fakeAppProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeInstrumentationTestApkBuildStep buildStep =
        new BlazeInstrumentationTestApkBuildStep(getProject(), info, blazeFlags, helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).contains(instrumentorTarget.toString());
    assertThat(externalTaskInterceptor.command).contains(appTarget.toString());
    assertThat(externalTaskInterceptor.command).contains("--output_groups=+android_deploy_info");
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
  }

  @Test
  public void deployInfoBuiltCorrectly_selfInstrumentingTest()
      throws GetDeployInfoException, ApkProvisionException {
    setupProject();
    Label instrumentorTarget = Label.create("//java/com/foo/app:test_app_self_instrumenting");
    InstrumentationInfo info = new InstrumentationInfo(null, instrumentorTarget);

    BlazeContext context = BlazeContext.create();
    ImmutableList<String> blazeFlags = ImmutableList.of("some_blaze_flag", "some_other_flag");

    // Setup interceptor for fake running of blaze commands and capture details.
    ExternalTaskInterceptor externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

    // Return fake deploy info proto and mocked deploy info data object.
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);

    AndroidDeployInfo fakeInstrumentorProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    when(helper.readDeployInfoProtoForTarget(
            eq(instrumentorTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeInstrumentorProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeInstrumentorProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeInstrumentationTestApkBuildStep buildStep =
        new BlazeInstrumentationTestApkBuildStep(getProject(), info, blazeFlags, helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).contains(instrumentorTarget.toString());
    assertThat(externalTaskInterceptor.command).contains("--output_groups=+android_deploy_info");
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
  }

  @Test
  public void exceptionDuringDeployInfoExtraction() throws GetDeployInfoException {
    setupProject();
    Label instrumentorTarget = Label.create("//java/com/foo/app:test_app");
    Label appTarget = Label.create("//java/com/foo/app:app");
    InstrumentationInfo info = new InstrumentationInfo(instrumentorTarget, appTarget);

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, messageCollector);

    // Make blaze command invocation always pass.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 0);

    // Return fake deploy info proto and mocked deploy info data object.
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);

    AndroidDeployInfo fakeInstrumentorProto = AndroidDeployInfo.newBuilder().build();
    AndroidDeployInfo fakeAppProto = AndroidDeployInfo.newBuilder().build();
    when(helper.readDeployInfoProtoForTarget(
            eq(instrumentorTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeInstrumentorProto);
    when(helper.readDeployInfoProtoForTarget(eq(appTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeAppProto);
    when(helper.extractInstrumentationTestDeployInfoAndInvalidateManifests(
            any(), any(), any(), any()))
        .thenThrow(new GetDeployInfoException("Fake Exception"));

    // Perform
    BlazeInstrumentationTestApkBuildStep buildStep =
        new BlazeInstrumentationTestApkBuildStep(getProject(), info, ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Could not read apk deploy info from build: Fake Exception");
  }

  @Test
  public void blazeCommandFailed() throws GetDeployInfoException {
    setupProject();
    Label instrumentorTarget = Label.create("//java/com/foo/app:test_app");
    Label appTarget = Label.create("//java/com/foo/app:app");
    InstrumentationInfo info = new InstrumentationInfo(appTarget, instrumentorTarget);

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, messageCollector);

    // Return a non-zero value to indicate blaze command run failure.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 1337);

    // Return fake deploy info proto and mocked deploy info data object.
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);

    AndroidDeployInfo fakeInstrumentorProto = AndroidDeployInfo.newBuilder().build();
    AndroidDeployInfo fakeAppProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    when(helper.readDeployInfoProtoForTarget(
            eq(instrumentorTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeInstrumentorProto);
    when(helper.readDeployInfoProtoForTarget(eq(appTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeAppProto);
    when(helper.extractInstrumentationTestDeployInfoAndInvalidateManifests(
            eq(getProject()),
            eq(new File(getExecRoot())),
            eq(fakeInstrumentorProto),
            eq(fakeAppProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeInstrumentationTestApkBuildStep buildStep =
        new BlazeInstrumentationTestApkBuildStep(getProject(), info, ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Blaze build failed. See Blaze Console for details.");
  }

  @Test
  public void nullExecRoot()
      throws GetDeployInfoException, ApkProvisionException, GetArtifactsException {
    setupProject();
    Label instrumentorTarget = Label.create("//java/com/foo/app:test_app");
    Label appTarget = Label.create("//java/com/foo/app:app");
    InstrumentationInfo info = new InstrumentationInfo(appTarget, instrumentorTarget);
    ImmutableList<String> blazeFlags = ImmutableList.of("some_blaze_flag", "some_other_flag");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, messageCollector);

    // Return null execroot
    // the only way for execroot to be null is for getBlazeInfo() to throw an exception
    BuildSystemProviderWrapper.getInstance(getProject()).setThrowExceptionOnGetBlazeInfo(true);

    // Setup interceptor for fake running of blaze commands and capture details.
    ExternalTaskInterceptor externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

    // Return fake deploy info proto and mocked deploy info data object.
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);

    AndroidDeployInfo fakeInstrumentorProto = AndroidDeployInfo.newBuilder().build();
    AndroidDeployInfo fakeAppProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    when(helper.readDeployInfoProtoForTarget(
            eq(instrumentorTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeInstrumentorProto);
    when(helper.readDeployInfoProtoForTarget(eq(appTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeAppProto);
    when(helper.extractInstrumentationTestDeployInfoAndInvalidateManifests(
            eq(getProject()),
            eq(new File(getExecRoot())),
            eq(fakeInstrumentorProto),
            eq(fakeAppProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeInstrumentationTestApkBuildStep buildStep =
        new BlazeInstrumentationTestApkBuildStep(getProject(), info, blazeFlags, helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages()).contains("Could not locate execroot!");
  }

  /** Saves the latest blaze command and context for later verification. */
  private static class ExternalTaskInterceptor implements ExternalTaskProvider {
    ImmutableList<String> command;
    BlazeContext context;

    @Override
    public ExternalTask build(ExternalTask.Builder builder) {
      command = builder.command.build();
      context = builder.context;
      return scopes -> 0;
    }
  }
}
