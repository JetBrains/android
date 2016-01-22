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

import com.google.common.collect.Maps;
import org.fest.reflect.reference.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Map;

import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.assertNotNull;

/**
 * Collects configuration information from a UI test method's {@link IdeGuiTestSetup} annotations and applies it
 * to the test before execution, using the the IDE's {@code ClassLoader} (which is the {@code ClassLoader} used by UI tests, to be able to
 * access IDE's services, state and components.)
 */
class GuiTestConfigurator {
  private static final String TAKE_SCREENSHOT_ON_TEST_FAILURE_KEY = "takeScreenshotOnTestFailure";

  @NotNull private final Object myTest;

  private final boolean myTakeScreenshotOnTestFailure;

  @NotNull
  static GuiTestConfigurator createNew(@NotNull Method testMethod, @NotNull Object test) throws Throwable {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> target = classLoader.loadClass(GuiTestConfigurator.class.getCanonicalName());
    Map<String, Object> testConfig = method("extractTestConfiguration").withReturnType(new TypeRef<Map<String, Object>>() {})
                                                                       .withParameterTypes(Method.class)
                                                                       .in(target)
                                                                       .invoke(testMethod);
    assertNotNull(testConfig);
    return new GuiTestConfigurator(testConfig, test);
  }

  // Invoked using reflection and the IDE's ClassLoader.
  @NotNull
  private static Map<String, Object> extractTestConfiguration(@NotNull Method testMethod) {
    Map<String, Object> config = Maps.newHashMap();
    IdeGuiTestSetup guiTestSetup = testMethod.getDeclaringClass().getAnnotation(IdeGuiTestSetup.class);
    if (guiTestSetup != null) {
      config.put(TAKE_SCREENSHOT_ON_TEST_FAILURE_KEY, guiTestSetup.takeScreenshotOnTestFailure());
    }
    return config;
  }

  private GuiTestConfigurator(@NotNull Map<String, Object> configuration,
                              @NotNull Object test) {
    myTakeScreenshotOnTestFailure = getBooleanValue(TAKE_SCREENSHOT_ON_TEST_FAILURE_KEY, configuration, true);

    myTest = test;
  }

  private static boolean getBooleanValue(@NotNull String key, @NotNull Map<String, Object> configuration, boolean defaultValue) {
    Object value = configuration.get(key);
    if (value instanceof Boolean) {
      return ((Boolean)value);
    }
    return defaultValue;
  }

  void executeSetupTasks() throws Throwable {
    closeAllProjects();
  }

  private void closeAllProjects() {
    method("closeAllProjects").in(myTest).invoke();
  }

  boolean shouldTakeScreenshotOnFailure() {
    return myTakeScreenshotOnTestFailure;
  }
}
