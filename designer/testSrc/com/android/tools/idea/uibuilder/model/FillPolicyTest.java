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
package com.android.tools.idea.uibuilder.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FillPolicyTest {
  @Test
  public void testNone() {
    FillPolicy policy = FillPolicy.NONE;
    assertFalse(policy.fillHorizontally(false));
    assertFalse(policy.fillHorizontally(true));
    assertFalse(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  @Test
  public void testBoth() {
    FillPolicy policy = FillPolicy.BOTH;
    assertTrue(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertTrue(policy.fillVertically(true));
  }

  @Test
  public void testWidth() {
    FillPolicy policy = FillPolicy.WIDTH;
    assertTrue(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertFalse(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  @Test
  public void testHeight() {
    FillPolicy policy = FillPolicy.HEIGHT;
    assertFalse(policy.fillHorizontally(false));
    assertFalse(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertTrue(policy.fillVertically(true));
  }

  @Test
  public void testOpposite() {
    FillPolicy policy = FillPolicy.OPPOSITE;
    assertFalse(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  @Test
  public void testWidthInVertical() {
    FillPolicy policy = FillPolicy.WIDTH_IN_VERTICAL;
    assertFalse(policy.fillHorizontally(false));
    assertTrue(policy.fillHorizontally(true));
    assertFalse(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }

  @Test
  public void testHeightInHorizontal() {
    FillPolicy policy = FillPolicy.HEIGHT_IN_HORIZONTAL;
    assertFalse(policy.fillHorizontally(false));
    assertFalse(policy.fillHorizontally(true));
    assertTrue(policy.fillVertically(false));
    assertFalse(policy.fillVertically(true));
  }
}