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
package com.android.tools.idea.navigator.nodes.ndk.includes.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;

public class TestPackageKey {
  private static void assertAreEqual(@NotNull PackageKey lhs, @NotNull Object rhs) {
    assertThat(lhs).isEqualTo(rhs);
    assertThat(lhs.hashCode()).isEqualTo(rhs.hashCode());
  }

  private static void assertAreNotEqual(@NotNull PackageKey lhs, @Nullable Object rhs) {
    assertThat(lhs).isNotEqualTo(rhs);
    if (rhs != null) {
      assertThat(lhs.hashCode()).isNotEqualTo(rhs.hashCode());
    }
  }

  @Test
  public void testEquals() throws Exception {
    assertAreEqual(new PackageKey(PackageType.CDepPackage, "a", new File(".")),
                   new PackageKey(PackageType.CDepPackage, "a", new File(".")));
  }

  @Test
  public void testEqualsSameInstance() throws Exception {
    PackageKey instance = new PackageKey(PackageType.CDepPackage, "a", new File("."));
    assertAreEqual(instance, instance);
  }

  @Test
  public void testNotEqualsDifferentPackage() throws Exception {
    assertAreNotEqual(new PackageKey(PackageType.CDepPackage, "a", new File(".")),
                      new PackageKey(PackageType.CocosThirdPartyPackage, "a", new File(".")));
  }

  @Test
  public void testNotEqualsDifferentPath() throws Exception {
    assertAreNotEqual(new PackageKey(PackageType.CDepPackage, "a", new File("path1")),
                      new PackageKey(PackageType.CDepPackage, "a", new File("path2")));
  }

  @Test
  public void testNotEqualsNullRhs() throws Exception {
    assertAreNotEqual(new PackageKey(PackageType.CDepPackage, "a", new File("path1")), null);
  }

  @Test
  public void testNotEqualsStringRhs() throws Exception {
    assertAreNotEqual(new PackageKey(PackageType.CDepPackage, "a", new File("path1")), "bob");
  }

  @Test
  public void testNotEqualsDifferentPackageName() throws Exception {
    assertAreNotEqual(new PackageKey(PackageType.CDepPackage, "a", new File(".")),
                   new PackageKey(PackageType.CDepPackage, "b", new File(".")));
  }
}