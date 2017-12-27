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
package com.android.tools.idea.gradle.structure.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class PsModelTest {
  @Test
  public void setModified() throws Exception {
    PsModel parent = new TestModel("parent");
    PsModel child = new TestModel("child", parent);

    assertFalse(child.isModified());
    assertFalse(parent.isModified());

    child.setModified(true);

    assertTrue(child.isModified());
    assertTrue(parent.isModified());

    child.setModified(false);

    assertFalse(child.isModified());
    assertTrue(parent.isModified());
  }

  @Test
  public void testToString() throws Exception {
    PsModel model = new TestModel("testName");
    assertEquals("testName", model.toString());
  }
}