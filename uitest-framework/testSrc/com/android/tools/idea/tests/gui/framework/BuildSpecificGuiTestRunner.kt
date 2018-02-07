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
package com.android.tools.idea.tests.gui.framework

import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.google.common.collect.Iterables
import org.junit.runners.Parameterized
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * [BuildSpecificGuiTestRunner] allows ui-tests to run with multiple [TargetBuildSystem]'s
 * by running the test with [Parameterized] and using the [BuildSpecificGuiTestRunner.Factory]
 * runner factory. This runner must be used in conjunction with JUnit4's parameterized tests. Additional
 * naming support can be added using the [Parameterized.Parameters] annotation (see example below).
 *
 * Tests can specify one or more build systems to target via the [TargetBuildSystem.BuildSystem] annotation.
 * Each test is then run once per indicated build system.
 *
 * Example of a class containing tests to be run with multiple build systems:
 * <pre>
 * &#064;RunWith(Parameterized.class)
 * &#064;Parameterized.UseParametersRunnerFactory(BuildSpecificGuiTestRunner.Factory.class)
 * public class MultiBuildSystemTest {
 *
 *     &#064;Rule public final GuiTestRule guiTest = new GuiTestRule();
 *
 *     &#064;Parameterized.Parameters(name="{0}")
 *     public static TargetBuildSystem.BuildSystem[] data() {
 *         return TargetBuildSystem.BuildSystem.values();
 *     }
 *
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
 * If a test is not annotated with any build system, then {@link TargetBuildSystem.BuildSystem#getDefault()} will be used.
 */
class BuildSpecificGuiTestRunner @Throws(InitializationError::class) constructor(test: TestWithParameters) : GuiTestRunner(
    test.testClass.javaClass,
    Iterables.getOnlyElement(test.parameters) as TargetBuildSystem.BuildSystem
) {

  class Factory : ParametersRunnerFactory {
    override fun createRunnerForTestWithParameters(test: TestWithParameters) = BuildSpecificGuiTestRunner(test)
  }

  override fun getChildren(): List<FrameworkMethod> = computeTestMethods().filter { isMethodApplicable(it) }

  private fun isMethodApplicable(method: FrameworkMethod): Boolean {
    // if there are no annotations on the method, we can run it only with the default build system
    val annotation = method.getAnnotation(TargetBuildSystem::class.java) ?: return myBuildSystem.isDefault

    // if the method is annotated, then the list of build systems the method targets must include the current build system
    return annotation.value.contains(myBuildSystem)
  }

  /*
   * The name needs to be enclosed in square brackets due to the way IntelliJ parses runner names.
   * Without square brackets the tests would show up in the Run window as:
   *
   *   > TestClassName
   *     > Running with buildSystemOne
   *       > TestClassName.testName
   *     > Running with buildSystemTwo
   *       > TestClassName.testName
   *
   * With square brackets they show as:
   *
   *   > TestClassName
   *     > [Running with buildSystemOne]
   *       > testName
   *     > [Running with buildSystemTwo]
   *       > testName
   */
  override fun getName(): String = "[Running with a $myBuildSystem project]"

  /**
   * Formatted as "name\[buildSystem\]" to keep consistent with JUnit 4's parameterized test
   * naming. This is required for proper tooling support from IntelliJ.
   */
  override fun testName(method: FrameworkMethod): String = method.name + "[" + myBuildSystem + "]"
}
