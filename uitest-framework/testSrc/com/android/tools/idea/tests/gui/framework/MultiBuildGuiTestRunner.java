/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem;
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem.BuildSystem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;
import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem.*;

/**
 * {@link MultiBuildGuiTestRunner} allows ui-tests to run with multiple {@link TargetBuildSystem}'s.
 * Tests using this runner can specify one or more build systems to target via the {@link BuildSystem} annotation.
 * Each test is then run once per indicated build system by {@link BuildSpecificGuiTestRunner}.
 * <p>
 * Example of a class containing tests to be run with multiple build systems:
 * <pre>
 * &#064;RunWith(MultiBuildGuiTestRunner.class)
 * public class MultiBuildSystemTest {
 *     &#064;Test
 *     &#064;TargetBuildSystem({TargetBuildSystem.BuildSystem.GRADLE, TargetBuildSystem.BuildSystem.BAZEL})
 *     public testWithBoth() {
 *         ...
 *     }
 *
 *     &#064;Test
 *     &#064;TargetBuildSystem({TargetBuildSystem.BuildSystem.BAZEL})
 *     public testWithJustBazel() {
 *         ...
 *     }
 *
 *     &#064;Test
 *     public testWithDefaultBuildSystem() {
 *         ...
 *     }
 * }
 * </pre>
 * <p>
 * If a test is not annotated with any build system, then {@link BuildSystem#getDefault()} will be used.
 */
public class MultiBuildGuiTestRunner extends Suite {
  @NotNull private final List<Runner> myRunners;

  public MultiBuildGuiTestRunner(Class<?> klass, RunnerBuilder builder) throws InitializationError {
    super(klass, Collections.emptyList());
    myRunners = runnersFor(klass, requiredBuildSystems(klass));
  }

  @NotNull
  private static Set<BuildSystem> requiredBuildSystems(@NotNull Class<?> klass) {
    return Arrays.stream(klass.getMethods())
      .filter(method -> method.getAnnotation(Test.class) != null)
      .map(method -> method.getAnnotation(TargetBuildSystem.class))
      .flatMap(targetBuildSystem -> {
        if (targetBuildSystem == null) {
          return Stream.of(BuildSystem.getDefault());
        } else {
          return Stream.of(targetBuildSystem.value());
        }
      })
      .collect(Collectors.toSet());
  }

  @NotNull
  private static List<Runner> runnersFor(@NotNull Class<?> klass, @NotNull Set<BuildSystem> buildSystems) throws InitializationError {
    ImmutableList.Builder<Runner> builder = new ImmutableList.Builder<>();
    for (BuildSystem system : buildSystems) {
      builder.add(new BuildSpecificGuiTestRunner(klass, system));
    }
    return builder.build();
  }

  @Override
  protected List<Runner> getChildren() {
    return myRunners;
  }

  private static final class BuildSpecificGuiTestRunner extends GuiTestRunner {
    public BuildSpecificGuiTestRunner(Class<?> testClass, @NotNull BuildSystem buildSystem) throws InitializationError {
      super(testClass, buildSystem);
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

    /**
     * Include information about the current build system as a part of the test's name to provide
     * better tooling support when running tests from within IntelliJ.
     */
    @Override
    protected String getName() {
      // The test name needs to be enclosed in square brackets due to the way IntelliJ parses test names
      // based on runners. Without square brackets the tests would show up in the Run window as:
      //
      //   > TestClassName
      //     > Running with buildSystemOne
      //       > TestClassName.testName
      //     > Running with buildSystemTwo
      //       > TestClassName.testName
      //
      // With square brackets they show as:
      //
      //   > TestClassName
      //     > [Running with buildSystemOne]
      //       > testName
      //     > [Running with buildSystemTwo]
      //       > testName
      //
      return "[Running with " + myBuildSystem.name().toLowerCase(Locale.US) + " based project]";
    }
  }
}
