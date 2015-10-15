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

/**
 * Tests for {@link GradleInvoker}.
 */
public class GradleInvokerTest extends IdeaTestCase {
  private String myModuleGradlePath;
  private AndroidFacet myAndroidFacet;
  private GradleInvoker myInvoker;

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
  }

  @Override
  protected void checkForSettingsDamage(@NotNull List<Throwable> exceptions) { }

  public void testCleanProject() throws Exception {
    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals("assembleTranslate", tasks.get(0));
        assertEquals(BuildMode.ASSEMBLE_TRANSLATE, getBuildMode());
      }
    });
    myInvoker.assembleTranslate();
  }

  public void testAssembleTranslate() {
    final String taskName = "sourceGen";
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES = Sets.newHashSet(taskName);

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertEquals(2, tasks.size());
        assertEquals("clean", tasks.get(0));
        assertEquals(myModuleGradlePath + GRADLE_PATH_SEPARATOR + taskName, tasks.get(1));
        assertEquals(BuildMode.CLEAN, getBuildMode());
      }
    });
    myInvoker.cleanProject();
  }

  public void testGenerateSources() throws Exception {
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES = Sets.newHashSet("sourceGen", "testSourceGen");

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertEquals(2, tasks.size());
        assertContainsElements(tasks, myModuleGradlePath + GRADLE_PATH_SEPARATOR + "sourceGen");
        assertContainsElements(tasks, myModuleGradlePath + GRADLE_PATH_SEPARATOR + "testSourceGen");
        assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
      }
    });
    myInvoker.generateSources();
  }

  public void testGenerateSourcesWithoutTestSourceGen() throws Exception {
    myAndroidFacet.getProperties().AFTER_SYNC_TASK_NAMES = Sets.newHashSet("sourceGen");

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals(myModuleGradlePath + GRADLE_PATH_SEPARATOR + "sourceGen", tasks.get(0));
        assertEquals(BuildMode.SOURCE_GEN, getBuildMode());
      }
    });
    myInvoker.generateSources();
  }

  public void testCompileJava() throws Exception {
    final String taskName = "compileJava";
    final String testTaskName = "compileTestJava";
    myAndroidFacet.getProperties().COMPILE_JAVA_TASK_NAME = taskName;
    myAndroidFacet.getProperties().COMPILE_JAVA_TEST_TASK_NAME = testTaskName;

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertEquals(2, tasks.size());
        assertEquals(qualifiedTaskName(taskName), tasks.get(0));
        assertEquals(qualifiedTaskName(testTaskName), tasks.get(1));
        assertEquals(BuildMode.COMPILE_JAVA, getBuildMode());
      }
    });
    myInvoker.compileJava(new Module[] { myModule }, GradleInvoker.TestCompileType.NONE);
  }

  public void testMake() throws Exception {
    final String taskName = "assemble";
    myAndroidFacet.getProperties().ASSEMBLE_TASK_NAME = taskName;

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals(qualifiedTaskName(taskName), tasks.get(0));
        assertEquals(BuildMode.ASSEMBLE, getBuildMode());
      }
    });
    myInvoker.assemble(new Module[]{myModule}, GradleInvoker.TestCompileType.NONE);
  }

  public void testRebuild() throws Exception {
    final String taskName = "assemble";
    myAndroidFacet.getProperties().COMPILE_JAVA_TASK_NAME = taskName;

    myInvoker.addBeforeGradleInvocationTask(new GradleInvoker.BeforeGradleInvocationTask() {
      @Override
      public void execute(@NotNull List<String> tasks) {
        assertEquals(2, tasks.size());
        assertEquals("clean", tasks.get(0));
        assertEquals(qualifiedTaskName(taskName), tasks.get(1));
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
