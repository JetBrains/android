/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android;

import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.HardwareFeature;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.Keep;
import com.google.idea.blaze.android.functional.AndroidDeviceCompat;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.ExternalTaskProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProviderWrapper;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;

/** Base class for integration tests for Mobile install build step. */
public class MobileInstallBuildStepTestCase extends BlazeAndroidIntegrationTestCase {
  protected Label buildTarget;
  protected ImmutableList<String> blazeFlags;
  protected ImmutableList<String> execFlags;
  protected ExternalTaskInterceptor externalTaskInterceptor;
  protected BlazeContext context;
  protected MessageCollector messageCollector;

  @Before
  public void setupProject() {
    setProjectView(
        "directories:",
        "  java/com/foo/app",
        "targets:",
        "  //java/com/foo/app:app",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");

    workspace.createFile(
        new WorkspacePath("java/com/foo/app/MainActivity.java"),
        "package com.foo.app",
        "import android.app.Activity;",
        "public class MainActivity extends Activity {}");

    setTargetMap(android_binary("//java/com/foo/app:app").src("MainActivity.java"));
    runFullBlazeSyncWithNoIssues();

    // MI invocation flags
    buildTarget = Label.create("//java/com/foo/app:app");
    blazeFlags = ImmutableList.of("some_blaze_flag", "other_blaze_flag");
    execFlags = ImmutableList.of("some_exec_flag", "other_exec_flag");
  }

  /** Setup build system provider with {@link BuildSystemProviderWrapper} */
  @Before
  public void setupBuildSystemProvider() {
    BuildSystemProviderWrapper buildSystem = new BuildSystemProviderWrapper(() -> getProject());
    registerExtensionFirst(BuildSystemProvider.EP_NAME, buildSystem);
  }

  @Before
  public void setupTestDetailCollectors() {
    // Setup interceptor for fake running of blaze commands and capture details.
    externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

    // Collect messages sent to IssueOutput.
    messageCollector = new MessageCollector();
    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, messageCollector);
  }

  /** Saves the latest blaze command and context for later verification. */
  public static class ExternalTaskInterceptor implements ExternalTaskProvider {
    ImmutableList<String> command;
    BlazeContext context;

    @Override
    public ExternalTask build(ExternalTask.Builder builder) {
      command = builder.command.build();
      context = builder.context;
      return scopes -> 0;
    }

    public ImmutableList<String> getCommand() {
      return command;
    }

    public BlazeContext getContext() {
      return context;
    }
  }

  /**
   * A fake android device that returns a mocked launched-device. This class is required because
   * {@link DeviceFutures} and all other implementations of {@link AndroidDevice} are final,
   * therefore we need this to stub out a fake {@link DeviceSession}.
   */
  public static class FakeDevice extends AndroidDeviceCompat {
    @Override
    public ListenableFuture<IDevice> getLaunchedDevice() {
      IDevice device = mock(IDevice.class);
      when(device.getSerialNumber()).thenReturn("serial-number");
      return Futures.immediateFuture(device);
    }

    //
    // All methods below this point has no purpose. Please ignore.
    //
    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public boolean isVirtual() {
      return false;
    }

    @Override
    public AndroidVersion getVersion() {
      return null;
    }

    @Override
    public int getDensity() {
      return 0;
    }

    @Nullable
    @Override
    public List<Abi> getAbis() {
      return null;
    }

    @Override
    public String getSerial() {
      return null;
    }

    @Override
    public boolean supportsFeature(HardwareFeature hardwareFeature) {
      return false;
    }

    @Override
    public String getName() {
      return null;
    }

    // @Override #api42
    @Keep
    public boolean renderLabel(
        SimpleColoredComponent simpleColoredComponent, boolean b, @Nullable String s) {
      return false;
    }

    // @Override #api42
    @Keep
    public void prepareToRenderLabel() {}

    // api40: see new API for canRun below
    @Keep
    public LaunchCompatibility canRun(
        AndroidVersion androidVersion,
        IAndroidTarget iAndroidTarget,
        EnumSet<HardwareFeature> enumSet,
        @Nullable Set<String> set) {
      return null;
    }

    @Override
    public ListenableFuture<IDevice> launch(Project project) {
      return null;
    }

    // @Override #api 3.6
    @Keep
    public ListenableFuture<IDevice> launch(Project project, String s) {
      return null;
    }

    // @Override #api 4.0
    @Keep
    public ListenableFuture<IDevice> launch(Project project, List<String> list) {
      return null;
    }

    // @Override #api 3.6
    @Keep
    @Override
    public boolean isDebuggable() {
      return false;
    }
  }
}
