/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.pom.java.LanguageLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link JavaLanguageLevelSection}. */
@RunWith(JUnit4.class)
public class JavaLanguageLevelSectionTest {

  @Test
  public void testParseLanguageLevels() {
    int minLevelToTest = 5;
    int maxLevelToTest = LanguageLevel.HIGHEST.toJavaVersion().feature;
    for (int i = minLevelToTest; i <= maxLevelToTest; i++) {
      LanguageLevel level = JavaLanguageLevelSection.getLanguageLevel(i, null);
      assertThat(level).isNotNull();
      assertThat(level.toJavaVersion().feature).isEqualTo(i);
    }
  }
}
