/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.experiments;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for the system property experiment loader. */
@RunWith(JUnit4.class)
public class SystemPropertyExperimentLoaderTest {

  private static final String EXPERIMENT = "test.foo";
  private static final String PROPERTY = "blaze.experiment.test.foo";
  private static final String VALUE = "true";

  @Before
  public void setUp() {
    System.setProperty(PROPERTY, VALUE);
  }

  @After
  public void tearDown() {
    System.clearProperty(PROPERTY);
  }

  @Test
  public void testGetExperiment() {
    ExperimentLoader loader = new SystemPropertyExperimentLoader();
    assertThat(loader.getExperiments()).containsEntry(EXPERIMENT, VALUE);
  }
}
