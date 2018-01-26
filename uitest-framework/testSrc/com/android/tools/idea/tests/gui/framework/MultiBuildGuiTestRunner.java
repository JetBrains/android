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
import org.jetbrains.annotations.NotNull;
import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link MultiBuildGuiTestRunner} allows ui-tests to run with multiple {@link TargetBuildSystem}'s.
 * When a ui-test is annotated to run with {@link MultiBuildGuiTestRunner}, an instance of {@link GuiTestRunner}
 * is created for each available build system defined in {@link TargetBuildSystem.BuildSystem}.
 * Then each test in the class is run with every {@link GuiTestRunner} instance that applies to it,
 * as determined by the {@link TargetBuildSystem} annotation.
 * Tests can specify required build systems using the {@link TargetBuildSystem} annotation.
 * <p>
 * Example of a test class containing tests containing different build systems:
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
 * If a test is not annotated with any build system, the default will be chosen.  Currently
 * the default is to run with only GRADLE as build system.
 */
public class MultiBuildGuiTestRunner extends Suite {
  @NotNull private final List<Runner> myRunners;

  public MultiBuildGuiTestRunner(Class<?> klass, RunnerBuilder builder) throws InitializationError {
    super(klass, Collections.emptyList());

    myRunners = new ArrayList<>();
    for (BuildSystem buildSystem : BuildSystem.values()) {
      myRunners.add(new GuiTestRunner(klass, buildSystem));
    }
  }

  @Override
  protected List<Runner> getChildren() {
    return myRunners;
  }
}
