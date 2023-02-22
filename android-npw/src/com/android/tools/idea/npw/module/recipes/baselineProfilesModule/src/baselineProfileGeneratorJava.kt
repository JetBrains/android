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
package com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src

fun baselineProfileGeneratorJava(
  targetModuleName: String,
  className: String,
  packageName: String,
  targetPackageName: String
): String {
  return """package $packageName;

import androidx.benchmark.macro.ExperimentalBaselineProfilesApi;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test class generates a basic startup baseline profile for the target package.
 * <p>
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the <a href="https://d.android.com/topic/performance/baselineprofiles">baseline profile documentation</a>
 * for more information.
 * <p>
 * You can run the generator with the Generate Baseline Profile run configuration,
 * or directly with {@code generateBaselineProfiles} Gradle task:
 * <pre>
 * ./gradlew :$targetModuleName:generateReleaseBaselineProfiles -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
 * </pre>
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 * <p>
 * Check <a href="https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args">documentation</a>
 * for more information about instrumentation arguments.
 * <p>
 * After you run the generator, you can verify the improvements running the {@link StartupBenchmarks} benchmark.
 **/
@ExperimentalBaselineProfilesApi
@RunWith(AndroidJUnit4.class)
@LargeTest
public class $className {
    @Rule
    public BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @Test
    public void generate() {
        baselineProfileRule.collectBaselineProfile("$targetPackageName", scope -> {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll
            // through your most important UI.

            // Start default activity for your app
            scope.pressHome();
            scope.startActivityAndWait();

            // TODO Write more interactions to optimize advanced journeys of your app.
            // For example:
            // 1. Wait until the content is asynchronously loaded
            // 2. Scroll the feed content
            // 3. Navigate to detail screen

            // Check UiAutomator documentation for more information how to interact with the app.
            // https://d.android.com/training/testing/other-components/ui-automator

            return Unit.INSTANCE;
        });
    }
}

"""
}
