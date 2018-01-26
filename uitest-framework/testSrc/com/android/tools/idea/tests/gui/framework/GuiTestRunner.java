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

import com.android.tools.idea.tests.gui.framework.guitestsystem.TargetBuildSystem;
import com.google.common.collect.ImmutableSet;
import org.junit.AssumptionViolatedException;
import org.junit.internal.runners.statements.Fail;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GuiTestRunner extends BlockJUnit4ClassRunner {
  private final TargetBuildSystem.BuildSystem myBuildSystem;
  private TestClass myTestClass;

  public GuiTestRunner(Class<?> testClass) throws InitializationError {
    this(testClass, TargetBuildSystem.BuildSystem.GRADLE);
  }

  public GuiTestRunner(Class<?> aClass, TargetBuildSystem.BuildSystem buildSystem) throws InitializationError {
    super(aClass);
    myBuildSystem = buildSystem;
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    if (GraphicsEnvironment.isHeadless()) {
      // checked first because IdeTestApplication.getInstance below (indirectly) throws an AWTException in a headless environment
      return falseAssumption("headless environment");
    }

    Method methodFromClassLoader;
    try {
      ClassLoader ideClassLoader = IdeTestApplication.getInstance().getIdeClassLoader();
      myTestClass = new TestClass(ideClassLoader.loadClass(getTestClass().getJavaClass().getName()));
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

  /**
   * Called by {@link BlockJUnit4ClassRunner#methodBlock}.
   */
  @Override
  protected Object createTest() throws Exception {
    try {
      IdeTestApplication.getInstance().setTargetBuildSystem(myBuildSystem);
    }
    catch (Exception e) {
      return new Fail(e);
    }

    return myTestClass.getOnlyConstructor().newInstance();
  }

  @Override
  protected List<FrameworkMethod> getChildren() {
    return computeTestMethods()
      .stream()
      .filter(this::isMethodApplicable)
      .collect(Collectors.toList());
  }

  private boolean isMethodApplicable(FrameworkMethod method) {
    TargetBuildSystem annotation = method.getAnnotation(TargetBuildSystem.class);

    // if there are no annotations on the method, we can run it only with the default build system
    if (annotation == null) {
      return myBuildSystem.isDefault();
    }

    // if the method is annotated, then one of the annotations must include the current build system
    return ImmutableSet.copyOf(annotation.value()).contains(myBuildSystem);
  }
}
