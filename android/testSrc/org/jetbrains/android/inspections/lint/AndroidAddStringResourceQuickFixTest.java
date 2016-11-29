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
package org.jetbrains.android.inspections.lint;

import junit.framework.Assert;
import org.junit.Test;

public class AndroidAddStringResourceQuickFixTest {
  @Test
  public void testResourceNameSimple() throws Exception {
    doTest("Just simple string", "just_simple_string");
  }

  @Test
  public void testResourceNameSurroundingSpaces() throws Exception {
    doTest(" Just a simple string ", "just_a_simple_string");
  }

  @Test
  public void testResourceNameWithDigits() throws Exception {
    doTest("A string with 31337 number", "a_string_with_31337_number");
  }

  @Test
  public void testResourceNameShouldNotStartWithNumber() throws Exception {
    doTest("100 things", "_100_things");
  }

  private static void doTest(String resourceValue, String expectedName) {
    Assert.assertEquals(expectedName, AndroidAddStringResourceQuickFix.buildResourceName(resourceValue));
  }
}