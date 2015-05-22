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

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;

import static com.android.tools.idea.tests.gui.framework.GuiTestRunner.canRunGuiTests;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.reflect.core.Reflection.method;

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

    Annotation ideGuiTestAnnotation = null;
    for (Annotation annotation : myTestMethod.getAnnotations()) {
      if (annotation.annotationType().getCanonicalName().equals(IdeGuiTest.class.getCanonicalName())) {
        ideGuiTestAnnotation = annotation;
        break;
      }
    }

    if (ideGuiTestAnnotation != null) {
      Object minimumJdkVersion = method("runWithMinimumJdkVersion").withReturnType(Object.class)
                                                                   .in(ideGuiTestAnnotation)
                                                                   .invoke();
      assert minimumJdkVersion != null;

      Class<?> guiTestsClass = classLoader.loadClass(GuiTests.class.getCanonicalName());
      Class<?> javaSdkVersionClass = classLoader.loadClass(JavaSdkVersion.class.getCanonicalName());
      Boolean hasRequiredJdk = method("hasRequiredJdk").withReturnType(boolean.class)
                                                       .withParameterTypes(javaSdkVersionClass)
                                                       .in(guiTestsClass)
                                                       .invoke(minimumJdkVersion);
      assert hasRequiredJdk != null;
      if (!hasRequiredJdk) {
        String jdkVersion = method("getDescription").withReturnType(String.class).in(minimumJdkVersion).invoke();
        System.out.println(String.format("Skipping test '%1$s'. It needs JDK %2$s or newer.", name, jdkVersion));
        return;
      }
    }

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
