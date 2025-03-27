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

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun baselineProfileGeneratorKt(
  targetModuleName: String,
  pluginTaskName: String,
  className: String,
  packageName: String,
  targetPackageName: String,
  useInstrumentationArgumentForAppId: Boolean
): String {
  return """package ${escapeKotlinIdentifier(packageName)}

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
${if (useInstrumentationArgumentForAppId) "import androidx.test.platform.app.InstrumentationRegistry" else ""}
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :$targetModuleName:$pluginTaskName
 * ```
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class $className {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        ${if (useInstrumentationArgumentForAppId) "// The application id for the running build variant is read from the instrumentation arguments." else "// This example works only with the variant with application id `$targetPackageName`.\""}
        rule.collect(
            packageName = ${if (useInstrumentationArgumentForAppId) "InstrumentationRegistry.getArguments().getString(\"targetAppId\") ?: throw Exception(\"targetAppId not passed as instrumentation runner arg\")" else "\"$targetPackageName\""},

            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            includeInStartupProfile = true
        ) {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll through your most important UI.

            // Start default activity for your app
            pressHome()
            startActivityAndWait()

            // TODO Write more interactions to optimize advanced journeys of your app.
            // For example:
            // 1. Wait until the content is asynchronously loaded
            // 2. Scroll the feed content
            // 3. Navigate to detail screen

            // Check UiAutomator documentation for more information how to interact with the app.
            // https://d.android.com/training/testing/other-components/ui-automator
        }
    }
}

"""
}
