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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.lang.UrlClassLoader;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import javax.swing.*;
import java.awt.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.fest.swing.finder.WindowFinder.findFrame;

public class GuiTestRunner extends BlockJUnit4ClassRunner {
  private Class<? extends Annotation> myBeforeClass;
  private Class<? extends Annotation> myAfterClass;

  private TestClass myTestClass;

  public GuiTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    if (!canRunGuiTests()) {
      Class<?> testClass = getTestClass().getJavaClass();
      Logger logger = Logger.getInstance(testClass);
      logger.info("Skipping GUI test " + testClass.getCanonicalName() + " due to headless environment");
      return super.methodBlock(method);
    }

    FrameworkMethod newMethod;
    try {
      loadClassesWithIdeClassLoader();
      Method methodFromClassLoader = myTestClass.getJavaClass().getMethod(method.getName());
      newMethod = new FrameworkMethod(methodFromClassLoader);
    }
    catch (Exception e) {
      return new Fail(e);
    }
    Object test;
    try {
      test = new ReflectiveCallable() {
        @Override
        protected Object runReflectiveCall() throws Throwable {
          return createTest();
        }
      }.run();
    }
    catch (Throwable e) {
      return new Fail(e);
    }

    Statement statement = methodInvoker(newMethod, test);

    List<FrameworkMethod> beforeMethods = myTestClass.getAnnotatedMethods(myBeforeClass);
    if (!beforeMethods.isEmpty()) {
      statement = new RunBefores(statement, beforeMethods, test);
    }

    List<FrameworkMethod> afterMethods = myTestClass.getAnnotatedMethods(myAfterClass);
    if (!afterMethods.isEmpty()) {
      statement = new RunAfters(statement, afterMethods, test);
    }

    return statement;
  }

  public static boolean canRunGuiTests() {
    return !GraphicsEnvironment.isHeadless();
  }

  private void loadClassesWithIdeClassLoader() throws Exception {
    Robot robot = null;
    try {
      robot = BasicRobot.robotWithNewAwtHierarchy();

      IdeTestApplication testApplication = IdeTestApplication.getInstance();
      // wait till IDE is up
      findFrame(JFrame.class).withTimeout(5, TimeUnit.MINUTES).using(robot);

      UrlClassLoader ideClassLoader = testApplication.getIdeClassLoader();
      Thread.currentThread().setContextClassLoader(ideClassLoader);

      myBeforeClass = loadAnnotationClass(Before.class, ideClassLoader);
      myAfterClass = loadAnnotationClass(After.class, ideClassLoader);

      myTestClass = new TestClass(ideClassLoader.loadClass(getTestClass().getJavaClass().getName()));
    }
    finally {
      if (robot != null) {
        robot.cleanUpWithoutDisposingWindows();
      }
    }
  }

  @NotNull
  private static Class<? extends Annotation> loadAnnotationClass(@NotNull Class<? extends Annotation> annotationClass,
                                                                 @NotNull ClassLoader classLoader) throws ClassNotFoundException {
    //noinspection unchecked
    return (Class<? extends Annotation>)classLoader.loadClass(annotationClass.getName());
  }

  @Override
  protected Object createTest() throws Exception {
    return myTestClass != null ? myTestClass.getJavaClass().newInstance() : super.createTest();
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    return new MethodInvoker(method, test);
  }
}
