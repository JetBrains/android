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
package com.android.tools.idea.apk.viewer.dex;

import com.android.tools.apk.analyzer.dex.PackageTreeCreator;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import com.android.tools.apk.analyzer.dex.tree.DexPackageNode;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.apk.analyzer.dex.DexFiles.getDexFile;
import static com.google.common.truth.Truth.assertThat;

public class GenerateProguardKeepRuleActionTest {
  private DexElementNode myPackageTree;
  private Map<Path, DexBackedDexFile> myDexMap;
  private DexBackedDexFile myDexFile;

  @Before
  public void setUp() throws IOException {
    Path path = getDexPath("Test.dex");
    myDexFile = getTestDexFile(path);
    myDexMap = new HashMap<>();
    myDexMap.put(path, myDexFile);
    myPackageTree = new PackageTreeCreator(null, false).constructPackageTree(myDexMap);
  }

  public static Path getDexPath(String s) {
    return Paths.get(AndroidTestBase.getTestDataPath(), "apk/" + s);
  }

  @NotNull
  public static DexBackedDexFile getTestDexFile(@NotNull Path dexPath) throws IOException {
    return getDexFile(Files.readAllBytes(dexPath));
  }

  @Test
  public void canGenerateRule_package_nofqn() {
    assertThat(GenerateProguardKeepRuleAction.canGenerateRule(new DexPackageNode("root", null))).isFalse();
  }

  @Test
  public void canGenerateRule_package() {
    assertThat(GenerateProguardKeepRuleAction.canGenerateRule(myPackageTree)).isFalse();
  }

  @Test
  public void canGenerateRule_class() {
    DexElementNode node = getNode(myPackageTree, "Test");
    assertThat(node).isNotNull();
    assertThat(GenerateProguardKeepRuleAction.canGenerateRule(node)).isTrue();
  }

  @Test
  public void canGenerateRule_method() {
    DexElementNode node = getNode(myPackageTree, "Test.<init>()");
    assertThat(node).isNotNull();
    assertThat(GenerateProguardKeepRuleAction.canGenerateRule(node)).isTrue();
  }

  @Test
  public void getKeepRule_class() {
    DexElementNode node = getNode(myPackageTree, "Test.<init>()");
    assertThat(node).isNotNull();
    String code = GenerateProguardKeepRuleAction.getKeepRule(node);

    // the expected data may change if baksmali is updated
    String expected = "# Add *one* of the following rules to your Proguard configuration file.\n" +
                      "# Alternatively, you can annotate classes and class members with @android.support.annotation.Keep\n" +
                      "\n" +
                      "# keep the class and specified members from being removed or renamed\n" +
                      "-keep class Test { <init>(); }\n" +
                      "\n" +
                      "# keep the specified class members from being removed or renamed \n" +
                      "# only if the class is preserved\n" +
                      "-keepclassmembers class Test { <init>(); }\n" +
                      "\n" +
                      "# keep the class and specified members from being renamed only\n" +
                      "-keepnames class Test { <init>(); }\n" +
                      "\n" +
                      "# keep the specified class members from being renamed only\n" +
                      "-keepclassmembernames class Test { <init>(); }\n" +
                      "\n";
    assertThat(code).isEqualTo(expected);
  }

  @Nullable
  private static DexElementNode getNode(@NotNull DexElementNode root, String path) {
    int index = path.indexOf('.');

    String segment = index < 0 ? path : path.substring(0, index);
    String rest = index < 0 ? "" : path.substring(index + 1);

    for (int i = 0, n = root.getChildCount(); i < n; i ++) {
      DexElementNode at = root.getChildAt(i);
      if (at.getName().equals(segment)) {
        if (rest.isEmpty()) {
          return at;
        }
        else {
          return getNode(at, rest);
        }
      }
    }

    return null;
  }
}
