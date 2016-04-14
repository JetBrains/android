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
package com.android.tools.idea.gradle.run;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.fd.FileChangeListener;
import com.android.tools.idea.fd.InstantRunSettings.ColdSwapMode;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.run.AndroidDevice;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GradleInvokerOptionsTest {
  private GradleInvokerOptions.GradleTasksProvider myTasksProvider;
  private AndroidDevice myDevice;
  private List<AndroidDevice> myDevices;

  private static final RunAsValidator ourRunAsSupported = new RunAsValidator() {
    @Override
    public boolean hasWorkingRunAs(@NotNull AndroidDevice device) {
      return true;
    }
  };

  private static final RunAsValidator ourRunAsNotSupported = new RunAsValidator() {
    @Override
    public boolean hasWorkingRunAs(@NotNull AndroidDevice device) {
      return false;
    }
  };

  private static final List<String> ASSEMBLE_TASKS = ImmutableList.of(":app:assemble");
  private static final List<String> CLEAN_TASKS = ImmutableList.of("clean", ":app:generateSources");
  private static final List<String> INCREMENTAL_TASKS = ImmutableList.of("incremental");

  @Before
  public void setup() {
    myTasksProvider = mock(GradleInvokerOptions.GradleTasksProvider.class);

    when(myTasksProvider.getTasksFor(BuildMode.ASSEMBLE, GradleInvoker.TestCompileType.NONE)).thenReturn(ASSEMBLE_TASKS);
    when(myTasksProvider.getCleanAndGenerateSourcesTasks()).thenReturn(CLEAN_TASKS);
    when(myTasksProvider.getIncrementalDexTasks()).thenReturn(INCREMENTAL_TASKS);

    myDevice = mock(AndroidDevice.class);
    myDevices = Collections.singletonList(myDevice);
  }

  @Test
  public void userGoalsHaveNoInstantRunOptions() throws Exception {
    GradleInvokerOptions.BuildOptions buildOptions = new GradleInvokerOptions.BuildOptions(myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, buildOptions, myTasksProvider, ourRunAsSupported, "foo", null);

    assertEquals(options.tasks, Collections.singletonList("foo"));
    assertTrue("Command line arguments aren't set for user goals", options.commandLineArguments.isEmpty());
  }

  @Test
  public void unitTestsHaveNoInstantRunOptions() throws Exception {
    List<String> tasks = Collections.singletonList("compileUnitTest");
    when(myTasksProvider.getUnitTestTasks(BuildMode.COMPILE_JAVA)).thenReturn(tasks);

    GradleInvokerOptions.BuildOptions buildOptions = new GradleInvokerOptions.BuildOptions(myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.JAVA_TESTS, buildOptions, myTasksProvider, ourRunAsSupported, null, null);

    assertEquals(tasks, options.tasks);
    assertTrue("Command line arguments aren't set for unit test tasks", options.commandLineArguments.isEmpty());
  }

  @Test
  public void buildWithoutInstantRun() throws Exception {
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    GradleInvokerOptions.BuildOptions buildOptions = new GradleInvokerOptions.BuildOptions(myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, buildOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.api=20"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.abi=armeabi,x86"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.density=xxxhdpi"));
    assertEquals(ASSEMBLE_TASKS, options.tasks);
  }

  @Test
  public void cleanBuild() throws Exception {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(true, false, false);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(true, false, true, false, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.api=20"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.abi=armeabi,x86"));

    // should have clean + build tasks
    HashSet<String> expected = Sets.newHashSet(CLEAN_TASKS);
    expected.addAll(ASSEMBLE_TASKS);

    assertEquals(expected, Sets.newHashSet(options.tasks));
  }

  @Test
  public void fullBuild() throws Exception {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(true, false, false);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));
    when(myDevice.getDensity()).thenReturn(640);

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, true, true, false, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.api=20"));
    assertEquals(ASSEMBLE_TASKS, options.tasks);
  }

  @Test
  public void incrementalBuild() throws Exception {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(false, true, false);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(21, null));
    when(myDevice.getDensity()).thenReturn(640);

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, false, true, false, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,LOCAL_RES_ONLY"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.api=21"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.density=xxxhdpi"));
    assertEquals(INCREMENTAL_TASKS, options.tasks);
  }

  @Test
  public void fullBuildIfUsingMultipleProcesses() throws Exception {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(false, true, false);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(21, null));
    when(myDevice.getDensity()).thenReturn(640);

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, false, true, true, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY,LOCAL_RES_ONLY"));
    assertEquals(INCREMENTAL_TASKS, options.tasks);
  }

  @Test
  public void incrementalBuildAppNotRunning() throws Exception {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(false, false, true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(21, null));
    when(myDevice.getDensity()).thenReturn(640);

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, false, false, false, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY,LOCAL_JAVA_ONLY"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.api=21"));
    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.density=xxxhdpi"));
    assertEquals(INCREMENTAL_TASKS, options.tasks);
  }

  @Test
  public void disableDexswapIfColdswapDisabled() {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(false, false, true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(21, null));

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, false, false, false, false, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY"));
    assertEquals(ASSEMBLE_TASKS, options.tasks);
  }

  @Test
  public void disableDexswapOnApiLessThan21() {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(false, false, true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(19, null));

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, false, false, false, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY"));
    assertEquals(ASSEMBLE_TASKS, options.tasks);
  }

  @Test
  public void disableDexswapIfRunAsIsBroken() {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(false, false, true);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, null));

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, false, false, false, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsNotSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.optional.compilation=INSTANT_DEV,RESTART_ONLY"));
    assertEquals(ASSEMBLE_TASKS, options.tasks);
  }

  @Test
  public void previewPlatformOptions() throws Exception {
    FileChangeListener.Changes changes = new FileChangeListener.Changes(false, true, false);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, "N"));
    when(myDevice.getDensity()).thenReturn(640);

    GradleInvokerOptions.InstantRunBuildOptions instantRunOptions =
      new GradleInvokerOptions.InstantRunBuildOptions(false, false, true, false, true, ColdSwapMode.DEFAULT, changes, myDevices);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(GradleInvoker.TestCompileType.NONE, instantRunOptions, myTasksProvider,
                                  ourRunAsSupported, null, null);

    assertTrue(options.commandLineArguments.contains("-Pandroid.injected.build.api=24"));
  }
}