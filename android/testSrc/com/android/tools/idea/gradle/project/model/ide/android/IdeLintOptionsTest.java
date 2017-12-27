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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.LintOptions;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.LintOptionsStub;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.*;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link IdeLintOptions}.
 */
public class IdeLintOptionsTest {
  private ModelCache myModelCache;
  private GradleVersion myModelVersion;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
    myModelVersion = GradleVersion.parse("2.4.0");
  }

  @Test
  public void serializable() {
    assertThat(IdeLintOptions.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeLintOptions lintOptions = new IdeLintOptions(new LintOptionsStub(), myModelCache, myModelVersion);
    byte[] bytes = serialize(lintOptions);
    Object o = deserialize(bytes);
    assertEquals(lintOptions, o);
  }

  @Test
  public void constructor() throws Throwable {
    LintOptions original = new LintOptionsStub();
    IdeLintOptions copy = new IdeLintOptions(original, myModelCache, myModelVersion);
    assertEqualsOrSimilar(original, copy);
    verifyUsageOfImmutableCollections(copy);
  }

  @Test
  public void equalsAndHashCode() {
    createEqualsVerifier(IdeLintOptions.class).verify();
  }
}