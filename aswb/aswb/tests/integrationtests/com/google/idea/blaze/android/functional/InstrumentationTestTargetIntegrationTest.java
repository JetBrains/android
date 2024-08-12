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

import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for android_instrumentation_test dependencies.
 *
 * <p>Note: Technically this test should be an invoking blaze integration test to truly verify that
 * instrumentation test dependencies work as expected. However, this test is still useful for
 * verifying that instrumentation test related IDE info work correctly.
 *
 * <p>TODO(b/141650036): Change this test into an invoking blaze integration test.
 */
@RunWith(JUnit4.class)
public class InstrumentationTestTargetIntegrationTest extends BlazeAndroidIntegrationTestCase {
  @Before
  public void setup() {
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
        android_instrumentation_test("//java/com/foo/app:instrumentation_test")
            .test_app("//java/com/foo/app:test_app")
            .target_device("//tools/android/emulated_devices/generic_phone:android_17_x86"));
    runFullBlazeSyncWithNoIssues();
  }

  @Test
  public void findInstrumentorAndTestTargets() {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(projectData).isNotNull();

    // The following extracts the dependency info required during an instrumentation test.
    // To disambiguate, the following terms are used:
    // test: The android_instrumentation_test target.
    // instrumentor: The android app that instruments the UI test.
    // app: The android app under test.
    Label testLabel = Label.create("//java/com/foo/app:instrumentation_test");
    Label instrumentorLabel = Label.create("//java/com/foo/app:test_app");
    Label appLabel = Label.create("//java/com/foo/app:app");
    Label targetDeviceLabel =
        Label.create("//tools/android/emulated_devices/generic_phone:android_17_x86");

    TargetMap targetMap = projectData.getTargetMap();
    TargetIdeInfo testTarget = targetMap.get(TargetKey.forPlainTarget(testLabel));
    assertThat(testTarget).isNotNull();
    assertThat(testTarget.getKind()).isEqualTo(RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind());
    assertThat(testTarget.getAndroidInstrumentationInfo().getTestApp())
        .isEqualTo(instrumentorLabel);
    assertThat(testTarget.getAndroidInstrumentationInfo().getTargetDevice())
        .isEqualTo(targetDeviceLabel);

    TargetIdeInfo instrumentorTarget = targetMap.get(TargetKey.forPlainTarget(instrumentorLabel));
    assertThat(instrumentorTarget).isNotNull();
    assertThat(instrumentorTarget.getKind()).isEqualTo(RuleTypes.ANDROID_BINARY.getKind());
    assertThat(instrumentorTarget.getAndroidIdeInfo().getInstruments()).isEqualTo(appLabel);

    TargetIdeInfo appTarget = targetMap.get(TargetKey.forPlainTarget(appLabel));
    assertThat(appTarget).isNotNull();
    assertThat(appTarget.getKind()).isEqualTo(RuleTypes.ANDROID_BINARY.getKind());
  }
}
