/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.android.tools.idea.run.ValidationError;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.util.Map;
import javax.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;

/** State specific for the android test configuration. */
public final class BlazeAndroidTestRunConfigurationState implements RunConfigurationState {

  private static final String LAUNCH_METHOD_ATTR = "launch-method";
  @Deprecated private static final String RUN_THROUGH_BLAZE_ATTR = "blaze-run-through-blaze";

  public static final int TEST_ALL_IN_MODULE = 0;
  public static final int TEST_ALL_IN_PACKAGE = 1;
  public static final int TEST_CLASS = 2;
  public static final int TEST_METHOD = 3;

  // We reinterpret Android Studio's test mode for running "all tests in a module"
  // (all the tests in the installed test APK) as running all
  // the tests in a rule.
  public static final int TEST_ALL_IN_TARGET = TEST_ALL_IN_MODULE;

  private static final String TESTING_TYPE = "TESTING_TYPE";
  private static final String INSTRUMENTATION_RUNNER_CLASS = "INSTRUMENTATION_RUNNER_CLASS";
  private static final String METHOD_NAME = "METHOD_NAME";
  private static final String CLASS_NAME = "CLASS_NAME";
  private static final String PACKAGE_NAME = "PACKAGE_NAME";
  private static final String EXTRA_OPTIONS = "EXTRA_OPTIONS";

  private int testingType = TEST_ALL_IN_MODULE;
  private String instrumentationRunnerClass = "";
  private String methodName = "";
  private String className = "";
  private String packageName = "";
  private String extraOptions = "";

  private AndroidTestLaunchMethod launchMethod = AndroidTestLaunchMethod.NON_BLAZE;

  private final BlazeAndroidRunConfigurationCommonState commonState;

  public BlazeAndroidTestRunConfigurationState(String buildSystemName) {
    commonState = new BlazeAndroidRunConfigurationCommonState(buildSystemName);
  }

  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return commonState;
  }

  @Contract(pure = true)
  AndroidTestLaunchMethod getLaunchMethod() {
    return launchMethod;
  }

  @VisibleForTesting
  public void setLaunchMethod(AndroidTestLaunchMethod launchMethod) {
    this.launchMethod = launchMethod;
  }

  public int getTestingType() {
    return testingType;
  }

  public void setTestingType(int testingType) {
    this.testingType = testingType;
  }

  public String getInstrumentationRunnerClass() {
    return instrumentationRunnerClass;
  }

  public void setInstrumentationRunnerClass(@Nullable String instrumentationRunnerClass) {
    this.instrumentationRunnerClass = Strings.nullToEmpty(instrumentationRunnerClass);
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(@Nullable String methodName) {
    this.methodName = Strings.nullToEmpty(methodName);
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(@Nullable String className) {
    this.className = Strings.nullToEmpty(className);
  }

  public String getPackageName() {
    return packageName;
  }

  public void setPackageName(@Nullable String packageName) {
    this.packageName = Strings.nullToEmpty(packageName);
  }

  public String getExtraOptions() {
    return extraOptions;
  }

  public void setExtraOptions(@Nullable String extraOptions) {
    this.extraOptions = Strings.nullToEmpty(extraOptions);
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning.
   */
  public ImmutableList<ValidationError> validate(Project project) {
    ImmutableList.Builder<ValidationError> errors = ImmutableList.builder();
    errors.addAll(commonState.validate(project));
    if (commonState.isNativeDebuggingEnabled()
        && !launchMethod.equals(AndroidTestLaunchMethod.NON_BLAZE)) {
      errors.add(
          ValidationError.fatal(
              "Native debugging is not support when running with blaze test or mobile-install."));
    }
    return errors.build();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    commonState.readExternal(element);

    String testingTypeAttribute = element.getAttributeValue(TESTING_TYPE);
    if (!Strings.isNullOrEmpty(testingTypeAttribute)) {
      testingType = Integer.parseInt(testingTypeAttribute);
    }
    instrumentationRunnerClass =
        Strings.nullToEmpty(element.getAttributeValue(INSTRUMENTATION_RUNNER_CLASS));
    methodName = Strings.nullToEmpty(element.getAttributeValue(METHOD_NAME));
    className = Strings.nullToEmpty(element.getAttributeValue(CLASS_NAME));
    packageName = Strings.nullToEmpty(element.getAttributeValue(PACKAGE_NAME));
    extraOptions = Strings.nullToEmpty(element.getAttributeValue(EXTRA_OPTIONS));

    String launchMethodAttribute = element.getAttributeValue(LAUNCH_METHOD_ATTR);
    if (launchMethodAttribute != null) {
      launchMethod = AndroidTestLaunchMethod.valueOf(launchMethodAttribute);
    } else {
      if (Boolean.parseBoolean(element.getAttributeValue(RUN_THROUGH_BLAZE_ATTR))) {
        launchMethod = AndroidTestLaunchMethod.BLAZE_TEST;
      } else {
        launchMethod = AndroidTestLaunchMethod.NON_BLAZE;
      }
    }

    for (Map.Entry<String, String> entry : getLegacyValues(element).entrySet()) {
      String value = entry.getValue();
      switch (entry.getKey()) {
        case TESTING_TYPE:
          if (!Strings.isNullOrEmpty(value)) {
            testingType = Integer.parseInt(value);
          }
          break;
        case INSTRUMENTATION_RUNNER_CLASS:
          instrumentationRunnerClass = Strings.nullToEmpty(value);
          break;
        case METHOD_NAME:
          methodName = Strings.nullToEmpty(value);
          break;
        case CLASS_NAME:
          className = Strings.nullToEmpty(value);
          break;
        case PACKAGE_NAME:
          packageName = Strings.nullToEmpty(value);
          break;
        case EXTRA_OPTIONS:
          extraOptions = Strings.nullToEmpty(value);
          break;
        default:
          break;
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    commonState.writeExternal(element);

    element.setAttribute(LAUNCH_METHOD_ATTR, launchMethod.name());
    element.setAttribute(TESTING_TYPE, Integer.toString(testingType));
    element.setAttribute(INSTRUMENTATION_RUNNER_CLASS, instrumentationRunnerClass);
    element.setAttribute(METHOD_NAME, methodName);
    element.setAttribute(CLASS_NAME, className);
    element.setAttribute(PACKAGE_NAME, packageName);
    element.setAttribute(EXTRA_OPTIONS, extraOptions);
  }

  /** Imports legacy values in the old reflective JDOM externalizer manner. Can be removed ~2.0+. */
  private static Map<String, String> getLegacyValues(Element element) {
    Map<String, String> result = Maps.newHashMap();
    for (Element option : element.getChildren("option")) {
      String name = option.getAttributeValue("name");
      String value = option.getAttributeValue("value");
      result.put(name, value);
    }
    return result;
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeAndroidTestRunConfigurationStateEditor(commonState.getEditor(project), project);
  }
}
