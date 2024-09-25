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
package com.google.idea.blaze.python.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.run.producers.TestContextRunConfigurationProducer;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link PyTestContextProvider}. */
@RunWith(JUnit4.class)
public class BlazePyTestConfigurationProducerTest extends BlazeRunConfigurationProducerTestCase {

  @Before
  public final void suppressNativeProducers() {
    // Project components triggered before we can set up BlazeImportSettings.
    NonBlazeProducerSuppressor.suppressProducers(getProject());
  }

  @Test
  public void testProducedFromPyFile() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(googletest.TestCase):",
            "  def testSomething():",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    ConfigurationContext context = createContextFromPsi(pyFile);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config)).isNull();
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test unittest.py");
  }

  @Test
  public void testProducedFromPyClass() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(googletest.TestCase):",
            "  def testSomething():",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyClass pyClass = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyClass.class);
    assertThat(pyClass).isNotNull();

    ConfigurationContext context = createContextFromPsi(pyClass);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config)).isEqualTo("--test_filter=UnitTest");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest (unittest.py)");
  }

  @Test
  public void testProducedFromTestCase() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(googletest.TestCase):",
            "  def testSomething():",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config)).isEqualTo("--test_filter=UnitTest.testSomething");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.testSomething (unittest.py)");
  }

  @Test
  public void testProducedFromTestCaseWithParameters() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(parameterized.TestCase):",
            "  @parameterized.parameters(1,100)",
            "  def testSomething(self, value):",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=\"UnitTest.testSomething0 UnitTest.testSomething1\"");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.testSomething (unittest.py)");
  }

  @Test
  public void testProducedFromTestCaseWithNamedParametersDict() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(parameterized.TestCase):",
            "  @parameterized.named_parameters(",
            "    {'testcase_name': '_First', value: 1},",
            "    {'testcase_name': '_Second', value: 100},",
            "  )",
            "  def testSomething(self, value):",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=\"UnitTest.testSomething_First UnitTest.testSomething_Second\"");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.testSomething (unittest.py)");
  }

  @Test
  public void testProducedFromTestCaseWithNamedParametersTuple() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(parameterized.TestCase):",
            "  @parameterized.named_parameters(",
            "    ('_First', 1),",
            "    ('_Second', 100),",
            "  )",
            "  def testSomething(self, value):",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=\"UnitTest.testSomething_First UnitTest.testSomething_Second\"");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.testSomething (unittest.py)");
  }

  @Test
  public void testProducedFromTestCaseWithNamedParametersTupleAndUnderscores() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(parameterized.TestCase):",
            "  @parameterized.named_parameters(",
            "    ('First', 1),",
            "    ('_Second', 100),", // intentional underscore
            "  )",
            "  def test_something(self, value):",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config))
        .isEqualTo(
            "--test_filter=\"UnitTest.test_something_First UnitTest.test_something_Second\"");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.test_something (unittest.py)");
  }

  @Test
  public void testProducedFromTestCaseWithParametersList() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(parameterized.TestCase):",
            "  @parameterized.parameters([1,100])",
            "  def testSomething(self, value):",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=\"UnitTest.testSomething0 UnitTest.testSomething1\"");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.testSomething (unittest.py)");
  }

  @Test
  public void testProducedFromTestCaseWithNamedParametersDictList() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(parameterized.TestCase):",
            "  @parameterized.named_parameters([",
            "    {'testcase_name': '_First', value: 1},",
            "    {'testcase_name': '_Second', value: 100},",
            "  ])",
            "  def testSomething(self, value):",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=\"UnitTest.testSomething_First UnitTest.testSomething_Second\"");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.testSomething (unittest.py)");
  }

  @Test
  public void testProducedFromTestCaseWithEmptyParametersList() throws Throwable {
    PsiFile pyFile =
        createAndIndexFile(
            new WorkspacePath("py/test/unittest.py"),
            "class UnitTest(parameterized.TestCase):",
            "  @parameterized.parameters()",
            "  def testSomething(self, value):",
            "    return");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("py_test")
                    .setLabel("//py/test:unittests")
                    .addSource(sourceRoot("py/test/unittest.py"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PyFunction function = PsiUtils.findFirstChildOfClassRecursive(pyFile, PyFunction.class);
    assertThat(function).isNotNull();

    ConfigurationContext context = createContextFromPsi(function);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//py/test:unittests"));
    assertThat(getTestFilterContents(config)).isNull();
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    assertThat(config.getName()).isEqualTo("Bazel test UnitTest.testSomething (unittest.py)");
  }
}
