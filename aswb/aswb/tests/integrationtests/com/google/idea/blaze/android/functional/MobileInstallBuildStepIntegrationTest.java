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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.run.DeviceFutures;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.MobileInstallBuildStepTestCase;
import com.google.idea.blaze.android.run.binary.mobileinstall.AdbTunnelConfigurator;
import com.google.idea.blaze.android.run.binary.mobileinstall.AdbTunnelConfigurator.AdbTunnelConfiguratorProvider;
import com.google.idea.blaze.android.run.binary.mobileinstall.MobileInstallBuildStep;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.base.async.process.ExternalTaskProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProviderWrapper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link MobileInstallBuildStep} */
@RunWith(JUnit4.class)
public final class MobileInstallBuildStepIntegrationTest extends MobileInstallBuildStepTestCase {
  @Test
  public void deployInfoBuiltCorrectly() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.getContext()).isEqualTo(context);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.getCommand())
        .containsAnyOf("serial-number", "serial-number:tcp:0");
    assertThat(externalTaskInterceptor.getCommand()).contains(buildTarget.toString());
  }

  @Test
  public void deployInfoBuiltCorrectly_withInactiveAdbTunnelSetup() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Setup mock AdbTunnelConfigurator for testing device port flags.
    AdbTunnelConfigurator tunnelConfigurator = mock(AdbTunnelConfigurator.class);
    when(tunnelConfigurator.isActive()).thenReturn(false);
    when(tunnelConfigurator.getAdbServerPort()).thenReturn(12345);
    registerExtension(AdbTunnelConfiguratorProvider.EP_NAME, providerCxt -> tunnelConfigurator);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.getContext()).isEqualTo(context);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.getCommand()).contains("--device");
    // workaround for inconsistent stateful AndroidDebugBridge class.
    assertThat(externalTaskInterceptor.getCommand())
        .containsAnyOf("serial-number", "serial-number:tcp:0");
    assertThat(externalTaskInterceptor.getCommand()).contains(buildTarget.toString());
  }

  @Test
  public void deployInfoBuiltCorrectly_withAdbTunnelSetup() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Setup mock AdbTunnelConfigurator for testing device port flags.
    AdbTunnelConfigurator tunnelConfigurator = mock(AdbTunnelConfigurator.class);
    when(tunnelConfigurator.isActive()).thenReturn(true);
    when(tunnelConfigurator.getAdbServerPort()).thenReturn(12345);
    registerExtensionFirst(AdbTunnelConfiguratorProvider.EP_NAME, providerCxt -> tunnelConfigurator);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.getContext()).isEqualTo(context);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.getCommand()).contains("--device");
    assertThat(externalTaskInterceptor.getCommand()).contains("serial-number:tcp:12345");
    assertThat(externalTaskInterceptor.getCommand()).contains(buildTarget.toString());
  }

  @Test
  public void deployInfoBuiltCorrectly_withNullAdbTunnelSetup() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Do not pass AdbTunnelConfigurator.
    registerExtension(AdbTunnelConfiguratorProvider.EP_NAME, providerCxt -> null);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.getContext()).isEqualTo(context);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.getCommand()).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.getCommand()).contains("--device");
    // workaround for inconsistent stateful AndroidDebugBridge class.
    assertThat(externalTaskInterceptor.getCommand())
        .containsAnyOf("serial-number", "serial-number:tcp:0");
    assertThat(externalTaskInterceptor.getCommand()).contains(buildTarget.toString());
  }

  @Test
  public void moreThanOneDevice() throws Exception {
    // Make blaze command invocation always pass.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 0);

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures =
        new DeviceFutures(ImmutableList.of(new FakeDevice(), new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Only one device can be used with mobile-install.");
  }

  @Test
  public void exceptionDuringDeployInfoExtraction() throws Exception {
    // Make blaze command invocation always pass.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 0);

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(any(), any(), any()))
        .thenThrow(new GetDeployInfoException("Fake Exception"));

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Could not read apk deploy info from build: Fake Exception");
  }

  @Test
  public void blazeCommandFailed() throws Exception {
    // Return a non-zero value to indicate blaze command run failure.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 1337);

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Blaze build failed. See Blaze Console for details.");
  }

  @Test
  public void nullExecRoot() throws Exception {
    // Return null execroot
    // the only way for execroot to be null is for getBlazeInfo() to throw an exception
    BuildSystemProviderWrapper.getInstance(getProject()).setThrowExceptionOnGetBlazeInfo(true);

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages()).contains("Could not locate execroot!");
  }
}
