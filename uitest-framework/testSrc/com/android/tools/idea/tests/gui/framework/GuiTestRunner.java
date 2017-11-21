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

import com.android.tools.idea.tests.gui.framework.guitestsystem.GuiTestSystem;
import com.android.tools.idea.tests.gui.framework.guitestsystem.RunWithBuildSystem;
import com.google.common.collect.ImmutableList;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.*;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class GuiTestRunner extends Suite {
  private final List<Runner> perBuildSystemRunners;

  public GuiTestRunner(Class<?> klass, RunnerBuilder builder) throws InitializationError {
    super(klass, Collections.emptyList());

    try {
      // Wait until the IDE is loaded before making runners.
      IdeTestApplication.getInstance();
    } catch(Exception e) {
      throw new InitializationError(e);
    }

    // obtain a list of available test systems and the build systems they correspond to.
    EnumMap<RunWithBuildSystem.BuildSystem, GuiTestSystem> availableTestSystems = new EnumMap<>(RunWithBuildSystem.BuildSystem.class);
    for (GuiTestSystem sys : GuiTestSystem.Companion.getEP_NAME().getExtensions()) {
      availableTestSystems.put(sys.getBuildSystem(), sys);
    }

    if (availableTestSystems.isEmpty()) {
      throw new MissingResourceException("Cannot find GUI test systems to run tests. Please register at least one " +
                                         "GUI test system for " + getDefaultBuildSystems().toString(),
                                         GuiTestSystem.class.getName(),
                                         getDefaultBuildSystems().toString());
    }

    // needed systems = systems used by methods of this test + defaults if there exist tests without RunWithBuildSystem annotation.
    Set<RunWithBuildSystem.BuildSystem> neededBuildSystems = new TreeSet<>();
    for (Method method : klass.getMethods()) {
      RunWithBuildSystem annotation = method.getAnnotation(RunWithBuildSystem.class);
      if (annotation != null) neededBuildSystems.addAll(Arrays.asList(annotation.value()));
    }

    // If number of @Test methods is greater than number of @RunWithBuildSystem methods, then there are unannotated tests.
    if (getTestClass().getAnnotatedMethods(Test.class).size() > getTestClass().getAnnotatedMethods(RunWithBuildSystem.class).size()) {
      System.out.println("Detected tests without specific annotation.  Adding defaults.");
      neededBuildSystems.addAll(getDefaultBuildSystems());
    }

    // If filter flag is set then filter the list of build systems
    String buildSystemFilter = System.getProperty("test.buildsystem.filter");
    if (buildSystemFilter != null) {
      neededBuildSystems = neededBuildSystems.stream().filter(sys -> sys.name().equalsIgnoreCase(buildSystemFilter)).collect(Collectors.toSet());
      if (neededBuildSystems.isEmpty()) {
        System.out.println("No tests for filter " + buildSystemFilter + ", skipping tests");
      }
    }

    perBuildSystemRunners = new ArrayList<>();
    for (RunWithBuildSystem.BuildSystem buildSystem : neededBuildSystems) {
      if (availableTestSystems.containsKey(buildSystem)) {
        perBuildSystemRunners.add(new BuildSystemSpecificRunner(klass, buildSystem, availableTestSystems.get(buildSystem)));
      } else {
        System.out.println("Tests for " + buildSystem + " based projects exist but no matching test system is available." +
                           " Tests with " + buildSystem + " based projects will be skipped.\n");
      }
    }
  }

  private static ImmutableList<RunWithBuildSystem.BuildSystem> getDefaultBuildSystems() {
    return ImmutableList.of(RunWithBuildSystem.BuildSystem.GRADLE);
  }

  @Override
  protected List<Runner> getChildren() {
    return perBuildSystemRunners;
  }

  private static class BuildSystemSpecificRunner extends BlockJUnit4ClassRunner {
    private final RunWithBuildSystem.BuildSystem myBuildSystem;
    private final GuiTestSystem myTestSystem;
    private TestClass myTestClass;

    public BuildSystemSpecificRunner(Class<?> testClass, RunWithBuildSystem.BuildSystem buildSystem, GuiTestSystem testSystem) throws InitializationError {
      super(testClass);
      myBuildSystem = buildSystem;
      myTestSystem = testSystem;
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
        Thread.currentThread().setContextClassLoader(ideClassLoader);
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

    private boolean isMethodApplicable(FrameworkMethod method) {
      RunWithBuildSystem annotation = method.getAnnotation(RunWithBuildSystem.class);
      if (annotation == null || annotation.value().length == 0) return getDefaultBuildSystems().contains(myBuildSystem);
      return Arrays.asList(annotation.value()).contains(myBuildSystem);
    }

    @Override
    protected List<FrameworkMethod> getChildren() {
      List<FrameworkMethod> methods = new ArrayList<>();
      for (FrameworkMethod m : computeTestMethods()) {
        if (isMethodApplicable(m)) {
          methods.add(m);
        }
      }
      return methods;
    }

    /** Called by {@link BlockJUnit4ClassRunner#methodBlock}. */
    @Override
    protected Object createTest() throws Exception {
      System.setProperty("guitest.currentguitestsystem", myTestSystem.getId());
      return myTestClass.getOnlyConstructor().newInstance();
    }

    @Override
    protected String getName() {
      return "[Running with " + myBuildSystem.name().toLowerCase() + " based project]";
    }
  }
}
