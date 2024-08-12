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
import static org.junit.Assert.fail;

import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo.InstrumentationParserException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link InstrumentationInfo} */
@RunWith(JUnit4.class)
public class InstrumentationInfoTest extends BlazeAndroidIntegrationTestCase {
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

  @Test
  public void separateInstrumentationAndTargetApp() {
    setupProject();

    Label instrumentationTestLabel = Label.create("//java/com/foo/app:instrumentation_test");
    InstrumentationInfo info =
        InstrumentationInfo.getInstrumentationInfo(
            instrumentationTestLabel,
            BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData());

    assertThat(info.testApp).isEqualTo(Label.create("//java/com/foo/app:test_app"));
    assertThat(info.targetApp).isEqualTo(Label.create("//java/com/foo/app:app"));
    assertThat(info.isSelfInstrumentingTest()).isFalse();
  }

  @Test
  public void selfInstrumentingTest() {
    setupProject();

    Label instrumentationTestLabel = Label.create("//java/com/foo/app:self_instrumenting_test");
    InstrumentationInfo info =
        InstrumentationInfo.getInstrumentationInfo(
            instrumentationTestLabel,
            BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData());

    assertThat(info.testApp)
        .isEqualTo(Label.create("//java/com/foo/app:test_app_self_instrumenting"));
    assertThat(info.targetApp).isNull();
    assertThat(info.isSelfInstrumentingTest()).isTrue();
  }

  @Test
  public void noTestAppSpecified() {
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
        android_instrumentation_test("//java/com/foo/app:instrumentation_test"));
    runFullBlazeSyncWithNoIssues();

    Label instrumentationTestLabel = Label.create("//java/com/foo/app:instrumentation_test");
    try {
      InstrumentationInfo.getInstrumentationInfo(
          instrumentationTestLabel,
          BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData());
      fail("parsing should've thrown an exception");
    } catch (InstrumentationParserException e) {
      assertThat(e.getMessage())
          .startsWith(
              "No \"test_app\" in target definition for //java/com/foo/app:instrumentation_test.");
    }
  }

  @Test
  public void findTestAndAppTargets() {
    setupProject();
    Label instrumentationTestLabel = Label.create("//java/com/foo/app:instrumentation_test");
    InstrumentationInfo info =
        InstrumentationInfo.getInstrumentationInfo(
            instrumentationTestLabel,
            BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData());
    assertThat(info.testApp).isEqualTo(Label.create("//java/com/foo/app:test_app"));
    assertThat(info.targetApp).isEqualTo(Label.create("//java/com/foo/app:app"));
  }
}
