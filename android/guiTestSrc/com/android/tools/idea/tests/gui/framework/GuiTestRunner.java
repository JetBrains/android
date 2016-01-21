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

import com.google.common.base.Strings;
import org.fest.swing.image.ScreenshotTaker;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.internal.runners.statements.Fail;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.awt.*;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;

public class GuiTestRunner extends BlockJUnit4ClassRunner {

  private TestClass myTestClass;

  @Nullable private final ScreenshotTaker myScreenshotTaker;

  public GuiTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);

    myScreenshotTaker = !GraphicsEnvironment.isHeadless() ? new ScreenshotTaker() : null;

    // UI_TEST_MODE is set whenever we run UI tests on top of a Studio build. In that case, we
    // assume the classpath has been properly configured. Otherwise, if we're running from the
    // IDE or an Ant build, we need to check we have access to the classpath of community-main.
    if (Strings.isNullOrEmpty(System.getenv("UI_TEST_MODE"))) {
      try {
        // A random class which is reachable from module community-main's classpath but not
        // module android's classpath.
        Class.forName("git4idea.repo.GitConfig", false, testClass.getClassLoader());
      }
      catch (ClassNotFoundException e) {
        throw new InitializationError("Invalid test run configuration. Edit your test configuration and make sure that " +
                                      "\"Use classpath of module\" is set to \"community-main\", *NOT* \"android\"!");
      }
    }
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    if (GraphicsEnvironment.isHeadless()) {
      // checked first because loadClassesWithIdeClassLoader below (indirectly) throws an AWTException in a headless environment
      return falseAssumption("headless environment");
    }
    Method methodFromClassLoader;
    try {
      loadClassesWithIdeClassLoader();
      methodFromClassLoader = myTestClass.getJavaClass().getMethod(method.getName());
    }
    catch (Exception e) {
      return new Fail(e);
    }
    return super.methodBlock(new FrameworkMethod(methodFromClassLoader));
  }

  private static Statement falseAssumption(final String message) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        throw new AssumptionViolatedException(message);
      }
    };
  }

  private void loadClassesWithIdeClassLoader() throws Exception {
    ClassLoader ideClassLoader = IdeTestApplication.getInstance().getIdeClassLoader();
    Thread.currentThread().setContextClassLoader(ideClassLoader);

    Class<?> testClass = getTestClass().getJavaClass();
    myTestClass = new TestClass(ideClassLoader.loadClass(testClass.getName()));
  }

  @Override
  protected Object createTest() throws Exception {
    return myTestClass != null ? myTestClass.getJavaClass().newInstance() : super.createTest();
  }

  @Override
  protected Statement methodInvoker(final FrameworkMethod method, Object test) {
    try {
      assertNotNull(myScreenshotTaker);
      return new MethodInvoker(method, test, myScreenshotTaker);
    }
    catch (Throwable e) {
      return new Fail(e);
    }
  }
}
