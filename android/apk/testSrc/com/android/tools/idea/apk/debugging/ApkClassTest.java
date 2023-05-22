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
package com.android.tools.idea.apk.debugging;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link ApkClass}.
 */
public class ApkClassTest {
  @Test
  public void getFqn() {
    ApkPackage p1 = new ApkPackage("p1", null);
    ApkPackage p2 = new ApkPackage("p2", p1);

    ApkClass c = new ApkClass("c", p2);
    assertEquals("p1.p2.c", c.getFqn());
  }

  @Test
  public void equalsAndHashCode() {
    ApkPackage p1 = new ApkPackage("p1", null);
    ApkPackage p2 = new ApkPackage("p2", null);
    EqualsVerifier.forClass(ApkClass.class).withPrefabValues(ApkPackage.class, p1, p2).verify();
  }
}