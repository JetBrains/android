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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    myModuleGradlePath = SdkConstants.GRADLE_PATH_SEPARATOR + myModule.getName();
    myInvoker = new GradleInvoker(myProject);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FacetManager facetManager = FacetManager.getInstance(myModule);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          model.addFacet(facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null));
          model.addFacet(facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null));
        } finally {
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

  public void testCleanProject() throws Exception {
    myInvoker.cleanProject(new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals("clean", tasks.get(0));
        assertEquals(BuildMode.CLEAN, Projects.getBuildModeFrom(myProject));
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        assertNull(Projects.getBuildModeFrom(myProject));
      }
    });
  }

  public void testGenerateSources() throws Exception {
    final String taskName = "sourceGen";
    myAndroidFacet.getProperties().SOURCE_GEN_TASK_NAME = taskName;

    myInvoker.generateSources(new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals(myModuleGradlePath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName, tasks.get(0));
        assertEquals(BuildMode.SOURCE_GEN, Projects.getBuildModeFrom(myProject));
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        assertNull(Projects.getBuildModeFrom(myProject));
      }
    });
  }

  public void testCompileJava() throws Exception {
    final String taskName = "compileJava";
    myAndroidFacet.getProperties().COMPILE_JAVA_TASK_NAME = taskName;

    myInvoker.compileJava(new MyDataContext(), new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals(qualifiedTaskName(taskName), tasks.get(0));
        assertEquals(BuildMode.COMPILE_JAVA, Projects.getBuildModeFrom(myProject));
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        assertNull(Projects.getBuildModeFrom(myProject));
      }
    });
  }

  public void testMake() throws Exception {
    final String taskName = "assemble";
    myAndroidFacet.getProperties().ASSEMBLE_TASK_NAME = taskName;

    myInvoker.make(new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals(qualifiedTaskName(taskName), tasks.get(0));
        assertEquals(BuildMode.MAKE, Projects.getBuildModeFrom(myProject));
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        assertNull(Projects.getBuildModeFrom(myProject));
      }
    }, null);
  }

  public void testMakeWithDataContext() throws Exception {
    final String taskName = "assemble";
    myAndroidFacet.getProperties().ASSEMBLE_TASK_NAME = taskName;

    myInvoker.make(new MyDataContext(), new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
        assertEquals(1, tasks.size());
        assertEquals(qualifiedTaskName(taskName), tasks.get(0));
        assertEquals(BuildMode.MAKE, Projects.getBuildModeFrom(myProject));
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        assertNull(Projects.getBuildModeFrom(myProject));
      }
    }, null);
  }

  public void testRebuild() throws Exception {
    final String taskName = "assemble";
    myAndroidFacet.getProperties().ASSEMBLE_TASK_NAME = taskName;

    myInvoker.rebuild(new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
        assertEquals(2, tasks.size());
        assertEquals("clean", tasks.get(0));
        assertEquals(qualifiedTaskName(taskName), tasks.get(1));
        assertEquals(BuildMode.REBUILD, Projects.getBuildModeFrom(myProject));
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        assertNull(Projects.getBuildModeFrom(myProject));
      }
    });
  }

  @NotNull
  private String qualifiedTaskName(@NotNull String taskName) {
    return myModuleGradlePath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName;
  }

  private class MyDataContext implements DataContext {
    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (LangDataKeys.MODULE_CONTEXT_ARRAY.getName().equals(dataId)) {
        return new Module[]{myModule};
      }
      return null;
    }
  }
}
