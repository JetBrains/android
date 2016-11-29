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

import junit.framework.TestCase;

public class ResizePolicyTest extends TestCase {
  public void testNone() {
    ResizePolicy policy = ResizePolicy.get("none");
    assertNotNull(policy);
    assertSame(ResizePolicy.none(), policy);
    assertFalse(policy.topAllowed());
    assertFalse(policy.bottomAllowed());
    assertFalse(policy.leftAllowed());
    assertFalse(policy.rightAllowed());
    assertFalse(policy.isAspectPreserving());
  }

  public void testFull() {
    ResizePolicy policy = ResizePolicy.get("full");
    assertNotNull(policy);
    assertSame(ResizePolicy.full(), policy);
    assertTrue(policy.topAllowed());
    assertTrue(policy.bottomAllowed());
    assertTrue(policy.leftAllowed());
    assertTrue(policy.rightAllowed());
    assertFalse(policy.isAspectPreserving());
  }

  public void testHorizontal() {
    ResizePolicy policy = ResizePolicy.get("horizontal");
    assertNotNull(policy);
    assertSame(ResizePolicy.horizontal(), policy);
    assertFalse(policy.topAllowed());
    assertFalse(policy.bottomAllowed());
    assertTrue(policy.leftAllowed());
    assertTrue(policy.rightAllowed());
    assertFalse(policy.isAspectPreserving());
  }

  public void testVertical() {
    ResizePolicy policy = ResizePolicy.get("vertical");
    assertNotNull(policy);
    assertSame(ResizePolicy.vertical(), policy);
    assertTrue(policy.topAllowed());
    assertTrue(policy.bottomAllowed());
    assertFalse(policy.leftAllowed());
    assertFalse(policy.rightAllowed());
    assertFalse(policy.isAspectPreserving());
  }

  public void testScaled() {
    ResizePolicy policy = ResizePolicy.get("scaled");
    assertNotNull(policy);
    assertSame(ResizePolicy.scaled(), policy);
    assertTrue(policy.topAllowed());
    assertTrue(policy.bottomAllowed());
    assertTrue(policy.leftAllowed());
    assertTrue(policy.rightAllowed());
    assertTrue(policy.isAspectPreserving());
  }
}