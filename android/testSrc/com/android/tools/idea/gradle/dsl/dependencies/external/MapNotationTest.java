/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.dependencies.external;

import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link MapNotation}.
 */
@RunWith(Parameterized.class)
public class MapNotationTest {
  @NotNull private Map<String, String> myNamedArguments;
  @Nullable private ExternalDependencySpec myDependencySpec;

  public MapNotationTest(@NotNull Map<String, String> namedArguments, @Nullable ExternalDependencySpec dependencySpec) {
    myNamedArguments = namedArguments;
    myDependencySpec = dependencySpec;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][]{
      {ImmutableMap.builder().put("name", "service")
                             .put("group", "org.gradle.test.classifiers")
                             .put("version", "1.0")
                             .put("classifier", "jdk15")
                             .put("ext", "jar")
                             .build(),
        new ExternalDependencySpec("service", "org.gradle.test.classifiers", "1.0", "jdk15", "jar")},

      {ImmutableMap.builder().put("name", "groovy")
                             .put("group", "org.groovy")
                             .put("version", "2.2.0")
                             .put("ext", "jar")
                             .build(),
        new ExternalDependencySpec("groovy", "org.groovy", "2.2.0", null, "jar")},
    });
  }

  @Test
  public void parseCompactNotation() {
    ExternalDependencySpec spec = MapNotation.parse(myNamedArguments);
    assertNotNull(spec);
    assertEquals(myDependencySpec, spec);
  }
}