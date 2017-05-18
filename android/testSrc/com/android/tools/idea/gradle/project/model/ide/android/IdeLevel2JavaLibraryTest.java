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

import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.Level2JavaLibraryStub;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static com.android.tools.idea.gradle.project.model.ide.android.CopyVerification.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for class {@link IdeLevel2JavaLibrary}.
 */
public class IdeLevel2JavaLibraryTest {
  private ModelCache myModelCache;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
  }

  @Test
  public void serializable() {
    assertThat(IdeLevel2JavaLibrary.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeLevel2JavaLibrary javaLibrary = new IdeLevel2JavaLibrary(createStub(), myModelCache);
    byte[] bytes = serialize(javaLibrary);
    Object o = deserialize(bytes);
    assertEquals(javaLibrary, o);
  }

  @Test
  public void constructor() throws Throwable {
    Library original = createStub();
    assertEqualsOrSimilar(original, new IdeLevel2JavaLibrary(original, myModelCache));
  }

  @NotNull
  private static Library createStub() {
    return new Level2JavaLibraryStub();
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(IdeLevel2JavaLibrary.class).withRedefinedSuperclass()
      .withCachedHashCode("myHashCode", "calculateHashCode", null)
      .suppress(Warning.NO_EXAMPLE_FOR_CACHED_HASHCODE)
      .verify();
  }
}
