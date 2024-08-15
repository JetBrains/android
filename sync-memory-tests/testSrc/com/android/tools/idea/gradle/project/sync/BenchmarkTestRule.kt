/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.sync.memory.MemoryConstrainedTestRule
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

// Standard benchmark names
const val SUBSET_50_NAME = "50Modules"
const val SUBSET_100_NAME = "100Modules"
const val SUBSET_200_NAME = "200Modules"
const val SUBSET_500_NAME = "500Modules"
const val SUBSET_1000_NAME = "1000Modules"
const val SUBSET_2000_NAME = "2000Modules"
const val SUBSET_4200_NAME = "4200Modules"
const val MULTI_APP_100_NAME = "100Apps_1300Modules"
const val MULTI_APP_190_NAME = "190Apps_2200Modules"

// Benchmarks with different versions than standard benchmarks
const val SUBSET_1000_GRADLE_SNAPSHOT_NAME = "1000Modules_GradleSnapshot"
const val SUBSET_1000_KOTLIN_LATEST_NAME = "1000Modules_KotlinLatest"

// Feature benchmark names
const val FEATURE_RUNTIME_CLASSPATH_1000 = "FRuntimeClasspath1000"

interface BenchmarkTestRule : ProjectSetupRule, TestRule

fun createBenchmarkTestRule(projectName: String,
                            project: BenchmarkProject,
                            useLatestGradle: Boolean = false,
                            useLatestKotlin: Boolean = false): BenchmarkTestRule {
  val projectSetupRule = ProjectSetupRuleImpl(
    projectName,
    project,
    useLatestGradle,
    useLatestKotlin
  ) { AndroidProjectRule.withIntegrationTestEnvironment() }
  val wrappedRules =  RuleChain.outerRule(projectSetupRule.testEnvironmentRule)
    .around(projectSetupRule)
    .around(MemoryConstrainedTestRule(projectName, project.maxHeapMB).also {
      projectSetupRule.addListener(it.listener)
    })
    // The 233 platform doesn't disable indexing during project import but we don't want that to influence benchmarks, yet.
    // TODO(b/315761803): Enable and evaluate benchmark behavior after the platform merge is done and any possible regressions are handled.
    .around(DisableIndexingDuringSyncRule())
    .around(CollectDaemonLogsRule())
  return object : BenchmarkTestRule,
                  ProjectSetupRule by projectSetupRule,
                  TestRule by wrappedRules {}
}










