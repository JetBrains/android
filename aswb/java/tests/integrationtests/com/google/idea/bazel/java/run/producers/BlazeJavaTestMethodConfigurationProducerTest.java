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
package com.google.idea.bazel.java.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.run.producers.TestContextRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for configuring run configurations from java test methods. */
@RunWith(JUnit4.class)
public class BlazeJavaTestMethodConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

  @Before
  public final void setup() {
    // required for IntelliJ to recognize annotations, JUnit version, etc.
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runner/RunWith.java"),
        "package org.junit.runner;"
            + "public @interface RunWith {"
            + "    Class<? extends Runner> value();"
            + "}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/Test.java"),
        "package org.junit;",
        "public @interface Test {}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runners/JUnit4.java"),
        "package org.junit.runners;",
        "public class JUnit4 {}");
  }

  @Test
  public void testProducedFromPsiMethod() throws Throwable {
    // Arrange
    PsiFile javaFile =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/TestClass.java"),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
            "public class TestClass {",
            "  @org.junit.Test",
            "  public void testMethod1() {}",
            "}");

    registerTargets(new TargetInfo(Label.create("//java/com/google/test:TestClass"), "java_test"));
    PsiMethod method = PsiUtils.findFirstChildOfClassRecursive(javaFile, PsiMethod.class);

    // Act
    ConfigurationContext context = createContextFromPsi(method);
    List<ConfigurationFromContext> configurations =
        runWithProgress(context::getConfigurationsFromContext);
    ConfigurationFromContext fromContext = configurations.get(0);

    // Assert
    assertThat(configurations).hasSize(1);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    performFirstRun(config, context);
    assertThat(config.getTargetPatterns()).containsExactly("//java/com/google/test:TestClass");
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=com.google.test.TestClass#testMethod1$");
    assertThat(config.getName()).isEqualTo("Bazel test TestClass.testMethod1");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);

    BlazeCommandRunConfigurationCommonState state =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    assertThat(state.getBlazeFlagsState().getRawFlags()).contains(BlazeFlags.DISABLE_TEST_SHARDING);
  }

  @Test
  public void testConfigFromContextRecognizesItsOwnConfig() throws Throwable {
    PsiMethod method = setupGenericJunitTestClassAndBlazeTarget();
    ConfigurationContext context = createContextFromPsi(method);
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration)
            runWithProgress(context::getConfiguration).getConfiguration();
    performFirstRun(config, context);

    boolean isConfigFromContext =
        runWithProgress(
            () ->
                new TestContextRunConfigurationProducer()
                    .isConfigurationFromContext(config, context));

    assertThat(isConfigFromContext).isTrue();
  }

  @Test
  public void testConfigWithDifferentLabelIsIgnored() throws Throwable {
    // Arrange
    PsiMethod method = setupGenericJunitTestClassAndBlazeTarget();
    ConfigurationContext context = createContextFromPsi(method);
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration)
            runWithProgress(context::getConfiguration).getConfiguration();
    performFirstRun(config, context);
    // modify the label, and check that is enough for the producer to class it as different.
    config.setTargetPattern("//java/com/google/test:DifferentTestTarget");

    // Act
    boolean isConfigFromContext =
        runWithProgress(
            () ->
                new TestContextRunConfigurationProducer()
                    .isConfigurationFromContext(config, context));

    // Assert
    assertThat(isConfigFromContext).isTrue();
  }

  @Test
  public void testConfigWithDifferentFilterIgnored() throws Throwable {
    // Arrange
    PsiMethod method = setupGenericJunitTestClassAndBlazeTarget();
    ConfigurationContext context = createContextFromPsi(method);
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration)
            runWithProgress(context::getConfiguration).getConfiguration();
    performFirstRun(config, context);
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);

    // modify the test filter, and check that is enough for the producer to class it as different.
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    flags.add(BlazeFlags.TEST_FILTER + "=com.google.test.DifferentTestClass#");
    handlerState.getBlazeFlagsState().setRawFlags(flags);

    // Act
    boolean isConfigFromContext =
        runWithProgress(
            () ->
                new TestContextRunConfigurationProducer()
                    .isConfigurationFromContext(config, context));

    // Assert
    assertThat(isConfigFromContext).isFalse();
  }

  /**
   * Creates a JUnit test class and associated blaze target, and returns a PsiMethod from that
   * class. Used when the implementation details (class name, target string, etc.) aren't relevant
   * to the test.
   */
  private PsiMethod setupGenericJunitTestClassAndBlazeTarget() throws Throwable {
    PsiFile javaFile =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/TestClass.java"),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
            "public class TestClass {",
            "  @org.junit.Test",
            "  public void testMethod1() {}",
            "}");

    registerTargets(new TargetInfo(Label.create("//java/com/google/test:TestClass"), "java_test"));

    return PsiUtils.findFirstChildOfClassRecursive(javaFile, PsiMethod.class);
  }
}
