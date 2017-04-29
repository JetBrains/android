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

import com.google.common.collect.Sets;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Tests for {@link IdeModel}.
 */
public class IdeModelTest {
  private ModelCache myModelCache;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
  }

  @Test
  public void copyNewProperty() {
    String copy = IdeModel.copyNewProperty(() -> "Hello", "Hi!");
    assertEquals("Hello", copy);

    copy = IdeModel.copyNewProperty(() -> {
      throw new UnsupportedMethodException("Test");
    }, "Hi!");
    assertEquals("Hi!", copy);
  }

  @Test
  public void copyNewPropertyWithModelCache() {
    Object value = new Object();
    myModelCache.getData().put("key", value);

    Object copy = IdeModel.copyNewProperty(myModelCache, () -> "key", s -> new Object(), null);
    // verify the copy comes from the cache.
    assertSame(value, copy);

    Object defaultValue = new Object();
    copy = IdeModel.copyNewProperty(myModelCache, () -> {
      throw new UnsupportedMethodException("Test");
    }, (Function<String, Object>)s -> new Object(), defaultValue);
    // verify the copy comes the default value.
    assertSame(defaultValue, copy);
  }

  @Test
  public void copyCollection() {
    List<String> original = Arrays.asList("One", "Two", "Three");
    List<String> copy = IdeModel.copy(original, myModelCache, s -> s + "_copied");
    assertThat(copy).containsExactly("One_copied", "Two_copied", "Three_copied");
  }

  @Test
  public void copyMap() {
    Map<String, String> original = new HashMap<>();
    original.put("1", "One");
    original.put("2", "Two");
    original.put("3", "Three");
    Map<String, String> copy = IdeModel.copy(original, myModelCache, s -> s + "_copied");
    assertThat(copy).containsEntry("1", "One_copied");
    assertThat(copy).containsEntry("2", "Two_copied");
    assertThat(copy).containsEntry("3", "Three_copied");
  }

  @Test
  public void copyStringSet() {
    Set<String> original = Sets.newHashSet("1", "2", "3");
    Set<String> copy = IdeModel.copy(original);
    assertThat(copy).isNotSameAs(original);
    assertEquals(original, copy);
  }

  // https://code.google.com/p/android/issues/detail?id=360245
  @Test
  public void withRecursiveModel() {
    RecursiveModel original = new OriginalRecursiveModel();
    IdeRecursiveModel copy = new IdeRecursiveModel(original, myModelCache);
    assertSame(copy, copy.getRecursiveModel());
  }

  private interface RecursiveModel {
    @NotNull
    RecursiveModel getRecursiveModel();
  }

  private static class OriginalRecursiveModel implements RecursiveModel {
    @Override
    @NotNull
    public RecursiveModel getRecursiveModel() {
      return this;
    }
  }

  private static class IdeRecursiveModel extends IdeModel implements RecursiveModel {
    @NotNull private final RecursiveModel myRecursiveModel;

    IdeRecursiveModel(@NotNull RecursiveModel recursiveModel, @NotNull ModelCache modelCache) {
      super(recursiveModel, modelCache);
      //noinspection Convert2Lambda
      myRecursiveModel = modelCache.computeIfAbsent(recursiveModel.getRecursiveModel(), new Function<RecursiveModel, RecursiveModel>() {
        @Override
        public RecursiveModel apply(RecursiveModel recursiveModel) {
          return new IdeRecursiveModel(recursiveModel, modelCache);
        }
      });
    }

    @Override
    @NotNull
    public RecursiveModel getRecursiveModel() {
      return myRecursiveModel;
    }
  }
}