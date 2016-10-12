/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui;

import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.google.common.truth.Truth.assertThat;


public class ExpensiveTaskTest {
  @Test
  public void expensiveTaskMethodsAreCalledInOrder() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    final boolean[] calledOnStarting = {false};
    final boolean[] calledDoBackgroundWork = {false};
    final boolean[] calledOnFinished = {false};

    ExpensiveTask.Runner taskRunner = new ExpensiveTask.Runner();
    ExpensiveTask task = new ExpensiveTask() {
      @Override
      public void onStarting() {
        calledOnStarting[0] = true;
        assertThat(calledDoBackgroundWork[0]).isFalse();
        assertThat(calledOnFinished[0]).isFalse();
      }

      @Override
      public void doBackgroundWork() throws Exception {
        calledDoBackgroundWork[0] = true;
        assertThat(calledOnStarting[0]).isTrue();
        assertThat(calledOnFinished[0]).isFalse();
      }

      @Override
      public void onFinished() {
        calledOnFinished[0] = true;
        assertThat(calledOnStarting[0]).isTrue();
        assertThat(calledDoBackgroundWork[0]).isTrue();

        latch.countDown();
      }
    };

    taskRunner.setTask(task);
    latch.await();

    assertThat(calledOnStarting[0]).isTrue();
    assertThat(calledDoBackgroundWork[0]).isTrue();
    assertThat(calledOnFinished[0]).isTrue();
  }

  @Test
  public void expensiveTaskOnFinishedOnlyCalledIfNotInterrupted() throws Exception {
    CountDownLatch firstTaskStartedLatch = new CountDownLatch(1);
    CountDownLatch firstTaskFinishLatch = new CountDownLatch(1);
    CountDownLatch secondTaskLatch = new CountDownLatch(1);

    ExpensiveTask.Runner taskRunner = new ExpensiveTask.Runner();
    ExpensiveTask firstTask = new ExpensiveTask() {
      @Override
      public void doBackgroundWork() throws Exception {
        firstTaskStartedLatch.countDown();
        firstTaskFinishLatch.await();
      }

      @Override
      public void onFinished() {
        Assert.fail("Expensive task not cancelled as expected");
      }
    };

    final boolean[] onSecondTaskStarted = {false};
    ExpensiveTask secondTask = new ExpensiveTask() {
      @Override
      public void onStarting() {
        // Verify that the logic starting the new task is called correctly by the previous,
        // aborted task.
        onSecondTaskStarted[0] = true;
      }

      @Override
      public void doBackgroundWork() throws Exception {
      }

      @Override
      public void onFinished() {
        secondTaskLatch.countDown();
      }
    };

    taskRunner.setTask(firstTask);
    firstTaskStartedLatch.await(); // Make sure the first task starts
    taskRunner.setTask(secondTask);
    firstTaskFinishLatch.countDown(); // This will release the first task
    secondTaskLatch.await();

    assertThat(onSecondTaskStarted[0]).isTrue();
  }

  @Test
  public void intermediateTasksAreSkipped() throws Exception {

    CountDownLatch firstTaskStartedLatch = new CountDownLatch(1);
    CountDownLatch firstTaskFinishLatch = new CountDownLatch(1);
    CountDownLatch lastTaskLatch = new CountDownLatch(1);

    ExpensiveTask.Runner taskRunner = new ExpensiveTask.Runner();
    ExpensiveTask firstTask = new ExpensiveTask() {
      @Override
      public void doBackgroundWork() throws Exception {
        firstTaskStartedLatch.countDown();
        firstTaskFinishLatch.await();
      }

      @Override
      public void onFinished() {
      }
    };

    List<ExpensiveTask> intermediateTasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      intermediateTasks.add(new ExpensiveTask() {
        @Override
        public void onStarting() {
          Assert.fail("Intermediate task should not have started");
        }

        @Override
        public void doBackgroundWork() throws Exception {

        }

        @Override
        public void onFinished() {

        }
      });
    }

    ExpensiveTask lastTask = new ExpensiveTask() {
      @Override
      public void doBackgroundWork() throws Exception {
      }

      @Override
      public void onFinished() {
        lastTaskLatch.countDown();
      }
    };

    taskRunner.setTask(firstTask);
    firstTaskStartedLatch.await(); // Make sure first task starts
    intermediateTasks.forEach(taskRunner::setTask);

    taskRunner.setTask(lastTask);
    firstTaskFinishLatch.countDown(); // This will release the first task
    lastTaskLatch.await(); // Verify the last task finishes as expected
  }

  @Test
  public void canCancelTasks() throws Exception {

    CountDownLatch firstTaskStartedLatch = new CountDownLatch(1);
    CountDownLatch firstTaskFinishLatch = new CountDownLatch(1);

    ExpensiveTask.Runner taskRunner = new ExpensiveTask.Runner();
    ExpensiveTask firstTask = new ExpensiveTask() {
      @Override
      public void doBackgroundWork() throws Exception {
        firstTaskStartedLatch.countDown();
        firstTaskFinishLatch.await();
      }

      @Override
      public void onFinished() {
        Assert.fail("Expensive task not cancelled as expected");
      }
    };

    ExpensiveTask secondTask = new ExpensiveTask() {
      @Override
      public void onStarting() {
        Assert.fail("Expensive task not cancelled as expected");
      }

      @Override
      public void doBackgroundWork() throws Exception {

      }

      @Override
      public void onFinished() {
      }
    };

    taskRunner.setTask(firstTask);
    firstTaskStartedLatch.await();

    taskRunner.setTask(secondTask);
    taskRunner.cancel();
    firstTaskFinishLatch.countDown();
  }
}