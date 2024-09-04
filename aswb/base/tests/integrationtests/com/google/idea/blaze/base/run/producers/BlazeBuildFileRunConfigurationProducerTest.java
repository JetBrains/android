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
package com.google.idea.blaze.base.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeBuildFileRunConfigurationProducer}. */
@RunWith(JUnit4.class)
public class BlazeBuildFileRunConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testProducedFromFuncallExpression() {
    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'");

    FuncallExpression target =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, FuncallExpression.class);
    assertThat(target).isNotNull();

    ConfigurationContext context = createContextFromPsi(target);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BlazeBuildFileRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//java/com/google/test:unit_tests"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testTestSuiteMacroNameRecognized() {
    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/BUILD"),
            "random_junit4_test_suites(name='gen_tests'");

    FuncallExpression target =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, FuncallExpression.class);
    ConfigurationContext context = createContextFromPsi(target);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BlazeBuildFileRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//java/com/google/test:gen_tests"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testProducedWhenInsideFuncallExpression() {
    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'");

    StringLiteral nameString =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral.class);
    assertThat(nameString).isNotNull();

    ConfigurationContext context = createContextFromPsi(nameString);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BlazeBuildFileRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//java/com/google/test:unit_tests"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testConfigFromContextRecognizesItsOwnConfig() {
    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'");

    StringLiteral nameString =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral.class);
    ConfigurationContext context = createContextFromPsi(nameString);
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) context.getConfiguration().getConfiguration();

    assertThat(
            new BlazeBuildFileRunConfigurationProducer()
                .isConfigurationFromContext(config, context))
        .isTrue();
  }

  @Test
  public void testConfigWithDifferentLabelIgnored() {
    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'");

    StringLiteral nameString =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral.class);
    ConfigurationContext context = createContextFromPsi(nameString);
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) context.getConfiguration().getConfiguration();

    // modify the label, and check that is enough for the producer to class it as different.
    config.setTarget(Label.create("//java/com/google/test:integration_tests"));

    assertThat(
            new BlazeBuildFileRunConfigurationProducer()
                .isConfigurationFromContext(config, context))
        .isFalse();
  }

  @Test
  public void testConfigWithTestFilterIgnored() {
    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'");

    StringLiteral nameString =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, StringLiteral.class);
    ConfigurationContext context = createContextFromPsi(nameString);
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) context.getConfiguration().getConfiguration();

    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    handlerState
        .getBlazeFlagsState()
        .setRawFlags(
            ImmutableList.of(BlazeFlags.TEST_FILTER + "=com.google.test.SingleTestClass#"));

    assertThat(
            new BlazeBuildFileRunConfigurationProducer()
                .isConfigurationFromContext(config, context))
        .isFalse();
  }
}
