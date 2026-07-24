/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers

import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils
import com.google.idea.blaze.base.model.primitives.WorkspacePath
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Integration tests for [BlazeBuildFileRunConfigurationProducer]. */
@RunWith(JUnit4::class)
class BlazeBuildFileRunConfigurationProducerTest : BlazeRunConfigurationProducerTestCase() {

  @Test
  fun testProducedFromFuncallExpression() {
    val buildFile = workspace.createPsiFile(WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'")

    val target = PsiUtils.findFirstChildOfClassRecursive(buildFile, FuncallExpression::class.java)
    assertThat(target).isNotNull()

    val context = createContextFromPsi(target)
    val configurations = requireNotNull(context.configurationsFromContext)
    assertThat(configurations).hasSize(1)

    val fromContext = configurations.first()
    assertThat(fromContext.isProducedBy(BlazeBuildFileRunConfigurationProducer::class.java)).isTrue()
    assertThat(fromContext.configuration).isInstanceOf(BlazeCommandRunConfiguration::class.java)

    val config = fromContext.configuration as BlazeCommandRunConfiguration
    assertThat(config.targetPatterns).containsExactly("//java/com/google/test:unit_tests")
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST)
  }

  @Test
  fun testTestSuiteMacroNameRecognized() {
    val buildFile = workspace.createPsiFile(WorkspacePath("java/com/google/test/BUILD"), "random_junit4_test_suites(name='gen_tests'")

    val target = PsiUtils.findFirstChildOfClassRecursive(buildFile, FuncallExpression::class.java)
    val context = createContextFromPsi(target)
    val configurations = requireNotNull(context.configurationsFromContext)
    assertThat(configurations).hasSize(1)

    val fromContext = configurations.first()
    assertThat(fromContext.isProducedBy(BlazeBuildFileRunConfigurationProducer::class.java)).isTrue()
    assertThat(fromContext.configuration).isInstanceOf(BlazeCommandRunConfiguration::class.java)

    val config = fromContext.configuration as BlazeCommandRunConfiguration
    assertThat(config.targetPatterns).containsExactly("//java/com/google/test:gen_tests")
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST)
  }

  @Test
  fun testProducedWhenInsideFuncallExpression() {
    val buildFile = workspace.createPsiFile(WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'")

    val nameString = PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral::class.java)
    assertThat(nameString).isNotNull()

    val context = createContextFromPsi(nameString)
    val configurations = requireNotNull(context.configurationsFromContext)
    assertThat(configurations).hasSize(1)

    val fromContext = configurations.first()
    assertThat(fromContext.isProducedBy(BlazeBuildFileRunConfigurationProducer::class.java)).isTrue()
    assertThat(fromContext.configuration).isInstanceOf(BlazeCommandRunConfiguration::class.java)

    val config = fromContext.configuration as BlazeCommandRunConfiguration
    assertThat(config.targetPatterns).containsExactly("//java/com/google/test:unit_tests")
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST)
  }

  @Test
  fun testConfigFromContextRecognizesItsOwnConfig() {
    val buildFile = workspace.createPsiFile(WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'")

    val nameString = PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral::class.java)
    val context = createContextFromPsi(nameString)
    val config = context.configuration!!.configuration as BlazeCommandRunConfiguration

    assertThat(BlazeBuildFileRunConfigurationProducer().isConfigurationFromContext(config, context)).isTrue()
  }

  @Test
  fun testConfigWithDifferentLabelIgnored() {
    val buildFile = workspace.createPsiFile(WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'")

    val nameString = PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral::class.java)
    val context = createContextFromPsi(nameString)
    val config = context.configuration!!.configuration as BlazeCommandRunConfiguration

    // modify the label, and check that is enough for the producer to class it as different.
    config.setTargetPattern("//java/com/google/test:integration_tests")

    assertThat(BlazeBuildFileRunConfigurationProducer().isConfigurationFromContext(config, context)).isFalse()
  }

  @Test
  fun testConfigWithTestFilterIgnored() {
    val buildFile = workspace.createPsiFile(WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'")

    val nameString = PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral::class.java)
    val context = createContextFromPsi(nameString)
    val config = context.configuration!!.configuration as BlazeCommandRunConfiguration

    val handlerState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)
    handlerState!!.blazeFlagsState.rawFlags = ImmutableList.of(BlazeFlags.TEST_FILTER + "=com.google.test.SingleTestClass#")

    assertThat(BlazeBuildFileRunConfigurationProducer().isConfigurationFromContext(config, context)).isFalse()
  }
}
