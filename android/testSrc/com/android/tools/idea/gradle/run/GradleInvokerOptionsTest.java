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
package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class GradleInvokerOptionsTest {
  private GradleInvokerOptions.GradleTasksProvider myTasksProvider;

  @Before
  public void setup() {
    myTasksProvider = Mockito.mock(GradleInvokerOptions.GradleTasksProvider.class);
  }

  @Test
  public void testUserGoal() throws Exception {
    GradleInvokerOptions options =
      GradleInvokerOptions.create(true, GradleInvoker.TestCompileType.JAVA_TESTS, null, myTasksProvider, "foo");

    assertEquals(options.tasks, Collections.singletonList("foo"));
    assertTrue("Command line arguments aren't set for user goals", options.commandLineArguments.isEmpty());
  }

  @Test
  public void testUnitTest() throws Exception {
    List<String> tasks = Arrays.asList("compileUnitTest");
    Mockito.when(myTasksProvider.getUnitTestTasks(BuildMode.COMPILE_JAVA)).thenReturn(tasks);

    GradleInvokerOptions options =
      GradleInvokerOptions.create(true, GradleInvoker.TestCompileType.JAVA_TESTS, null, myTasksProvider, null);

    assertEquals(tasks, options.tasks);
    assertTrue("Command line arguments aren't set for unit test tasks", options.commandLineArguments.isEmpty());
  }
}