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

import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.VariantStub;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static com.android.tools.idea.gradle.project.model.ide.android.CopyVerification.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

/**
 * Tests for {@link IdeVariant}.
 */
public class IdeVariantTest {
  private ModelCache myModelCache;
  private GradleVersion myGradleVersion;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
    myGradleVersion = GradleVersion.parse("3.2");
  }

  @Test
  public void serializable() {
    assertThat(IdeVariant.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeVariant apiVersion = new IdeVariant(new VariantStub(), myModelCache, myGradleVersion);
    byte[] bytes = serialize(apiVersion);
    Object o = deserialize(bytes);
    assertEquals(apiVersion, o);
  }

  @Test
  public void constructor() throws Throwable {
    Variant original = new VariantStub();
    assertEqualsOrSimilar(original, new IdeVariant(original, myModelCache, myGradleVersion));
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(IdeVariant.class)
      .withCachedHashCode("myHashCode", "calculateHashCode", null)
      .suppress(Warning.NO_EXAMPLE_FOR_CACHED_HASHCODE)
      .verify();
  }
}