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
import org.fest.swing.image.ScreenshotTaker;
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

import java.awt.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import static com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.assertNotNull;

public class GuiTestRunner extends BlockJUnit4ClassRunner {
  private Class<? extends Annotation> myBeforeClass;
  private Class<? extends Annotation> myAfterClass;

  private TestClass myTestClass;

  private final ScreenshotTaker myScreenshotTaker = new ScreenshotTaker();

  public GuiTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);

    try {
      // A random class which is reachable from module community-main's classpath but not
      // module android's classpath
      Class.forName("git4idea.repo.GitConfig");
    }
    catch (ClassNotFoundException e) {
      throw new InitializationError("Invalid test run configuration. Edit your test configuration and make sure that " +
                                    "\"Use classpath of module\" is set to \"community-main\", *NOT* \"android\"!");
    }
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

  @SuppressWarnings("unchecked")
  private void loadClassesWithIdeClassLoader() throws Exception {
    ClassLoader ideClassLoader = IdeTestApplication.getInstance().getIdeClassLoader();
    Thread.currentThread().setContextClassLoader(ideClassLoader);

    Class<?> testClass = getTestClass().getJavaClass();
    myTestClass = new TestClass(ideClassLoader.loadClass(testClass.getName()));
    myBeforeClass = (Class<? extends Annotation>)ideClassLoader.loadClass(Before.class.getName());
    myAfterClass = (Class<? extends Annotation>)ideClassLoader.loadClass(After.class.getName());
  }

  @Override
  protected Object createTest() throws Exception {
    return myTestClass != null ? myTestClass.getJavaClass().newInstance() : super.createTest();
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    boolean takeScreenshot = true;

    if (canRunGuiTests()) {
      try {
        ClassLoader ideClassLoader = IdeTestApplication.getInstance().getIdeClassLoader();
        Class<? extends Annotation> ideGuiTestSetupClass = loadAnnotationClass(ideClassLoader, IdeGuiTestSetup.class);
        Class<?> type = method.getMethod().getDeclaringClass();
        Annotation ideGuiTestSetup = type.getAnnotation(ideGuiTestSetupClass);
        if (ideGuiTestSetup != null) {
          boolean skipSourceGeneration = invokeBooleanMethod("skipSourceGenerationOnSync", ideGuiTestSetup, ideGuiTestSetupClass);
          if (skipSourceGeneration) {
            Class<?> guiTestsClass = ideClassLoader.loadClass(GuiTests.class.getCanonicalName());
            method("skipSourceGenerationOnSync").in(guiTestsClass).invoke();
          }

          takeScreenshot = invokeBooleanMethod("takeScreenshotOnTestFailure", ideGuiTestSetup, ideGuiTestSetupClass);
        }

        Class<? extends Annotation> ideGuiTestClass = loadAnnotationClass(ideClassLoader, IdeGuiTest.class);
        Annotation ideGuiTest = method.getMethod().getAnnotation(ideGuiTestClass);
        if (ideGuiTest != null) {
          boolean closeProject = invokeBooleanMethod("closeProjectBeforeExecution", ideGuiTest, ideGuiTestClass);
          if (closeProject) {
            method("closeAllProjects").in(test).invoke();
          }
        }
      }
      catch (Throwable e) {
        return new Fail(e);
      }
    }
    return new MethodInvoker(method, test, takeScreenshot ? myScreenshotTaker : null);
  }

  @NotNull
  private static Class<? extends Annotation> loadAnnotationClass(@NotNull ClassLoader ideClassLoader,
                                                                 @NotNull Class<? extends Annotation> type) throws ClassNotFoundException {
    //noinspection unchecked
    return (Class<? extends Annotation>)ideClassLoader.loadClass(type.getCanonicalName());
  }

  private static boolean invokeBooleanMethod(@NotNull String methodName,
                                             @NotNull Annotation target,
                                             @NotNull Class<? extends Annotation> annotationType) throws Throwable {
    if (Proxy.isProxyClass(target.getClass())) {
      InvocationHandler invocationHandler = Proxy.getInvocationHandler(target);
      Method method = annotationType.getDeclaredMethod(methodName);
      Object result = invocationHandler.invoke(annotationType, method, EMPTY_OBJECT_ARRAY);
      assertNotNull(result);
      assertThat(result).isInstanceOfAny(boolean.class, Boolean.class);
      return (Boolean)result;
    }
    throw new IllegalArgumentException("'target' is not a proxy for an annotation");
  }
}
