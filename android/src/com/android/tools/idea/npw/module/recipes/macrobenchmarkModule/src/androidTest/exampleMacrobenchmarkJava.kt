/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.androidTest

fun exampleMacrobenchmarkJava(packageName: String, targetPackageName: String) =
"""package $packageName;

import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ExampleStartupBenchmark {

    @Rule
    public MacrobenchmarkRule mBenchmarkRule = new MacrobenchmarkRule();

    @Test
    public void startup() {
        mMacrobenchmarkRule.measureRepeated(
                "$targetPackageName",
                Arrays.asList(new StartupTimingMetric()),
                new CompilationMode.SpeedProfile(),
                StartupMode.COLD,
                3,
                scope -> {
                    scope.pressHome(300);
                    scope.startActivityAndWait((intent) -> null);
                    return null;
                });
    }
}
"""
