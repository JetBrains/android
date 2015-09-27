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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link CompactNotation}.
 */
@RunWith(Parameterized.class)
public class CompactNotationTest {
  @NotNull private String myNotationValue;
  @Nullable private ExternalDependencySpec myDependencySpec;

  public CompactNotationTest(@NotNull String notationValue, @Nullable ExternalDependencySpec dependencySpec) {
    myNotationValue = notationValue;
    myDependencySpec = dependencySpec;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][]{
      {"org.gradle.test.classifiers:service:1.0:jdk15@jar",
        new ExternalDependencySpec("service", "org.gradle.test.classifiers", "1.0", "jdk15", "jar")},

      {"org.groovy:groovy:2.2.0@jar", new ExternalDependencySpec("groovy", "org.groovy", "2.2.0", null, "jar")},
      {"org.mockito:mockito:1.9.0-rc1", new ExternalDependencySpec("mockito", "org.mockito", "1.9.0-rc1", null, null)},
      {"org.hibernate:hibernate:3.1", new ExternalDependencySpec("hibernate", "org.hibernate", "3.1", null, null)},
      {"", null}
    });
  }

  @Test
  public void parseCompactNotation() {
    ExternalDependencySpec spec = CompactNotation.parse(myNotationValue);
    assertEquals(myDependencySpec, spec);
    if (spec != null) {
      assertEquals(myNotationValue, spec.toString());
    }
  }
}