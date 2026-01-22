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
package com.google.idea.blaze.android.run.test;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo.ManifestWithApks;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link
 * com.google.idea.blaze.android.run.test.BlazeAndroidTestApplicationIdProvider}
 */
@RunWith(JUnit4.class)
public class BlazeAndroidTestApplicationIdProviderTest {
  static ImmutableList<File> nativeSymbols = ImmutableList.of(new File("symbols.so"));
  static ImmutableList<File> testAppApk = ImmutableList.of(new File("test.apk"));
  static ImmutableList<File> appUnderTestApk = ImmutableList.of(new File("app_under_test.apk"));

  @Test
  public void getApplicationIds_bothPackagesPresent() throws Exception {
    ParsedManifest testManifest = stubManifest("test.package.name");
    ParsedManifest appManifest = stubManifest("package.name");
    BlazeAndroidDeployInfo deployInfo =
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
            new ManifestWithApks(testManifest, testAppApk), // Test App (main)
            new ManifestWithApks(appManifest, appUnderTestApk), // App Under Test (test target)
            nativeSymbols);
    ApkBuildStep mockBuildStep = mock(ApkBuildStep.class);
    when(mockBuildStep.getDeployInfo()).thenReturn(deployInfo);

    BlazeAndroidTestApplicationIdProvider provider =
        new BlazeAndroidTestApplicationIdProvider(mockBuildStep);

    // Check test package name
    assertThat(provider.getTestPackageName()).isEqualTo("test.package.name");
    // Check app under test package name
    assertThat(provider.getPackageName()).isEqualTo("package.name");
  }

  @Test
  public void getTestPackageName_noPackageNameInMergedManifest() throws Exception {
    ParsedManifest testManifest = stubManifest(null);
    ParsedManifest appManifest = stubManifest("package.name");
    try {
      BlazeAndroidDeployInfo deployInfo =
          BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
              new ManifestWithApks(testManifest, testAppApk), // Test App (main) - null package name
              new ManifestWithApks(appManifest, appUnderTestApk),     // App Under Test (test target)
              nativeSymbols);
      fail();
    } catch (ApkProvisionException ex) {
      // An exception should be thrown if the package name is not available because it's a
      // serious error and should not fail silently. In this case we shouldn't fallback to
      // the main package name, because the test package will be invalid as long as test
      // manifest is missing package name.
    }
  }

  @Test
  public void getPackageName_noPackageNameInMergedManifest() throws Exception {
    ParsedManifest testManifest = stubManifest("test.package.name");
    ParsedManifest appManifest = stubManifest(null);
    try {
      BlazeAndroidDeployInfo deployInfo =
          BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
              new ManifestWithApks(testManifest, testAppApk), // Test App (main)
              new ManifestWithApks(appManifest, appUnderTestApk), // App Under Test (test target) - null package name
              nativeSymbols);
      fail();
    } catch (ApkProvisionException ex) {
      // An exception should be thrown if the package name is not available because it's a
      // serious error and should not fail silently. In this case we shouldn't fallback to
      // the main package name, because the test package will be invalid as long as test
      // manifest is missing package name.
    }
  }

  @Test
  public void getPackageName_noMergedManifest() throws Exception {
    ParsedManifest testManifest = stubManifest("test.package.name");
    BlazeAndroidDeployInfo deployInfo =
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
            new ManifestWithApks(testManifest, testAppApk), // Test App (main)
            /* testTargetMergedManifestAndApks= */ null,
            ImmutableList.of()); // The original used ImmutableList.of() for symbols
    ApkBuildStep mockBuildStep = mock(ApkBuildStep.class);
    when(mockBuildStep.getDeployInfo()).thenReturn(deployInfo);

    BlazeAndroidTestApplicationIdProvider provider =
        new BlazeAndroidTestApplicationIdProvider(mockBuildStep);
    assertThat(provider.getPackageName()).isEqualTo("test.package.name");
  }

  private static ParsedManifest stubManifest(String packageName) {
    return new ParsedManifest(packageName, ImmutableList.of(), null);
  }
}
