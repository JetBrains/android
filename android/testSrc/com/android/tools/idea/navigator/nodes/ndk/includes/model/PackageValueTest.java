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

import java.util.ArrayList;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class PackageValueTest {
  @Test
  public void testNoSharedCommonFolder() {
    PackageKey key = new PackageKey(PackageType.CDepPackage, "package name", new File("."));
    List<SimpleIncludeValue> includes = new ArrayList<>();
    includes.add(new SimpleIncludeValue(PackageType.CDepPackage, PackageType.CDepPackage.myDescription, "package name", "x1", new File("."), new File(".")));
    includes.add(new SimpleIncludeValue(PackageType.CDepPackage, PackageType.CDepPackage.myDescription, "package name", "x2", new File("."), new File(".")));
    PackageValue value = new PackageValue(key, "CDep packages", includes);
    assertThat(value.getDescriptiveText()).isEqualTo("2 include paths");
    assertThat(value.toString()).isEqualTo("package name (CDep Packages, 2 include paths)");
  }

  @Test
  public void testSharedCommonFolder() {
    PackageKey key = new PackageKey(PackageType.CDepPackage, "package name", new File("."));
    List<SimpleIncludeValue> includes = new ArrayList<>();
    includes.add(new SimpleIncludeValue(PackageType.CDepPackage, PackageType.CDepPackage.myDescription, "package name", "root-folder/x1", new File("."), new File(".")));
    includes.add(new SimpleIncludeValue(PackageType.CDepPackage, PackageType.CDepPackage.myDescription, "package name", "root-folder/x2", new File("."), new File(".")));
    PackageValue value = new PackageValue(key, "CDep Packages", includes);
    assertThat(value.getDescriptiveText()).isEqualTo("root-folder");
    assertThat(value.toString()).isEqualTo("package name (CDep Packages, root-folder)");
  }

  @Test
  public void testEqualsHash() {
    EqualsVerifier.forClass(PackageValue.class).verify();
  }
}