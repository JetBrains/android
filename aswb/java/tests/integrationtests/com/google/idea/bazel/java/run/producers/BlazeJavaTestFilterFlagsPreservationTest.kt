/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.bazel.java.run.producers

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.WorkspacePath
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase
import com.google.idea.blaze.base.run.producers.TestContextProvider
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.intellij.psi.PsiClassOwner
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** High-level integration tests verifying that custom Blaze flags are preserved when a run configuration is updated from context. */
@RunWith(JUnit4::class)
class BlazeJavaTestFilterFlagsPreservationTest : BlazeRunConfigurationProducerTestCase() {

  @Before
  fun setup() {
    workspace.createPsiFile(
      WorkspacePath("org/junit/runner/RunWith.java"),
      "package org.junit.runner;",
      "public @interface RunWith {",
      "    Class<? extends Runner> value();",
      "}",
    )
    workspace.createPsiFile(WorkspacePath("org/junit/Test.java"), "package org.junit;", "public @interface Test {}")
    workspace.createPsiFile(WorkspacePath("org/junit/runners/JUnit4.java"), "package org.junit.runners;", "public class JUnit4 {}")
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun testPreExistingBlazeFlagsPreservedWhenConfigurationUpdatedFromContext() {
    val javaFile =
      createAndIndexFile(
        WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class TestClass {",
        "  @org.junit.Test",
        "  public void testMethod1() {}",
        "  @org.junit.Test",
        "  public void testMethod2() {}",
        "}",
      )

    val target = TargetInfo(Label.create("//java/com/google/test:TestClass"), "java_test")
    registerTargets(target)

    val javaClass = (javaFile as PsiClassOwner).classes[0]
    val method1 = javaClass.findMethodsByName("testMethod1", false)[0]

    // 1. Create run configuration from class context using standard IDE producer API
    val classContext = createContextFromPsi(javaClass)
    val fromContextList = runWithProgress { classContext.configurationsFromContext }
    assertThat(fromContextList).isNotNull()
    assertThat(fromContextList).isNotEmpty()

    val fromContext = fromContextList!![0]
    val configuration = fromContext.configuration as BlazeCommandRunConfiguration
    performFirstRun(configuration, classContext)

    // 2. Add custom pre-existing flags to the run configuration (e.g. --config=dev, --test_output=streamed)
    val commonState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)!!
    commonState.blazeFlagsState.rawFlags = listOf("--config=dev", "--test_output=streamed")

    // 3. Create context for specific test method and apply to existing configuration
    val methodContext = createContextFromPsi(method1)
    val testContext = runWithProgress {
      TestContextProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getTestContext(methodContext) }
    }
    assertThat(testContext).isNotNull()

    val setupSuccess = runWithProgress {
      val resolved = kotlinx.coroutines.runBlocking { testContext!!.resolve(project) }
      resolved.setupRunConfiguration(configuration)
    }
    assertThat(setupSuccess).isTrue()

    // 4. Assert that custom flags are retained while test filter is updated
    val flags = commonState.blazeFlagsState.rawFlags
    assertThat(flags).contains("--config=dev")
    assertThat(flags).contains("--test_output=streamed")
    assertThat(flags.any { it.contains("testMethod1") }).isTrue()
  }
}
