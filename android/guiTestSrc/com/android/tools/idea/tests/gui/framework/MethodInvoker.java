/*
 * Copyright (C) 2014 The Android Open Source Project
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

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import static com.android.tools.idea.tests.gui.framework.GuiTestRunner.canRunGuiTests;
import static org.fest.reflect.core.Reflection.field;

public class MethodInvoker extends Statement {
  private final FrameworkMethod myTestMethod;
  private final Object myTest;

  MethodInvoker(FrameworkMethod testMethod, Object test) {
    myTestMethod = testMethod;
    myTest = test;
  }

  @Override
  public void evaluate() throws Throwable {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> guiTestCaseType = Class.forName(GuiTestCase.class.getCanonicalName(), true, classLoader);
    String name = myTestMethod.getName();
    System.out.println(String.format("Executing test '%1$s'", myTestMethod.getMethod().getDeclaringClass() + "#" + name));
    if (guiTestCaseType.isInstance(myTest)) {
      if (!canRunGuiTests()) {
        // We don't run tests in headless environment.
        return;
      }
      field("myTestName").ofType(String.class).in(myTest).set(name);
    }
    myTestMethod.invokeExplosively(myTest);
  }
}
