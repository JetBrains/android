/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.designer;

import junit.framework.TestCase;

public class FillPolicyTest extends TestCase {
  public void testNone() {
    FillPolicy policy = FillPolicy.get("none");
    assertNotNull(policy);
    assertSame(FillPolicy.NONE, policy);
    assertFalse(policy.fillHorizontally(false));
    assertFalse(policy.fillHorizontally(true));
    assertFalse(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  public void testBoth() {
    FillPolicy policy = FillPolicy.get("both");
    assertNotNull(policy);
    assertSame(FillPolicy.BOTH, policy);
    assertTrue(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertTrue(policy.fillVertically(true));
  }

  public void testWidth() {
    FillPolicy policy = FillPolicy.get("width");
    assertNotNull(policy);
    assertSame(FillPolicy.WIDTH, policy);
    assertTrue(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertFalse(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  public void testHeight() {
    FillPolicy policy = FillPolicy.get("height");
    assertNotNull(policy);
    assertSame(FillPolicy.HEIGHT, policy);
    assertFalse(policy.fillHorizontally(false));
    assertFalse(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertTrue(policy.fillVertically(true));
  }

  public void testOpposite() {
    FillPolicy policy = FillPolicy.get("opposite");
    assertNotNull(policy);
    assertSame(FillPolicy.OPPOSITE, policy);
    assertFalse(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  public void testWidthInVertical() {
    FillPolicy policy = FillPolicy.get("width_in_vertical");
    assertNotNull(policy);
    assertSame(FillPolicy.WIDTH_IN_VERTICAL, policy);
    assertFalse(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertFalse(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  public void testHeightInHorizontal() {
    FillPolicy policy = FillPolicy.get("height_in_horizontal");
    assertNotNull(policy);
    assertSame(FillPolicy.HEIGHT_IN_HORIZONTAL, policy);
    assertFalse(policy.fillHorizontally(false));
    assertFalse(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }
}
