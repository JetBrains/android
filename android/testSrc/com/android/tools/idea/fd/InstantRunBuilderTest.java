/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.fd;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.idea.gradle.run.GradleTaskRunner;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.run.AndroidRunConfigContext;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.InstalledApkCache;
import com.android.tools.idea.run.InstalledPatchCache;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.hash.HashCode;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InstantRunBuilderTest {
  private static final String APPLICATION_ID = "instant.run";
  private static final List<String> ASSEMBLE_TASKS = ImmutableList.of(":app:assemble");
  private static final List<String> CLEAN_TASKS = ImmutableList.of("clean", ":app:gen");

  private static final String DUMPSYS_PACKAGE_EXISTS = "Package [" + APPLICATION_ID + "]";
  private static final String DUMPSYS_NO_SUCH_PACKAGE = "";

  @Language("XML")
  private static final String BUILD_INFO =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<instant-run\n" +
    "    abi=\"armeabi,armeabi-v7a\"\n" +
    "    api-level=\"24\"\n" +
    "    density=\"560dpi\"\n" +
    "    format=\"7\"\n" +
    "    timestamp=\"100\" >\n" +
    "\n" +
    "    <artifact\n" +
    "        location=\"/app/build/outputs/apk/app-debug.apk\"\n" +
    "        type=\"MAIN\" />\n" +
    "\n" +
    "</instant-run>";

  @Language("XML")
  private static final String BUILD_INFO_RELOAD_DEX =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<instant-run\n" +
    "    abi=\"armeabi,armeabi-v7a\"\n" +
    "    api-level=\"24\"\n" +
    "    density=\"560dpi\"\n" +
    "    format=\"7\"\n" +
    "    verifier=\"COMPATIBLE\"\n" +
    "    timestamp=\"100\" >\n" +
    "\n" +
    "    <artifact\n" +
    "        location=\"/app/build/intermediates/reload-dex/debug/classes.dex\"\n" +
    "        type=\"RELOAD_DEX\" />\n" +
    "\n" +
    "</instant-run>";

  @Language("XML")
  private static final String BUILD_INFO_NO_ARTIFACTS =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<instant-run\n" +
    "    abi=\"armeabi,armeabi-v7a\"\n" +
    "    api-level=\"24\"\n" +
    "    density=\"560dpi\"\n" +
    "    format=\"7\"\n" +
    "    verifier=\"FIELD_ADDED\"\n" + // e.g. field was added, but no cold swap patches since device api < 21
    "    timestamp=\"100\" >\n" +
    "\n" +
    "</instant-run>";

  private IDevice myDevice;
  private InstantRunContext myInstantRunContext;
  private AndroidRunConfigContext myRunConfigContext;
  private InstantRunTasksProvider myTasksProvider;

  private InstantRunBuilder.InstantRunClientDelegate myInstantRunClientDelegate;
  private String myDeviceBuildTimetamp;
  private boolean myAppInForeground;

  private InstalledPatchCache myInstalledPatchCache;
  private InstalledApkCache myInstalledApkCache;
  private File myApk;
  private String myDumpsysPackageOutput;

  private RecordingTaskRunner myTaskRunner;
  private InstantRunBuilder myBuilder;

  @Before
  public void setUp() throws Exception {
    myDevice = mock(IDevice.class);
    when(myDevice.getSerialNumber()).thenReturn("device1-serial");

    myInstalledPatchCache = new InstalledPatchCache();

    myInstantRunContext = mock(InstantRunContext.class);
    when(myInstantRunContext.getInstantRunBuildInfo())
      .thenReturn(InstantRunBuildInfo.get(BUILD_INFO))
      .thenReturn(InstantRunBuildInfo.get(BUILD_INFO_RELOAD_DEX));
    when(myInstantRunContext.getApplicationId()).thenReturn(APPLICATION_ID);
    when(myInstantRunContext.getInstalledPatchCache()).thenReturn(myInstalledPatchCache);
    when(myInstantRunContext.getGradlePluginVersion()).thenReturn(GradleVersion.parse("3.0.0-alpha4"));

    myRunConfigContext = new AndroidRunConfigContext();
    myRunConfigContext.setTargetDevices(DeviceFutures.forDevices(Collections.singletonList(myDevice)));

    myTasksProvider = mock(InstantRunTasksProvider.class);

    ListMultimap<Path, String> assembleTasks = ArrayListMultimap.create();
    assembleTasks.putAll(Paths.get("project_path"), ASSEMBLE_TASKS);
    when(myTasksProvider.getFullBuildTasks()).thenReturn(assembleTasks);

    myInstalledApkCache = new InstalledApkCache() {
      @Override
      protected String executeShellCommand(@NotNull IDevice device, @NotNull String cmd, long timeout, @NotNull TimeUnit timeUnit)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
        return myDumpsysPackageOutput;
      }
    };
    myApk = FileUtil.createTempFile("foo", "apk");

    myTaskRunner = new RecordingTaskRunner();
    myInstantRunClientDelegate = createInstantRunClientDelegate();

    myBuilder =
      new InstantRunBuilder(myDevice, myInstantRunContext, myRunConfigContext, myTasksProvider, false,
                            myInstalledApkCache, myInstantRunClientDelegate);
  }

  @NotNull
  private InstantRunBuilder.InstantRunClientDelegate createInstantRunClientDelegate() {
    return new InstantRunBuilder.InstantRunClientDelegate() {
      @Override
      public String getDeviceBuildTimestamp(@NotNull IDevice device, @NotNull InstantRunContext instantRunContext) {
        return myDeviceBuildTimetamp;
      }

      @Override
      public boolean isAppInForeground(@NotNull IDevice device, @NotNull InstantRunContext context) {
        return myAppInForeground;
      }
    };
  }

  @After
  public void tearDown() throws Exception {
    FileUtil.delete(myApk);
    Disposer.dispose(myInstalledPatchCache);
    Disposer.dispose(myInstalledApkCache);
  }

  @Test
  public void fullBuildIfNoDevice() throws Exception {
    InstantRunBuilder builder =
      new InstantRunBuilder(null, myInstantRunContext, myRunConfigContext, myTasksProvider, false,
                            myInstalledApkCache, myInstantRunClientDelegate);
    builder.build(myTaskRunner, Arrays.asList("-Pdevice.api=14", "-Pprofiling=on"));
    assertEquals(
      "gradlew -Pdevice.api=14 -Pprofiling=on -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK" +
      " --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildIfNoLocalTimestamp() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_NO_SUCH_PACKAGE;
    when(myInstantRunContext.getInstantRunBuildInfo()).thenReturn(null);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildWhenPackageNotInstalledOnDevice() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_NO_SUCH_PACKAGE;
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildWhenPackageNotInstalledForDefaultUser() throws Exception {
    myDumpsysPackageOutput =
      "Packages:\n" +
      "  Package [instant.run] (a1df9a8):\n" +
      "    User 0:  installed=false hidden=false stopped=true notLaunched=true enabled=0\n" +
      "      runtime permissions:\n" +
      "    User 10:  installed=true hidden=false stopped=true notLaunched=false enabled=0\n" +
      "      runtime permissions:\n";
    myDeviceBuildTimetamp = "100";
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildIfBuildTimestampsDoNotMatch() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "123";
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    setUpDeviceForHotSwap();

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildIfBelowApi15() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(10, null));
    setUpDeviceForHotSwap();

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildIfFirstInstallation() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void coldswapIfManifestResourceChanged() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));

    myInstalledPatchCache.setInstalledManifestResourcesHash(myDevice, APPLICATION_ID, HashCode.fromInt(1));
    when(myInstantRunContext.getManifestResourcesHash()).thenReturn(HashCode.fromInt(2));

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildIfAppNotRunningOnApiBelow21() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    myAppInForeground = false;
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));
    setUpDeviceForHotSwap();

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void fullBuildIfExecutorSwitchedOnApiBelow21() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    myAppInForeground = true;
    myRunConfigContext.setSameExecutorAsPreviousSession(false);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));

    setUpDeviceForHotSwap();

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void coldSwapBuildIfAppNotRunning() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    myAppInForeground = false;
    myRunConfigContext.setSameExecutorAsPreviousSession(true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    setUpDeviceForHotSwap();

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void coldSwapBuildIfUsingMultipleProcesses() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    myAppInForeground = true;
    myRunConfigContext.setSameExecutorAsPreviousSession(true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    setUpDeviceForHotSwap();

    when(myInstantRunContext.usesMultipleProcesses()).thenReturn(true);

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void hotSwapBuild() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    myAppInForeground = true;
    myRunConfigContext.setSameExecutorAsPreviousSession(true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    setUpDeviceForHotSwap();

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void noRebuildIfNoArtifactsAfterHotswapBuild() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    myAppInForeground = true;
    myRunConfigContext.setSameExecutorAsPreviousSession(true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    setUpDeviceForHotSwap();

    when(myInstantRunContext.getInstantRunBuildInfo())
      .thenReturn(InstantRunBuildInfo.get(BUILD_INFO))
      .thenReturn(InstantRunBuildInfo.get(BUILD_INFO_NO_ARTIFACTS));

    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void flightRecorderOptions() throws Exception {
    InstantRunBuilder builder =
      new InstantRunBuilder(null, myInstantRunContext, myRunConfigContext, myTasksProvider, true,
                            myInstalledApkCache, myInstantRunClientDelegate);
    builder.build(myTaskRunner, Arrays.asList("-Pdevice.api=14", "-Pprofiling=on"));
    assertEquals(
      "gradlew -Pdevice.api=14 -Pprofiling=on -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK"
      + " --info --full-stacktrace --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());
  }

  @Test
  public void alternativeUiForHotswap() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_PACKAGE_EXISTS;
    myDeviceBuildTimetamp = "100";
    myAppInForeground = true;
    myRunConfigContext.setSameExecutorAsPreviousSession(true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));
    setUpDeviceForHotSwap();

    // normally we'd do a hotswap
    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
      myTaskRunner.getBuilds());

    // but a full apk is forced if this was launched from the new UI
    boolean[] couldHaveInvokedHotswapValues = new boolean[] { true, false };
    for (boolean couldHaveHotswaped: couldHaveInvokedHotswapValues) {
      myRunConfigContext.setForceColdSwap(true, couldHaveHotswaped);
      myTaskRunner = new RecordingTaskRunner();
      myBuilder.build(myTaskRunner, Collections.emptyList());
      assertEquals(
        "gradlew -Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY -Pandroid.injected.coldswap.mode=MULTIAPK --no-build-cache :app:assemble",
        myTaskRunner.getBuilds());
    }
  }

  @Test
  public void testBuildCacheOptionWithEarlierVersionsOfGradlePlugin() throws Exception {
    myDumpsysPackageOutput = DUMPSYS_NO_SUCH_PACKAGE;
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));

    // Regresssion test for bug 63926980: For plugin versions prior to 3.0.0-alpha4, --no-build-cache should not be passed
    when(myInstantRunContext.getGradlePluginVersion()).thenReturn(GradleVersion.parse("2.3.2"));
    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK :app:assemble",
      myTaskRunner.getBuilds());

    // Test the boundary case at 3.0.0-alpha3
    when(myInstantRunContext.getGradlePluginVersion()).thenReturn(GradleVersion.parse("3.0.0-alpha3"));
    myTaskRunner = new RecordingTaskRunner();
    myBuilder.build(myTaskRunner, Collections.emptyList());
    assertEquals(
      "gradlew -Pandroid.optional.compilation=INSTANT_DEV,FULL_APK -Pandroid.injected.coldswap.mode=MULTIAPK :app:assemble",
      myTaskRunner.getBuilds());
  }

  private void setUpDeviceForHotSwap() {
    HashCode resourcesHash = HashCode.fromInt(1);
    myInstalledPatchCache.setInstalledManifestResourcesHash(myDevice, APPLICATION_ID, resourcesHash);
    when(myInstantRunContext.getManifestResourcesHash()).thenReturn(resourcesHash);
  }

  private static class RecordingTaskRunner implements GradleTaskRunner {
    private StringBuilder sb = new StringBuilder(100);

    @Override
    public boolean run(@NotNull ListMultimap<Path, String> tasks,
                       @Nullable BuildMode buildMode,
                       @NotNull List<String> commandLineArguments) {
      if (sb.length() > 0) {
        sb.append("\n");
      }

      Set<Path> rootPaths = tasks.keys().elementSet();
      for (Iterator<Path> iterator = rootPaths.iterator(); iterator.hasNext(); ) {
        Path rootPath = iterator.next();
        List<String> projectTasks = tasks.get(rootPath);
        sb.append("gradlew ");
        sb.append(Joiner.on(' ').join(commandLineArguments));
        sb.append(" ");
        sb.append(Joiner.on(' ').join(projectTasks));

        if(iterator.hasNext()) {
          sb.append("\n");
        }
      }
      return true;
    }

    public String getBuilds() {
      return sb.toString();
    }
  }
}
