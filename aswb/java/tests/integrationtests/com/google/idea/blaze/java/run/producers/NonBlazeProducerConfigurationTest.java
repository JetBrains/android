/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.intellij.execution.actions.RunConfigurationProducer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link NonBlazeProducerSuppressor}. The aim of the is test is to make sure
 * that non Bazel producers are suppressed. This test should alert us when new non Bazel producers
 * are available so that we can add them to the list of suppressed producers (or to
 * `ACCEPTED_NON_BAZEL_PRODUCERS` list if they are not harmful). This test covers Java, Kotlin,
 * Android and Gradle producers.
 */
@RunWith(JUnit4.class)
public class NonBlazeProducerConfigurationTest extends BlazeRunConfigurationProducerTestCase {

  private static final ImmutableSet<String> ACCEPTED_NON_BAZEL_PRODUCERS =
      ImmutableSet.of(
          "com.intellij.execution.jar.JarApplicationConfigurationProducer",
          "com.intellij.execution.scratch.JavaScratchConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.script.standalone.KotlinStandaloneScriptRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradle.testing.js.KotlinMultiplatformJsTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradle.testing.js.KotlinMultiplatformJsTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradle.testing.native.KotlinMultiplatformNativeTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradle.testing.native.KotlinMultiplatformNativeTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradle.testing.common.KotlinMultiplatformCommonTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradle.testing.common.KotlinMultiplatformCommonTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.run.KotlinGradleTaskRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.run.KotlinMultiplatformJvmRunConfigurationProducer",
          "org.jetbrains.kotlin.ide.konan.KotlinNativeRunConfigurationProducer",
          "com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfigurationProducer",
          "com.android.tools.idea.run.configuration.BaselineProfileConfigurationProducer",
          "com.android.tools.idea.testartifacts.instrumented.kmp.KotlinMultiplatformAndroidTestConfigurationProducer");

  @Before
  public final void suppressNativeProducers() {
    NonBlazeProducerSuppressor.suppressProducers(getProject());
  }

  @Test
  public void testNonBlazeProducersSuppressed() {
    List<RunConfigurationProducer<?>> producers =
        RunConfigurationProducer.getProducers(getProject());

    List<String> unsuppressedProducers =
        producers.stream()
            .filter(producer -> !(producer instanceof BlazeRunConfigurationProducer))
            .map(producer -> producer.getClass().getName())
            .filter(producer -> !ACCEPTED_NON_BAZEL_PRODUCERS.contains(producer))
            .collect(toCollection(ArrayList::new));

    // This asserts that all non Bazel producers which were not suppressed are a subset of
    // `ACCEPTED_NON_BAZEL_PRODUCERS`
    assertWithMessage(
            "The Producers %s were not suppressed correctly. If you do not wish to suppress them,"
                + " please add them to ACCEPTED_NON_BAZEL_PRODUCERS list.",
            unsuppressedProducers)
        .that(unsuppressedProducers.isEmpty())
        .isTrue();
  }
}
