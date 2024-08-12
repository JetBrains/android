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
package com.google.idea.blaze.android.run.runner;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidInstrumentationInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import javax.annotation.Nullable;

/**
 * Container for information about {@code android_instrumentation_test}: it holds links to the test
 * target and the instrumented target.
 */
@VisibleForTesting
public class InstrumentationInfo {
  /**
   * {@code android_binary} target corresponding to the application under test.
   *
   * <p>This is obtained from the {@code instruments} attribute of the binary that contains the
   * tests.
   */
  @Nullable public final Label targetApp;

  /**
   * {@code android_binary} target that contains the instrumentation tests.
   *
   * <p>This is obtained from the {@code test_app} attribute of an {@code
   * android_instrumentation_test} target.
   */
  public final Label testApp;

  @VisibleForTesting
  public InstrumentationInfo(@Nullable Label targetApp, Label testApp) {
    this.targetApp = targetApp;
    this.testApp = testApp;
  }

  /** Returns whether the test app contains the target itself (self-instrumenting). */
  public boolean isSelfInstrumentingTest() {
    return targetApp == null;
  }

  /** Exception thrown on errors while retrieving {@link InstrumentationInfo} from project data. */
  public static final class InstrumentationParserException extends RuntimeException {
    public InstrumentationParserException(String msg) {
      super(msg);
    }
  }

  /**
   * Extracts information about the test and target apps from the instrumentation test rule.
   *
   * @return The labels contained in an {@link InstrumentationInfo} object.
   */
  @VisibleForTesting
  public static InstrumentationInfo getInstrumentationInfo(
      Label instrumentationTestLabel, BlazeProjectData projectData) {
    if (projectData.isQuerySync()) {
      ProjectTarget testTarget =
          (ProjectTarget) projectData.getBuildTarget(instrumentationTestLabel);
      if (testTarget == null) {
        String msg = "Unable to identify target \"" + instrumentationTestLabel + "\".";
        throw new InstrumentationParserException(msg);
      }
      if (testTarget.testApp().isEmpty()) {
        String msg = "Unable to identify test_app for target \"" + instrumentationTestLabel + "\".";
        throw new InstrumentationParserException(msg);
      }
      Label testApp = Label.create(testTarget.testApp().get().toString());
      ProjectTarget targetApp = (ProjectTarget) projectData.getBuildTarget(testApp);
      Label instruments = null;
      if (targetApp != null && targetApp.instruments().isPresent()) {
        instruments = Label.create(targetApp.instruments().get().toString());
      }
      return new InstrumentationInfo(instruments, testApp);
    }

    // The following extracts the dependency info required during an instrumentation test.
    // To disambiguate, we try to follow the same terminology as used by the
    // android_instrumentation_test rule docs:
    // - test: The android_instrumentation_test target.
    // - test_app: The target of kind android_binary that's used as the binary that
    // orchestrates the instrumentation test.
    // - target_app: The android_binary app that's being tested by the test_app.
    TargetMap targetMap = projectData.getTargetMap();
    TargetIdeInfo testTarget = targetMap.get(TargetKey.forPlainTarget(instrumentationTestLabel));
    if (testTarget == null
        || testTarget.getKind() != RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind()) {
      String msg =
          "Unable to identify target \""
              + instrumentationTestLabel
              + "\". Please sync the project and try again.";
      throw new InstrumentationParserException(msg);
    }
    AndroidInstrumentationInfo testInstrumentationInfo = testTarget.getAndroidInstrumentationInfo();
    if (testInstrumentationInfo == null) {
      String msg =
          "Required target data missing for \""
              + instrumentationTestLabel
              + "\".  Has the target definition changed recently? Please sync the project and"
              + " try again.";
      throw new InstrumentationParserException(msg);
    }

    Label testApp = testInstrumentationInfo.getTestApp();
    if (testApp == null) {
      String msg =
          "No \"test_app\" in target definition for "
              + testTarget.getKey().getLabel()
              + ". Please ensure \"test_app\" attribute is set.  See"
              + " https://docs.bazel.build/versions/master/be/android.html#android_instrumentation_test.test_app"
              + " for more information.";
      throw new InstrumentationParserException(msg);
    }

    TargetIdeInfo testAppIdeInfo = targetMap.get(TargetKey.forPlainTarget(testApp));
    if (testAppIdeInfo == null) {
      String msg =
          "Unable to identify target \"" + testApp + "\". Please sync the project and try again.";
      throw new InstrumentationParserException(msg);
    }
    AndroidIdeInfo testAppAndroidInfo = testAppIdeInfo.getAndroidIdeInfo();
    if (testAppAndroidInfo == null) {
      String msg =
          "Required target data missing for \""
              + testApp
              + "\".  Has the target definition changed recently? Please sync the project and"
              + " try again.";
      throw new InstrumentationParserException(msg);
    }
    Label targetApp = testAppAndroidInfo.getInstruments();
    return new InstrumentationInfo(targetApp, testApp);
  }
}
