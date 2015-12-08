/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker;

import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.module.Module;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleInvoker}.
 */
public class GradleInvokerTest extends AbstractGradleInvokerTest {
  public void testCleanProject() {
    myInvoker.addBeforeGradleInvocationTask(tasks -> {
      assertThat(tasks).containsExactly(CLEAN, qualifiedTaskName(SOURCE_GEN), qualifiedTaskName(ANDROID_TEST_SOURCE_GEN));
      // Make sure clean is first.
      assertEquals(CLEAN, tasks.get(0));
      assertEquals(BuildMode.CLEAN, getBuildMode());
    });
    myInvoker.cleanProject();
  }

  public void testGenerateSources() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(tasks -> {
      assertThat(tasks).containsExactly(qualifiedTaskName(SOURCE_GEN), qualifiedTaskName(ANDROID_TEST_SOURCE_GEN));
      assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
    });
    myInvoker.generateSources(false);
  }

  public void testGenerateSourcesWithClean() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(tasks -> {
      assertThat(tasks).containsExactly(CLEAN, qualifiedTaskName(SOURCE_GEN), qualifiedTaskName(ANDROID_TEST_SOURCE_GEN));
      assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
    });
    myInvoker.generateSources(true);
  }

  public void testGenerateSourcesWithoutTestSourceGen() throws Exception {
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES.remove(ANDROID_TEST_SOURCE_GEN);

    myInvoker.addBeforeGradleInvocationTask(tasks -> {
      assertThat(tasks).containsExactly(qualifiedTaskName(SOURCE_GEN));
      assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
    });
    myInvoker.generateSources(false);
  }

  public void testCompileJava() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(tasks -> {
      assertThat(tasks).containsExactly(qualifiedTaskName(SOURCE_GEN),
                                        qualifiedTaskName(ANDROID_TEST_SOURCE_GEN),
                                        qualifiedTaskName(COMPILE_JAVA),
                                        qualifiedTaskName(COMPILE_ANDROID_TEST_JAVA));
      assertEquals(BuildMode.COMPILE_JAVA, getBuildMode());
    });
    myInvoker.compileJava(new Module[]{myModule}, GradleInvoker.TestCompileType.NONE);
  }

  public void testRebuild() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(tasks -> {
      assertThat(tasks).containsExactly(CLEAN,
                                        qualifiedTaskName(SOURCE_GEN),
                                        qualifiedTaskName(ANDROID_TEST_SOURCE_GEN),
                                        qualifiedTaskName(COMPILE_JAVA),
                                        qualifiedTaskName(COMPILE_ANDROID_TEST_JAVA));
      // Make sure clean is first.
      assertEquals(CLEAN, tasks.get(0));
      assertEquals(BuildMode.REBUILD, getBuildMode());
    });
    myInvoker.rebuild();
  }
}
