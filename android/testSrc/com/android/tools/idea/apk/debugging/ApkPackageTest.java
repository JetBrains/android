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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

/**
 * Tests for {@link ApkPackage}.
 */
public class ApkPackageTest {
  @Test
  public void addSubpackage() {
    ApkPackage aPackage = new ApkPackage("a", null);
    assertEquals("a", aPackage.getName());
    assertEquals("a", aPackage.getFqn());
    assertNull(aPackage.getParent());

    ApkPackage bPackage = aPackage.addSubpackage("b");
    assertEquals("b", bPackage.getName());
    assertEquals("a.b", bPackage.getFqn());
    assertSame(aPackage, bPackage.getParent());
    assertThat(aPackage.getSubpackages()).containsExactly(bPackage);

    ApkPackage cPackage = bPackage.addSubpackage("c");
    assertEquals("c", cPackage.getName());
    assertEquals("a.b.c", cPackage.getFqn());
    assertSame(bPackage, cPackage.getParent());
    assertThat(bPackage.getSubpackages()).containsExactly(cPackage);
  }

  @Test
  public void addClass() {
    ApkPackage aPackage = new ApkPackage("a", null);
    ApkClass xClass = aPackage.addClass("X");
    assertEquals("X", xClass.getName());
    assertEquals("a.X", xClass.getFqn());
    assertSame(aPackage, xClass.getParent());

    ApkPackage defaultPackage = new ApkPackage("", null);
    ApkClass yClass = defaultPackage.addClass("Y");
    assertEquals("Y", yClass.getName());
    assertEquals("Y", yClass.getFqn());
    assertSame(defaultPackage, yClass.getParent());
  }

  @Test
  public void doSubpackagesHaveClasses() {
    ApkPackage aPackage = new ApkPackage("a", null);
    ApkPackage bPackage = aPackage.addSubpackage("b");
    assertFalse(aPackage.doSubpackagesHaveClasses());
    assertFalse(bPackage.doSubpackagesHaveClasses());

    bPackage.addClass("Class1");
    assertTrue(aPackage.doSubpackagesHaveClasses());
    assertFalse(bPackage.doSubpackagesHaveClasses());
  }

  @Test
  public void equalsAndHashCode() {
    ApkPackage p1 = new ApkPackage("p1", null);
    ApkPackage p2 = new ApkPackage("p2", null);
    // @formatter:off
    EqualsVerifier.forClass(ApkPackage.class).withPrefabValues(ApkPackage.class, p1, p2)
                                             .withIgnoredFields("myClassesByName", "mySubpackagesByName")
                                             .verify();
    // @formatter:on
  }
}