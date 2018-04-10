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

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.run.AndroidAppRunConfigurationBase;
import com.android.tools.idea.run.AndroidBundleRunConfiguration;
import com.android.tools.idea.run.AndroidDevice;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.apache.commons.io.FileUtils;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MakeBeforeRunTaskProviderTest extends IdeaTestCase {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myIdeAndroidProject;
  @Mock private AndroidDevice myDevice;
  @Mock private AndroidAppRunConfigurationBase myRunConfiguration;
  @Mock private AndroidModuleModel myAndroidModel2;
  @Mock private IdeAndroidProject myIdeAndroidProject2;
  private Module[] myModules;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
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
    myRunConfiguration = mock(AndroidBundleRunConfiguration.class);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, "N"));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    List<String> arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, Collections.singletonList(myDevice));

    String expectedJson = "{\"sdk_version\":23,\"screen_density\":640,\"supported_abis\":[\"armeabi\"]}";
    assertExpectedJsonFile(arguments, expectedJson);
  }

  /**
   * For a pre-L device, deploying an app with at least a dynamic feature should result
   * in using the "select apks from bundle" task (as opposed to the regular "assemble" task.
   */
  public void testDeviceArgumentsForPreLollipopDeviceWithDynamicFeature() throws IOException {
    // Setup an additional feature module
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

    GradleModuleModel model = new GradleModuleModel(module.getName(), Collections.emptyList(), GRADLE_PATH_SEPARATOR + module.getName(),
                                                    getBaseDirPath(getProject()), null, null);
    gradleFacet.setGradleModuleModel(model);
  }
}