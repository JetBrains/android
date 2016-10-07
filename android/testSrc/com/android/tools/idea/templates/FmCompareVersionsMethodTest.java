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
package com.android.tools.idea.templates;

import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import junit.framework.TestCase;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newArrayList;

/**
 * Tests for {@link FmCompareVersionsMethod}.
 */
public class FmCompareVersionsMethodTest extends TestCase {
  @SuppressWarnings("rawtypes")
  private void check(String lhs, String rhs, int expected) throws Exception {
    FmCompareVersionsMethod method = new FmCompareVersionsMethod();
    List list = newArrayList(new SimpleScalar(lhs), new SimpleScalar(rhs));
    Number result = ((SimpleNumber)method.exec(list)).getAsNumber();
    assertEquals(expected, Integer.signum(result.intValue()));
  }

  public void testComparison() throws Exception {
    check("1.2.3", "1.1.0", 1);
    check("1.1.0", "1.1.0", 0);
    check("1.0.0", "1.1.0", -1);
    check("1.3.0-alpha4", "1.1.0", 1);
    check("2.2.0-dev", "1.1.0", 1);
  }
}
