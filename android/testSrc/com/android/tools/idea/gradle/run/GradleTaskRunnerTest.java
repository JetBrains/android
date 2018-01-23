/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.TestBuildAction;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;

public class GradleTaskRunnerTest extends AndroidGradleTestCase {

  public void testExecuteTask() throws InterruptedException {
    Project project = getProject();

    GradleBuildInvokerStub buildInvoker = new GradleBuildInvokerStub(project);
    IdeComponents.replaceService(project, GradleBuildInvoker.class, buildInvoker);

    GradleTaskRunner runner = GradleTaskRunner.newRunner(project);

    CountDownLatch countDownLatch = new CountDownLatch(1);
    ForkJoinPool.commonPool().execute(() -> {
      try {
        runner.run(ArrayListMultimap.create(), BuildMode.ASSEMBLE, Collections.emptyList());
        countDownLatch.countDown();
      }
      catch (InvocationTargetException | InterruptedException e) {
        e.printStackTrace();
      }
    });

    TimeoutUtil.sleep(1000);
    int completed = buildInvoker.complete(new GradleInvocationResult(Collections.emptyList(), Collections.emptyList(), null));
    assertEquals(0, completed); // No tasks should be executed until we process the event queue
    assertEquals(1, countDownLatch.getCount()); // The runner should still be blocked

    UIUtil.dispatchAllInvocationEvents();

    completed = buildInvoker.complete(new GradleInvocationResult(Collections.emptyList(), Collections.emptyList(), null));
    assertEquals(1, completed);
    countDownLatch.await(5, TimeUnit.SECONDS);
  }

  public void testBuildActionRunner() throws Exception {
    loadSimpleApplication();

    GradleTaskRunner.DefaultGradleTaskRunner runner = new GradleTaskRunner.DefaultGradleTaskRunner(getProject(), new TestBuildAction());

    ForkJoinTask<Void> task = ForkJoinPool.commonPool().submit(() -> {
      ListMultimap<Path, String> tasks = ArrayListMultimap.create();
      tasks.put(new File(getProject().getBasePath()).toPath(), "assembleDebug");
      runner.run(tasks, BuildMode.ASSEMBLE, Collections.emptyList());
      return null;
    });
    UIUtil.dispatchAllInvocationEvents();

    task.get(2, TimeUnit.MINUTES);
    assertEquals("test", runner.getModel());
  }

  private static class GradleBuildInvokerStub extends GradleBuildInvoker {
    GradleBuildInvokerStub(@NotNull Project project) {
      super(project, mock(FileDocumentManager.class));
    }

    @Override
    public void executeTasks(@NotNull Request request) {
    }

    /**
     * Call all the {@link com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.AfterGradleInvocationTask}. This call does
     * not remove them from the list.
     */
    int complete(@NotNull GradleInvocationResult result) {
      int completed = 0;
      for (AfterGradleInvocationTask task : getAfterInvocationTasks()) {
        task.execute(result);
        completed++;
      }

      return completed;
    }
  }
}