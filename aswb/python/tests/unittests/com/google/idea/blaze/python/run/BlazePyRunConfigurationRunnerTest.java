/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazePyRunConfigurationRunner}. */
@RunWith(JUnit4.class)
public class BlazePyRunConfigurationRunnerTest {

  @Test
  public void testMultipleOutputFiles() {
    Label target = Label.create("//path/to/package:SomeTest");
    ImmutableList<File> outputFiles =
        ImmutableList.of(
            new File("blaze-bin/path/to/package/SomeTest.run.py"),
            new File("blaze-bin/path/to/package/SomeTest"));
    assertThat(BlazePyRunConfigurationRunner.findExecutable(target, outputFiles))
        .isEqualTo(new File("blaze-bin/path/to/package/SomeTest"));
  }
}
