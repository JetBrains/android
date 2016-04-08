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

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleInvoker}.
 */
public class GradleInvokerTest extends IdeaTestCase {
  private static final String SOURCE_GEN = "sourceGen";
  private static final String TEST_SOURCE_GEN = "testSourceGen";
  private static final String COMPILE_JAVA = "compileJava";
  private static final String COMPILE_TEST_JAVA = "compileTestJava";
  private static final String ASSEMBLE = "assemble";
  private static final String ASSEMBLE_ANDROID_TEST = "assembleAndroidTest";
  private static final String CLEAN = "clean";

  private String myModuleGradlePath;
  private AndroidFacet myAndroidFacet;
  private GradleInvoker myInvoker;

  private boolean myOriginalLoadAllTestArtifactsValue;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModuleGradlePath = GRADLE_PATH_SEPARATOR + myModule.getName();

    myInvoker = new GradleInvoker(myProject);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FacetManager facetManager = FacetManager.getInstance(myModule);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          model.addFacet(facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null));
          model.addFacet(facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null));
        }
        finally {
          model.commit();
        }
        AndroidGradleFacet facet = AndroidGradleFacet.getInstance(myModule);
        assertNotNull(facet);
        facet.getConfiguration().GRADLE_PROJECT_PATH = myModuleGradlePath;

        myAndroidFacet = AndroidFacet.getInstance(myModule);
        assertNotNull(myAndroidFacet);
      }
    });

    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES = Sets.newHashSet(SOURCE_GEN, TEST_SOURCE_GEN);
    myAndroidFacet.getProperties().COMPILE_JAVA_TASK_NAME = COMPILE_JAVA;
    myAndroidFacet.getProperties().COMPILE_JAVA_TEST_TASK_NAME = COMPILE_TEST_JAVA;
    myAndroidFacet.getProperties().ASSEMBLE_TASK_NAME = ASSEMBLE;
    myAndroidFacet.getProperties().ASSEMBLE_TEST_TASK_NAME = ASSEMBLE_ANDROID_TEST;

    myOriginalLoadAllTestArtifactsValue = GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS;
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = false;
  }

  @Override
  public void tearDown() throws Exception {
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = myOriginalLoadAllTestArtifactsValue;
    super.tearDown();
  }

  public void testAssembleTranslate() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsExactly("assembleTranslate");
        assertEquals(BuildMode.ASSEMBLE_TRANSLATE, getBuildMode());
      }
    });
    myInvoker.assembleTranslate();
  }

  public void testCleanProject() {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsExactly(CLEAN, qualifiedTaskName(SOURCE_GEN), qualifiedTaskName(TEST_SOURCE_GEN));
        // Make sure clean is first.
        assertEquals(CLEAN, tasks.get(0));
        assertEquals(BuildMode.CLEAN, getBuildMode());
      }
    });
    myInvoker.cleanProject();
  }

  public void testGenerateSources() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsExactly(qualifiedTaskName(SOURCE_GEN), qualifiedTaskName(TEST_SOURCE_GEN));
        assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
      }
    });
    myInvoker.generateSources(false);
  }

  public void testGenerateSourcesWithClean() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsExactly(CLEAN, qualifiedTaskName(SOURCE_GEN), qualifiedTaskName(TEST_SOURCE_GEN));
        assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
      }
    });
    myInvoker.generateSources(true);
  }

  public void testGenerateSourcesWithoutTestSourceGen() throws Exception {
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES.remove(TEST_SOURCE_GEN);

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsExactly(qualifiedTaskName(SOURCE_GEN));
        assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
      }
    });
    myInvoker.generateSources(false);
  }

  public void testCompileJava() throws Exception {
    final String mockableJar = "mockableJar";
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES.add(mockableJar);

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        // Make sure all "after sync tasks" are run, for running unit tests.
        assertThat(tasks).containsExactly(
          qualifiedTaskName(mockableJar),
          qualifiedTaskName(SOURCE_GEN),
          qualifiedTaskName(TEST_SOURCE_GEN),
          qualifiedTaskName(COMPILE_JAVA),
          qualifiedTaskName(COMPILE_TEST_JAVA));
        assertEquals(BuildMode.COMPILE_JAVA, getBuildMode());
      }
    });
    myInvoker.compileJava(new Module[]{myModule}, GradleInvoker.TestCompileType.NONE);
  }

  public void testCompileJava_forUnitTests() throws Exception {
    final String mockableJar = "mockableJar";
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES.add(mockableJar);

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        // Make sure all "after sync tasks" are run, for running unit tests.
        assertThat(tasks).containsExactly(
          qualifiedTaskName(mockableJar),
          qualifiedTaskName(SOURCE_GEN),
          qualifiedTaskName(TEST_SOURCE_GEN),
          qualifiedTaskName(COMPILE_TEST_JAVA));
        // If using Jack, running :app:compileDebugSources would be a waste of time.
        assertDoesntContain(tasks, COMPILE_JAVA);
        assertEquals(BuildMode.COMPILE_JAVA, getBuildMode());
      }
    });
    myInvoker.compileJava(new Module[]{myModule}, GradleInvoker.TestCompileType.JAVA_TESTS);
  }

  public void testAssemble() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsExactly(qualifiedTaskName(ASSEMBLE));
        assertEquals(BuildMode.ASSEMBLE, getBuildMode());
      }
    });
    myInvoker.assemble(new Module[]{myModule}, GradleInvoker.TestCompileType.NONE);
  }

  public void testAssemble_forTests() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsExactly(qualifiedTaskName(ASSEMBLE), qualifiedTaskName(ASSEMBLE_ANDROID_TEST));
        assertEquals(BuildMode.ASSEMBLE, getBuildMode());
      }
    });
    myInvoker.assemble(new Module[]{myModule}, GradleInvoker.TestCompileType.ANDROID_TESTS);
  }

  public void testRebuild() throws Exception {
    final String mockableJar = "mockableJar";
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES.add(mockableJar);

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        // Make sure all "after sync tasks" are run, for running unit tests.
        assertThat(tasks).containsExactly(
          CLEAN,
          qualifiedTaskName(mockableJar),
          qualifiedTaskName(SOURCE_GEN),
          qualifiedTaskName(TEST_SOURCE_GEN),
          qualifiedTaskName(COMPILE_JAVA),
          qualifiedTaskName(COMPILE_TEST_JAVA));
        // Make sure clean is first.
        assertEquals(CLEAN, tasks.get(0));
        assertEquals(BuildMode.REBUILD, getBuildMode());
      }
    });
    myInvoker.rebuild();
  }

  @Nullable
  private BuildMode getBuildMode() {
    return BuildSettings.getInstance(myProject).getBuildMode();
  }

  @NotNull
  private String qualifiedTaskName(@NotNull String taskName) {
    return myModuleGradlePath + GRADLE_PATH_SEPARATOR + taskName;
  }
}
