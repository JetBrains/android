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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.android.tools.idea.apk.debugging.ApkClass;
import com.android.tools.idea.apk.debugging.ApkPackage;
import com.android.tools.idea.navigator.nodes.apk.java.DexFileStructure.ApkPackages;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link ApkPackages}.
 */
public class
ApkPackagesTest {
  private ApkPackages myApkPackages;

  @Before
  public void setUp() {
    myApkPackages = new ApkPackages();
  }

  @Test
  public void add() throws Exception {
    myApkPackages.add("a.b.c.Class1");
    myApkPackages.add("a.Class2");
    myApkPackages.add("a.b.Class3");
    myApkPackages.add("Class4");
    myApkPackages.add("x.y.z.Class5");
    myApkPackages.add("a.b.c.d.e.Class6");
    myApkPackages.add("a.b.c.d.x.y.Class7");
    myApkPackages.add("Class8");

    List<ApkPackage> apkPackages = new ArrayList<>(myApkPackages.values());
    assertThat(apkPackages).hasSize(3);

    ApkPackage aPackage = myApkPackages.findPackage("a");
    assertNotNull(aPackage);
    ApkClass class2 = aPackage.findClass("Class2");
    assertNotNull(class2);

    ApkPackage abPackage = aPackage.findSubpackage("b");
    assertNotNull(abPackage);
    ApkClass class3 = abPackage.findClass("Class3");
    assertNotNull(class3);

    ApkPackage abcPackage = abPackage.findSubpackage("c");
    assertNotNull(abcPackage);
    ApkClass class1 = abcPackage.findClass("Class1");
    assertNotNull(class1);

    ApkPackage abcdPackage = abcPackage.findSubpackage("d");
    assertNotNull(abcdPackage);
    assertThat(abcdPackage.getClasses()).isEmpty();

    ApkPackage abcdePackage = abcdPackage.findSubpackage("e");
    assertNotNull(abcdePackage);
    ApkClass class6 = abcdePackage.findClass("Class6");
    assertNotNull(class6);

    ApkPackage abcdxPackage = abcdPackage.findSubpackage("x");
    assertNotNull(abcdxPackage);
    assertThat(abcdxPackage.getClasses()).isEmpty();

    ApkPackage abcdxyPackage = abcdxPackage.findSubpackage("y");
    assertNotNull(abcdxyPackage);
    ApkClass class7 = abcdxyPackage.findClass("Class7");
    assertNotNull(class7);

    ApkPackage defaultPackage = myApkPackages.findPackage("");
    assertNotNull(defaultPackage);
    assertThat(defaultPackage.getClasses()).hasSize(2);
    ApkClass class4 = defaultPackage.findClass("Class4");
    assertNotNull(class4);
    ApkClass class8 = defaultPackage.findClass("Class8");
    assertNotNull(class8);

    ApkPackage xPackage = myApkPackages.findPackage("x");
    assertNotNull(xPackage);
    assertThat(xPackage.getClasses()).isEmpty();

    ApkPackage xyPackage = xPackage.findSubpackage("y");
    assertNotNull(xyPackage);
    assertThat(xyPackage.getClasses()).isEmpty();

    ApkPackage xyzPackage = xyPackage.findSubpackage("z");
    assertNotNull(xyzPackage);
    ApkClass class5 = xyzPackage.findClass("Class5");
    assertNotNull(class5);
  }
}