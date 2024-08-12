/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.run.producers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for run configurations for Kotlin test classes. */
@RunWith(JUnit4.class)
public class KotlinTestContextProviderTest extends BlazeRunConfigurationProducerTestCase {

  private static final Correspondence<RunConfiguration, Boolean> IS_BLAZE_RUN_CONFIGURATION =
      transforming(BlazeCommandRunConfiguration.class::isInstance, "is a Blaze run configuration");
  private static final Correspondence<BlazeCommandRunConfiguration, TestBlazeCall> HAS_BLAZE_CALL =
      transforming(TestBlazeCall::fromRunConfig, "has a Blaze invocation using");
  private static final Correspondence<BlazeCommandRunConfiguration, TargetExpression>
      HAS_ONLY_TARGET =
          transforming(BlazeCommandRunConfiguration::getSingleTarget, "has the only target");

  @Before
  public final void setup() {
    // Required for IntelliJ to recognize annotations, JUnit version, etc.
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runner/RunWith.java"),
        "package org.junit.runner;",
        "public @interface RunWith {",
        "    Class<? extends Runner> value();",
        "}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/Test.java"),
        "package org.junit;",
        "public @interface Test {}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runners/JUnit4.java"),
        "package org.junit.runners;",
        "public class JUnit4 {}");
    workspace.createPsiFile(
        new WorkspacePath("com/google/testing/testsize/MediumTest.java"),
        "package com.google.testing.testsize;",
        "public @interface MediumTest {}");
  }

  @Test
  public void classHasExactlyRunConfigurationForBlaze() throws Throwable {
    // Fake a test file in the file system.
    String testFilePath = "com/google/test/TestClass.kt";
    PsiFile testFile =
        createAndIndexFile(
            new WorkspacePath(testFilePath),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4::class)",
            "class TestClass {",
            "  @org.junit.Test",
            "  fun testMethod() {}",
            "}");
    KtClass testClass = findClass(testFile);

    // Fake the BUILD file.
    TargetIdeInfo testTarget =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_test")
            .setLabel("//com/google/test:TestClass")
            .addSource(sourceRoot(testFilePath))
            .build();
    registerTargets(testTarget);

    ImmutableList<RunConfiguration> configurations = getRunConfigurations(testClass);

    assertThat(configurations)
        .comparingElementsUsing(IS_BLAZE_RUN_CONFIGURATION)
        .containsExactly(true);
  }

  @Test
  public void runConfigurationForClassHasCorrectBlazeCommand() throws Throwable {
    // Fake a test file in the file system.
    String testFilePath = "com/google/test/TestClass.kt";
    PsiFile testFile =
        createAndIndexFile(
            new WorkspacePath(testFilePath),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4::class)",
            "class TestClass {",
            "  @org.junit.Test",
            "  fun testMethod() {}",
            "}");
    KtClass testClass = findClass(testFile);

    // Fake the BUILD file.
    TargetIdeInfo testTarget =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_test")
            .setLabel("//com/google/test:TestClass")
            .addSource(sourceRoot(testFilePath))
            .build();
    registerTargets(testTarget);

    ImmutableList<BlazeCommandRunConfiguration> configurations =
        getBlazeRunConfigurations(testClass);

    assertThat(configurations)
        .comparingElementsUsing(HAS_BLAZE_CALL)
        .containsExactly(
            TestBlazeCall.create(
                BlazeCommandName.TEST,
                TargetExpression.fromStringSafe("//com/google/test:TestClass"),
                "--test_filter=com.google.test.TestClass"));
  }

  @Test
  public void methodHasExactlyRunConfigurationForBlaze() throws Throwable {
    // Fake a test file in the file system.
    String testFilePath = "com/google/test/TestClass.kt";
    PsiFile testFile =
        createAndIndexFile(
            new WorkspacePath(testFilePath),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4::class)",
            "class TestClass {",
            "  @org.junit.Test",
            "  fun testMethod() {}",
            "}");
    KtNamedFunction firstMethod = findFirstMethod(testFile);

    // Fake the BUILD file.
    TargetIdeInfo testTarget =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_test")
            .setLabel("//com/google/test:TestClass")
            .addSource(sourceRoot(testFilePath))
            .build();
    registerTargets(testTarget);

    ImmutableList<RunConfiguration> configurations = getRunConfigurations(firstMethod);

    assertThat(configurations)
        .comparingElementsUsing(IS_BLAZE_RUN_CONFIGURATION)
        .containsExactly(true);
  }

  @Test
  public void runConfigurationForMethodHasCorrectBlazeCommand() throws Throwable {
    // Fake a test file in the file system.
    String testFilePath = "com/google/test/TestClass.kt";
    PsiFile testFile =
        createAndIndexFile(
            new WorkspacePath(testFilePath),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4::class)",
            "class TestClass {",
            "  @org.junit.Test",
            "  fun testMethod1() {}",
            "  @org.junit.Test",
            "  fun testMethod2() {}",
            "}");
    KtNamedFunction firstMethod = findFirstMethod(testFile);

    // Fake the BUILD file.
    TargetIdeInfo testTarget =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_test")
            .setLabel("//com/google/test:TestClass")
            .addSource(sourceRoot(testFilePath))
            .build();
    registerTargets(testTarget);

    ImmutableList<BlazeCommandRunConfiguration> configurations =
        getBlazeRunConfigurations(firstMethod);

    assertThat(configurations)
        .comparingElementsUsing(HAS_BLAZE_CALL)
        .containsExactly(
            TestBlazeCall.create(
                BlazeCommandName.TEST,
                TargetExpression.fromStringSafe("//com/google/test:TestClass"),
                "--test_filter=com.google.test.TestClass.testMethod1"));
  }

  @Test
  public void correctTargetChosenForGivenTestSize() throws Throwable {
    // Fake a test file in the file system.
    String testFilePath = "com/google/test/TestClass.kt";
    PsiFile testFile =
        createAndIndexFile(
            new WorkspacePath(testFilePath),
            "package com.google.test;",
            "@com.google.testing.testsize.MediumTest",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4::class)",
            "class TestClass {",
            "  @org.junit.Test",
            "  fun testMethod() {}",
            "}");
    KtClass testClass = findClass(testFile);

    // Fake the BUILD file. It's important that we don't directly include the test file in the
    // sources of the actual test target as otherwise another source->target heuristic kicks in.
    String testLibraryTargetLabel = "//com/google/test:TestClass";
    TargetIdeInfo testLibraryTarget =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel(testLibraryTargetLabel)
            .addSource(sourceRoot(testFilePath))
            .build();
    TargetIdeInfo mediumTestsTarget =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_test")
            .setLabel("//com/google/test:medium_tests")
            .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
            .addDependency(testLibraryTargetLabel)
            .build();
    TargetIdeInfo smallTestsTarget =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_test")
            .setLabel("//com/google/test:small_tests")
            .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
            .addDependency(testLibraryTargetLabel)
            .build();
    registerTargets(testLibraryTarget, mediumTestsTarget, smallTestsTarget);

    List<BlazeCommandRunConfiguration> runConfigurations = getBlazeRunConfigurations(testClass);

    assertThat(runConfigurations)
        .comparingElementsUsing(HAS_ONLY_TARGET)
        .containsExactly(TargetExpression.fromStringSafe("//com/google/test:medium_tests"));
  }

  private void registerTargets(TargetIdeInfo target, TargetIdeInfo... additionalTargets) {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(target)
            .addTargets(ImmutableList.copyOf(additionalTargets))
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
  }

  private static KtClass findClass(PsiFile kotlinFile) {
    KtClass kotlinClass = PsiUtils.findFirstChildOfClassRecursive(kotlinFile, KtClass.class);
    Preconditions.checkNotNull(kotlinClass, "KtClass not found in given test file.");
    Preconditions.checkNotNull(
        kotlinClass.getFqName(),
        "KtClass not properly parsed. Check the definition of the file for errors.");
    return kotlinClass;
  }

  private static KtNamedFunction findFirstMethod(PsiFile kotlinFile) {
    KtNamedFunction kotlinMethod =
        PsiUtils.findFirstChildOfClassRecursive(kotlinFile, KtNamedFunction.class);
    Preconditions.checkNotNull(kotlinMethod, "KtNamedFunction not found in given test file.");
    Preconditions.checkNotNull(
        kotlinMethod.getFqName(),
        "KtNamedFunction not properly parsed. Check the definition of the file for errors.");
    return kotlinMethod;
  }

  private ImmutableList<BlazeCommandRunConfiguration> getBlazeRunConfigurations(
      PsiElement psiElement) {
    // Request run configurations and filter them to the ones we created. Also map them to our
    // sub-type so that we can check them for correctness (e.g. correct Blaze command).
    return getRunConfigurations(psiElement).stream()
        .filter(BlazeCommandRunConfiguration.class::isInstance)
        .map(BlazeCommandRunConfiguration.class::cast)
        .collect(toImmutableList());
  }

  private ImmutableList<RunConfiguration> getRunConfigurations(PsiElement psiElement) {
    // Create the necessary context data we need to request run configurations.
    ConfigurationContext context = createContextFromPsi(psiElement);

    // Request the run configurations from IntelliJ's API. This eventually calls into the extension
    // points we use to provide our custom run configurations.
    List<ConfigurationFromContext> configurationsFromContext =
        Optional.ofNullable(context.getConfigurationsFromContext()).orElse(ImmutableList.of());

    // Extract the actually created run configurations from the response context (which provides
    // additional data) as we're only interested in the run configurations in the tests.
    return configurationsFromContext.stream()
        .map(ConfigurationFromContext::getConfiguration)
        .collect(toImmutableList());
  }

  // Replace with Correspondence.transforming() when the truth version bundled with the IntelliJ API
  // is high enough.
  private static <A, E> Correspondence<A, E> transforming(
      Function<A, E> transform, String description) {
    return new Correspondence<A, E>() {
      @Override
      public boolean compare(@Nullable A actual, @Nullable E expected) {
        return Objects.equals(transform.apply(actual), expected);
      }

      @Override
      public String toString() {
        return description;
      }
    };
  }

  @AutoValue
  abstract static class TestBlazeCall {

    abstract BlazeCommandName commandName();

    abstract TargetExpression target();

    abstract String testFilter();

    public static TestBlazeCall fromRunConfig(BlazeCommandRunConfiguration runConfiguration) {
      return create(
          getCommandType(runConfiguration),
          runConfiguration.getSingleTarget(),
          getTestFilterContents(runConfiguration));
    }

    public static TestBlazeCall create(
        BlazeCommandName commandName, TargetExpression target, String testFilter) {
      return new AutoValue_KotlinTestContextProviderTest_TestBlazeCall(
          commandName, target, testFilter);
    }
  }
}
