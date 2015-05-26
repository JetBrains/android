/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.collect.Maps;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import org.fest.reflect.reference.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Map;

import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.assertNotNull;

/**
 * Collects configuration information from a UI test method's {@link IdeGuiTest} and {@link IdeGuiTestSetup} annotations and applies it
 * to the test before execution, using the the IDE's {@code ClassLoader} (which is the {@code ClassLoader} used by UI tests, to be able to
 * access IDE's services, state and components.)
 */
class GuiTestConfigurator {
  private static final String CLOSE_PROJECT_BEFORE_EXECUTION = "closeProjectBeforeExecution";
  private static final String RUN_WITH_MINIMUM_JDK_VERSION = "runWithMinimumJdkVersion";
  private static final String SKIP_SOURCE_GENERATION_ON_SYNC = "skipSourceGenerationOnSync";
  private static final String TAKE_SCREENSHOT_ON_TEST_FAILURE = "takeScreenshotOnTestFailure";

  @NotNull private final Map<String, Object> myConfiguration;
  @NotNull private final String myTestName;
  @NotNull private final Object myTest;
  @NotNull private final ClassLoader myClassLoader;

  static GuiTestConfigurator createNew(@NotNull Method testMethod, @NotNull Object test) throws Throwable {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> target = classLoader.loadClass(GuiTestConfigurator.class.getCanonicalName());
    Map<String, Object> testConfig = method("extractTestConfiguration").withReturnType(new TypeRef<Map<String, Object>>() {})
                                                                       .withParameterTypes(Method.class)
                                                                       .in(target)
                                                                       .invoke(testMethod);
    assertNotNull(testConfig);
    return new GuiTestConfigurator(testConfig, testMethod.getName(), test, classLoader);
  }

  // Invoked using reflection and the IDE's ClassLoader.
  @NotNull
  private static Map<String, Object> extractTestConfiguration(@NotNull Method testMethod) {
    Map<String, Object> config = Maps.newHashMap();
    IdeGuiTest guiTest = testMethod.getAnnotation(IdeGuiTest.class);
    if (guiTest != null) {
      config.put(CLOSE_PROJECT_BEFORE_EXECUTION, guiTest.closeProjectBeforeExecution());
      config.put(RUN_WITH_MINIMUM_JDK_VERSION, guiTest.runWithMinimumJdkVersion());
    }
    IdeGuiTestSetup guiTestSetup = testMethod.getDeclaringClass().getAnnotation(IdeGuiTestSetup.class);
    if (guiTestSetup != null) {
      config.put(SKIP_SOURCE_GENERATION_ON_SYNC, guiTestSetup.skipSourceGenerationOnSync());
      config.put(TAKE_SCREENSHOT_ON_TEST_FAILURE, guiTestSetup.takeScreenshotOnTestFailure());
    }
    return config;
  }

  private GuiTestConfigurator(@NotNull Map<String, Object> configuration,
                              @NotNull String testName,
                              @NotNull Object test,
                              @NotNull ClassLoader classLoader) {
    myConfiguration = configuration;
    myTestName = testName;
    myTest = test;
    myClassLoader = classLoader;
  }

  void executeSetupTasks() throws Throwable {
    closeAllProjects();
    skipSourceGenerationOnSync();
  }

  private void closeAllProjects() {
    Object value = myConfiguration.get(CLOSE_PROJECT_BEFORE_EXECUTION);
    if (value instanceof Boolean && ((Boolean)value)) {
      method("closeAllProjects").in(myTest).invoke();
    }
  }

  private void skipSourceGenerationOnSync() throws Throwable {
    Object value = myConfiguration.get(SKIP_SOURCE_GENERATION_ON_SYNC);
    if (value instanceof Boolean && ((Boolean)value)) {
      Class<?> target = loadMyClassWithTestClassLoader();
      method("doSkipSourceGenerationOnSync").in(target).invoke();
    }
  }

  // Invoked using reflection and the IDE's ClassLoader.
  private static void doSkipSourceGenerationOnSync() {
    System.out.println("Skipping source generation on project sync.");
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  boolean shouldSkipTest() throws Throwable {
    String javaSdkVersionClassName = JavaSdkVersion.class.getCanonicalName();

    Object minimumJdkVersion = myConfiguration.get(RUN_WITH_MINIMUM_JDK_VERSION);
    if (minimumJdkVersion != null && minimumJdkVersion.getClass().getCanonicalName().equals(javaSdkVersionClassName)) {
      Class<?> target = loadMyClassWithTestClassLoader();
      Boolean hasRequiredJdk = method("hasRequiredJdk").withReturnType(boolean.class)
                                                       .withParameterTypes(myClassLoader.loadClass(javaSdkVersionClassName))
                                                       .in(target)
                                                       .invoke(minimumJdkVersion);
      assertNotNull(hasRequiredJdk);
      if (!hasRequiredJdk) {
        String jdkVersion = method("getDescription").withReturnType(String.class).in(minimumJdkVersion).invoke();
        System.out.println(String.format("Skipping test '%1$s'. It needs JDK %2$s or newer.", myTestName, jdkVersion));
        return true;
      }
    }

    return false;
  }

  // Invoked using reflection and the IDE's ClassLoader.
  private static boolean hasRequiredJdk(@NotNull JavaSdkVersion jdkVersion) {
    Sdk jdk = IdeSdks.getJdk();
    assertNotNull("Expecting to have a JDK", jdk);
    JavaSdkVersion currentVersion = JavaSdk.getInstance().getVersion(jdk);
    return currentVersion != null && currentVersion.isAtLeast(jdkVersion);
  }

  boolean shouldTakeScreenshotOnFailure() {
    Object value = myConfiguration.get(TAKE_SCREENSHOT_ON_TEST_FAILURE);
    if (value instanceof Boolean) {
      return ((Boolean)value);
    }
    return true;
  }

  @NotNull
  private Class<?> loadMyClassWithTestClassLoader() throws ClassNotFoundException {
    return myClassLoader.loadClass(GuiTestConfigurator.class.getCanonicalName());
  }
}
