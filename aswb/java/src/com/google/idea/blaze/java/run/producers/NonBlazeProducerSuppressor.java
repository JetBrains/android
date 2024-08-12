/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

/** Suppresses certain non-Blaze configuration producers in Blaze projects. */
public class NonBlazeProducerSuppressor implements StartupActivity {

  private static final ImmutableList<String> KOTLIN_PRODUCERS =
      ImmutableList.of(
          "org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native.KotlinNativeRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.run.KotlinJvmTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.run.KotlinJvmTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.run.KotlinMultiplatformJvmTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.run.KotlinMultiplatformJvmTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.js.KotlinMultiplatformJsTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.js.KotlinMultiplatformJsTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.native.KotlinMultiplatformNativeTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.native.KotlinMultiplatformNativeTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.common.KotlinMultiplatformCommonTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.common.KotlinMultiplatformCommonTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.KotlinMultiplatformAllInDirectoryConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradleJava.testing.KotlinMultiplatformAllInPackageConfigurationProducer",
          "org.jetbrains.kotlin.idea.gradle.native.KotlinNativeRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.junit.KotlinJUnitRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.junit.KotlinPatternConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinJvmTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinMultiplatformJvmTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinMultiplatformJvmTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer",
          /** #api212: remove these duplicate producers. */
          "org.jetbrains.kotlin.idea.run.KotlinJUnitRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinPatternConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinTestMethodGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinJvmTestMethodGradleConfigurationProducer");

  private static final ImmutableList<String> ANDROID_PRODUCERS =
      ImmutableList.of(
          "com.android.tools.idea.run.AndroidConfigurationProducer",
          "com.android.tools.idea.run.configuration.AndroidComplicationRunConfigurationProducer",
          "com.android.tools.idea.run.configuration.AndroidTileRunConfigurationProducer",
          "com.android.tools.idea.run.configuration.AndroidWatchFaceRunConfigurationProducer",
          "com.android.tools.idea.testartifacts.instrumented.AndroidTestConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestClassAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestDirectoryAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestMethodAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestPackageAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestPatternConfigurationProducer");

  private static final ImmutableList<String> GRADLE_PRODUCERS =
      ImmutableList.of(
          "org.jetbrains.plugins.gradle.execution.GradleGroovyScriptRunConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.AllInDirectoryGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.PatternGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.service.execution.GradleRuntimeConfigurationProducer");

  private static final ImmutableList<String> JAVA_PRODUCERS =
      ImmutableList.of(
          "com.intellij.execution.junit.AbstractAllInDirectoryConfigurationProducer",
          "com.intellij.execution.junit.AllInDirectoryConfigurationProducer",
          "com.intellij.execution.junit.AllInPackageConfigurationProducer",
          "com.intellij.execution.junit.TestInClassConfigurationProducer",
          "com.intellij.execution.junit.TestClassConfigurationProducer",
          "com.intellij.execution.junit.PatternConfigurationProducer",
          "com.intellij.execution.junit.UniqueIdConfigurationProducer",
          "com.intellij.execution.junit.testDiscovery.JUnitTestDiscoveryConfigurationProducer",
          "com.intellij.execution.application.ApplicationConfigurationProducer");

  @Override
  public void runActivity(Project project) {
    if (Blaze.isBlazeProject(project)) {
      suppressProducers(project);
    }
  }

  static void suppressProducers(Project project) {
    RunConfigurationProducerService producerService =
        RunConfigurationProducerService.getInstance(project);
    producerService.getState().ignoredProducers.addAll(JAVA_PRODUCERS);
    producerService.getState().ignoredProducers.addAll(KOTLIN_PRODUCERS);
    producerService.getState().ignoredProducers.addAll(ANDROID_PRODUCERS);
    producerService.getState().ignoredProducers.addAll(GRADLE_PRODUCERS);
  }
}
