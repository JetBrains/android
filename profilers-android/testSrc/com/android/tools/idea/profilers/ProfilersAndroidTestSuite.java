/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.idea.profilers.performance.CaptureDetailsTest;
import com.android.tools.idea.profilers.performance.CpuProfilerAtraceCaptureTest;
import com.android.tools.idea.profilers.performance.CpuProfilerEmptyCaptureTest;
import com.android.tools.idea.profilers.performance.CpuProfilerPerfettoCaptureTest;
import com.android.tools.idea.profilers.performance.MemoryClassifierViewFindSuperSetNodeTest;
import com.android.tools.idea.profilers.performance.MemoryProfilerHeapDumpTest;
import com.android.tools.idea.profilers.performance.TraceProcessorDaemonBenchmarkTest;
import com.android.tools.tests.IdeaTestSuiteBase;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  ProfilersAndroidTestSuite.class, // a suite mustn't contain itself
  // Benchmark performance tests should not be part of this suite, because they will be run on their own test rule
  // See intellij.android.profilersAndroid.performance in profilers-android/BUILD
  PerformanceTestSuite.class,
  CpuProfilerAtraceCaptureTest.class,
  CpuProfilerEmptyCaptureTest.class,
  CpuProfilerPerfettoCaptureTest.class,
  MemoryProfilerHeapDumpTest.class, // b/152344964
  MemoryClassifierViewFindSuperSetNodeTest.class,
  TraceProcessorDaemonBenchmarkTest.class,
  CaptureDetailsTest.class,
})
public class ProfilersAndroidTestSuite extends IdeaTestSuiteBase {
  static {
    leakChecker.enabled = false;  // TODO(b/264602053): fix leaks.
  }
}
