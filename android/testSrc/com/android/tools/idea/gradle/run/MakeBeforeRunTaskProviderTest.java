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

import com.android.ddmlib.*;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.run.AndroidAppRunConfigurationBase;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ThreeState;
import org.apache.commons.io.FileUtils;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class MakeBeforeRunTaskProviderTest extends PlatformTestCase {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myIdeAndroidProject;
  @Mock private AndroidDevice myDevice;
  @Mock private IDevice myLaunchedDevice;
  @Mock private AndroidAppRunConfigurationBase myRunConfiguration;
  @Mock private AndroidModuleModel myAndroidModel2;
  @Mock private IdeAndroidProject myIdeAndroidProject2;
  private Module[] myModules;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myDevice.getLaunchedDevice()).thenReturn(Futures.immediateFuture(myLaunchedDevice));
    when(myLaunchedDevice.getVersion()).thenAnswer(invocation -> myDevice.getVersion());
    setupDeviceConfig(myLaunchedDevice, "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                                        "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27");

    myModules = new Module[]{getModule()};

    setUpModuleAsAndroidModule();
  }

  public void testDeviceSpecificArguments() throws IOException {
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    List<String> arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, Collections.singletonList(myDevice));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"));
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi,x86"));
    for (String argument : arguments) {
      assertFalse("codename should not be set for a released version", argument.startsWith("-Pandroid.injected.build.codename"));
    }
  }

  public void testPreviewDeviceArguments() throws IOException {
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, "N"));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    List<String> arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, Collections.singletonList(myDevice));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=23"));
    assertTrue(arguments.contains("-Pandroid.injected.build.codename=N"));
  }

  public void testPreviewDeviceArgumentsForBundleConfiguration() throws IOException {
    myRunConfiguration = mock(AndroidRunConfiguration.class);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, "N"));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    myRunConfiguration.DEPLOY = true;
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true;

    List<String> arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, Collections.singletonList(myDevice));

    String expectedJson =
      "{\"sdk_version\":23,\"screen_density\":640,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}";
    assertExpectedJsonFile(arguments, expectedJson);
  }

  /**
   * For a pre-L device, deploying an app with at least a dynamic feature should result
   * in using the "select apks from bundle" task (as opposed to the regular "assemble" task.
   */
  public void testDeviceArgumentsForPreLollipopDeviceWithDynamicFeature() throws IOException {
    // Setup an additional Dynamic Feature module
    Module featureModule = createModule("feature1");
    setUpModuleAsAndroidModule(featureModule, myAndroidModel2, myIdeAndroidProject2);
    when(myIdeAndroidProject2.getProjectType()).thenReturn(PROJECT_TYPE_DYNAMIC_FEATURE);
    when(myIdeAndroidProject.getDynamicFeatures()).thenReturn(ImmutableList.of(":feature1"));

    // Setup a pre-L device
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    myModules = new Module[]{getModule(), featureModule};
    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    List<String> arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, Collections.singletonList(myDevice));

    assertExpectedJsonFile(arguments, "{\"sdk_version\":20,\"screen_density\":640,\"supported_abis\":[\"armeabi\"]}");
  }

  /**
   * For a pre-L device, deploying an app with no a dynamic feature should result
   * in using the the regular "assemble" task.
   */
  public void testDeviceArgumentsForPreLollipopDevice() throws IOException {
    // Setup a pre-L device
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    List<String> arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, Collections.singletonList(myDevice));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"));
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi"));
  }

  public void testMultipleDeviceArguments() throws IOException {
    AndroidDevice device1 = mock(AndroidDevice.class);
    AndroidDevice device2 = mock(AndroidDevice.class);

    when(device1.getVersion()).thenReturn(new AndroidVersion(23, null));
    when(device1.getDensity()).thenReturn(640);
    when(device1.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    when(device2.getVersion()).thenReturn(new AndroidVersion(22, null));
    when(device2.getDensity()).thenReturn(480);
    when(device2.getAbis()).thenReturn(ImmutableList.of(Abi.X86, Abi.X86_64));

    List<String> arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, ImmutableList.of(device1, device2));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=22"));
    for (String argument : arguments) {
      assertFalse("ABIs should not be passed to Gradle when there are multiple devices",
                  argument.startsWith("-Pandroid.injected.build.abi"));
    }
  }

  private static void assertExpectedJsonFile(List<String> arguments, String expectedJson) throws IOException {
    assertThat(arguments.size()).isEqualTo(1);
    String args = arguments.get(0);
    assertThat(args).startsWith("-Pandroid.inject.apkselect.config=");
    String path = args.substring(args.lastIndexOf('=') + 1);
    assertThat(path).isNotEmpty();
    File jsonFile = new File(path);
    assertThat(jsonFile.exists()).isTrue();
    assertThat(FileUtils.readFileToString(jsonFile)).isEqualTo(expectedJson);
    //noinspection ResultOfMethodCallIgnored  // Test code only
    jsonFile.delete();
  }

  private void setUpModuleAsAndroidModule() {
    setUpModuleAsAndroidModule(getModule(), myAndroidModel, myIdeAndroidProject);
  }

  private void setUpModuleAsAndroidModule(Module module, AndroidModuleModel androidModel, IdeAndroidProject ideAndroidProject) {
    setUpModuleAsGradleModule(module);

    when(androidModel.getAndroidProject()).thenReturn(ideAndroidProject);

    AndroidModelFeatures androidModelFeatures = mock(AndroidModelFeatures.class);
    when(androidModelFeatures.isTestedTargetVariantsSupported()).thenReturn(false);
    when(androidModel.getFeatures()).thenReturn(androidModelFeatures);

    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    JpsAndroidModuleProperties state = androidFacet.getConfiguration().getState();
    assertNotNull(state);
    state.ASSEMBLE_TASK_NAME = "assembleTask2";
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet("afterSyncTask1", "afterSyncTask2");
    state.COMPILE_JAVA_TASK_NAME = "compileTask2";

    androidFacet.getConfiguration().setModel(androidModel);
  }

  private void setUpModuleAsGradleModule(Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR + module.getName();

    GradleProject gradleProjectStub = new GradleProjectStub(emptyList(), GRADLE_PATH_SEPARATOR + module.getName(),
                                                            getBaseDirPath(getProject()));
    GradleModuleModel model = new GradleModuleModel(module.getName(), gradleProjectStub, emptyList(), null, null, null, null);
    gradleFacet.setGradleModuleModel(model);
  }

  @SuppressWarnings("SameParameterValue")
  private static void setupDeviceConfig(IDevice device, String config)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    doAnswer(invocation -> {
      // get the 2nd arg (the receiver to feed it the lines).
      IShellOutputReceiver receiver = invocation.getArgument(1);
      byte[] byteArray = (config + "\n").getBytes(Charsets.UTF_8);
      receiver.addOutput(byteArray, 0, byteArray.length);
      return null;
    }).when(device).executeShellCommand(anyString(), any(), anyLong(), any());
  }

  public void testRunGradleSyncWithPostBuildSyncSupported() {
    Module app = createModule("app");
    setUpModuleAsAndroidModule(app, myAndroidModel, myIdeAndroidProject);
    when(myRunConfiguration.getModules()).thenReturn(new Module[]{app});
    // Simulate the case when post build sync is supported.
    when(myAndroidModel.getFeatures().isPostBuildSyncSupported()).thenReturn(true);
    GradleSyncInvoker syncInvoker = new IdeComponents(myProject).mockApplicationService(GradleSyncInvoker.class);
    GradleSyncState syncState = new IdeComponents(myProject).mockProjectService(GradleSyncState.class);
    when(syncState.isSyncNeeded()).thenReturn(ThreeState.YES);
    MakeBeforeRunTaskProvider provider = new MakeBeforeRunTaskProvider(myProject);

    // Invoke method to test.
    provider.runGradleSyncIfNeeded(myRunConfiguration, mock(DataContext.class));
    // Gradle sync should not be invoked.
    verify(syncInvoker, never()).requestProjectSync(eq(myProject), any(), any());
  }

  public void testRunGradleSyncWithPostBuildSyncNotSupported() {
    Module app = createModule("app");
    setUpModuleAsAndroidModule(app, myAndroidModel, myIdeAndroidProject);
    when(myRunConfiguration.getModules()).thenReturn(new Module[]{app});
    // Simulate the case when post build sync is NOT supported.
    when(myAndroidModel.getFeatures().isPostBuildSyncSupported()).thenReturn(false);
    GradleSyncInvoker syncInvoker = new IdeComponents(myProject).mockApplicationService(GradleSyncInvoker.class);
    GradleSyncState syncState = new IdeComponents(myProject).mockProjectService(GradleSyncState.class);
    when(syncState.isSyncNeeded()).thenReturn(ThreeState.YES);

    MakeBeforeRunTaskProvider provider = new MakeBeforeRunTaskProvider(myProject);

    // Invoke method to test.
    provider.runGradleSyncIfNeeded(myRunConfiguration, mock(DataContext.class));
    // Gradle sync should be invoked to make sure Android models are up-to-date.
    verify(syncInvoker, times(1)).requestProjectSync(eq(myProject), any(), any());
  }
}