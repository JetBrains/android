/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.templates.AndroidGradleArtifactsTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static org.fest.assertions.Assertions.assertThat;

public abstract class AbstractGradleInvokerTest extends AndroidGradleArtifactsTestCase {
  protected static final String SOURCE_GEN = "generateDebugSources";
  protected static final String ANDROID_TEST_SOURCE_GEN = "generateDebugAndroidTestSources";
  protected static final String COMPILE_JAVA = "compileDebugSources";
  protected static final String COMPILE_ANDROID_TEST_JAVA = "compileDebugAndroidTestSources";
  protected static final String COMPILE_UNIT_TEST_JAVA = "compileDebugUnitTestSources";
  protected static final String ASSEMBLE = "assembleDebug";
  protected static final String ASSEMBLE_ANDROID_TEST = "assembleDebugAndroidTest";
  protected static final String CLEAN = "clean";
  protected static final String PREPARE_UNIT_TEST_DEPENDENCIES = "prepareDebugUnitTestDependencies";
  protected static final String MOCKABLE_ANDROID_JAR = "mockableAndroidJar";

  protected String myModuleGradlePath;
  protected Module myModule;
  protected GradleInvoker myInvoker;

  @Override
  protected void checkForSettingsDamage(@NotNull List<Throwable> exceptions) {
    // for this test we don't care for this check
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadProject("guiTests/SimpleApplication", false);
    myModule = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("app");
    assertNotNull(myModule);

    myModuleGradlePath = GRADLE_PATH_SEPARATOR + myModule.getName();
    myInvoker = new GradleInvoker(getProject());
  }

  @Nullable
  protected BuildMode getBuildMode() {
    return BuildSettings.getInstance(getProject()).getBuildMode();
  }

  @NotNull
  protected String qualifiedTaskName(@NotNull String taskName) {
    return myModuleGradlePath + GRADLE_PATH_SEPARATOR + taskName;
  }

  // Following are common tests for all GradleInvoker test cases
  public void testAssembleTranslate() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsOnly("assembleTranslate");
        assertEquals(BuildMode.ASSEMBLE_TRANSLATE, getBuildMode());
      }
    });
    myInvoker.assembleTranslate();
  }

  public void testCompileJava_forUnitTests() throws Exception {
    if (!loadAllTestArtifacts()) {
      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(myModule);
      assertNotNull(androidGradleModel);
      androidGradleModel.setSelectedTestArtifactName(ARTIFACT_UNIT_TEST);
      androidGradleModel.syncSelectedVariantAndTestArtifact(myAndroidFacet);
    }
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        // Make sure all "after sync tasks" are run, for running unit tests.
        assertThat(tasks).containsOnly(qualifiedTaskName(MOCKABLE_ANDROID_JAR),
                                       qualifiedTaskName(PREPARE_UNIT_TEST_DEPENDENCIES),
                                       qualifiedTaskName(SOURCE_GEN),
                                       qualifiedTaskName(COMPILE_UNIT_TEST_JAVA));
        // If using Jack, running :app:compileDebugSources would be a waste of time.
        assertDoesntContain(tasks, COMPILE_JAVA);
        assertEquals(BuildMode.COMPILE_JAVA, getBuildMode());
      }
    });
    myInvoker.compileJava(new Module[] {myModule}, GradleInvoker.TestCompileType.JAVA_TESTS);
  }

  public void testAssemble() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsOnly(qualifiedTaskName(ASSEMBLE));
        assertEquals(BuildMode.ASSEMBLE, getBuildMode());
      }
    });
    myInvoker.assemble(new Module[]{myModule}, GradleInvoker.TestCompileType.NONE);
  }

  public void testAssemble_forAndroidTests() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertThat(tasks).containsOnly(qualifiedTaskName(ASSEMBLE),
                                       qualifiedTaskName(ASSEMBLE_ANDROID_TEST));
        assertEquals(BuildMode.ASSEMBLE, getBuildMode());
      }
    });
    myInvoker.assemble(new Module[]{myModule}, GradleInvoker.TestCompileType.ANDROID_TESTS);
  }

}
