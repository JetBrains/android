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
package com.android.tools.idea.gradle.project.model.ide.android.level2;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.BaseArtifactStub;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link IdeDependencies}.
 */
public class IdeDependenciesTest {
  IdeDependenciesFactory myDependenciesFactory;

  @Before
  public void setup() {
    myDependenciesFactory = new IdeDependenciesFactory();
  }

  @Test
  public void serializable() {
    assertThat(IdeDependenciesImpl.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeDependencies graphs = myDependenciesFactory.create(new BaseArtifactStub(), GradleVersion.parse("2.3.0"));
    byte[] bytes = serialize(graphs);
    Object o = deserialize(bytes);
    assertEquals(graphs, o);
  }
}