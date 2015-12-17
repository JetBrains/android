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
package org.jetbrains.android.util;

import com.intellij.testFramework.LightIdeaTestCase;

import static org.jetbrains.android.util.AndroidUtils.isValidAndroidPackageName;
import static org.jetbrains.android.util.AndroidUtils.isValidJavaPackageName;
import static org.jetbrains.android.util.AndroidUtils.validateAndroidPackageName;

public class AndroidUtilsTest extends LightIdeaTestCase {
  public void testIsValidJavaPackageName() {
    assertFalse(isValidJavaPackageName(""));
    assertFalse(isValidJavaPackageName("."));
    assertFalse(isValidJavaPackageName("foo.1bar"));
    assertFalse(isValidJavaPackageName("foo.if"));
    assertFalse(isValidJavaPackageName("foo.new"));

    assertTrue(isValidJavaPackageName("foo"));
    assertTrue(isValidJavaPackageName("foo.bar"));
    assertTrue(isValidJavaPackageName("foo._bar"));
    assertTrue(isValidJavaPackageName("my.p\u00f8"));
    assertTrue(isValidJavaPackageName("foo.f$"));
    assertTrue(isValidJavaPackageName("f_o.ba1r.baz"));
    assertTrue(isValidJavaPackageName("com.example"));
  }

  public void testIsValidAndroidJavaPackage() {
    assertFalse(isValidAndroidPackageName(""));
    assertFalse(isValidAndroidPackageName("foo"));
    assertFalse(isValidAndroidPackageName("if"));
    assertTrue(isValidAndroidPackageName("foo.bar"));
  }

  public void testValidateAndroidPackageName() {
    assertEquals("Package name is missing", validateAndroidPackageName(""));
    assertEquals("The package must have at least one '.' separator", validateAndroidPackageName("if"));
    assertEquals("Package names cannot contain Java keywords like 'if'", validateAndroidPackageName("if.then"));
    assertEquals("The character '_' cannot be the first character in a package segment", validateAndroidPackageName("foo._bar"));
    assertEquals("A digit cannot be the first character in a package segment", validateAndroidPackageName("foo.1bar"));
    assertEquals("The character '\u00f8' is not allowed in Android application package names", validateAndroidPackageName("my.p\u00f8"));
    assertEquals("The character '$' is not allowed in Android application package names", validateAndroidPackageName("foo.bar$"));
    assertEquals("Package segments must be of non-zero length", validateAndroidPackageName(".foo.bar"));
    assertNull(validateAndroidPackageName("foo.bar"));
    assertNull(validateAndroidPackageName("foo.b1.ar_"));
    assertNull(validateAndroidPackageName("Foo.Bar"));
  }

  public void testGetUnqualifiedName() {
    assertEquals("View", AndroidUtils.getUnqualifiedName("android.view.View"));
    assertEquals(null, AndroidUtils.getUnqualifiedName("android.view."));
    assertEquals(null, AndroidUtils.getUnqualifiedName("StringWithoutDots"));
  }
}
