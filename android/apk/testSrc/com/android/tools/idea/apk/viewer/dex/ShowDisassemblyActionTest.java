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

import static com.android.tools.idea.apk.viewer.dex.GenerateProguardKeepRuleActionTest.getDexPath;
import static com.android.tools.idea.apk.viewer.dex.GenerateProguardKeepRuleActionTest.getTestDexFile;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.apk.analyzer.dex.PackageTreeCreator;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

public class ShowDisassemblyActionTest {
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

  @Test
  public void canDisassemble_package() {
    assertThat(ShowDisassemblyAction.canDisassemble(myPackageTree)).isFalse();
  }

  @Test
  public void canDisassemble_class() {
    DexElementNode node = getNode(myPackageTree, "Test");
    assertThat(node).isNotNull();
    assertThat(ShowDisassemblyAction.canDisassemble(node)).isTrue();
  }

  @Test
  public void canDisassemble_method() {
    DexElementNode node = getNode(myPackageTree, "Test.<init>()");
    assertThat(node).isNotNull();
    assertThat(ShowDisassemblyAction.canDisassemble(node)).isTrue();
  }

  @Test
  public void getByteCode_method() {
    DexElementNode node = getNode(myPackageTree, "Test.<init>()");
    assertThat(node).isNotNull();
    String code = ShowDisassemblyAction.getByteCode(myDexFile, node, null);

    // the expected data may change if baksmali is updated
    String expected = ".method public constructor <init>()V\n" +
                      "    .registers 1\n" +
                      "\n" +
                      "    .prologue\n" +
                      "    .line 5\n" +
                      "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                      "\n" +
                      "    return-void\n" +
                      ".end method\n";
    assertThat(code).isEqualTo(expected);
  }

  @Test
  public void getByteCodeWithMapping_class() throws IOException, ParseException {
    DexElementNode node = getNode(myPackageTree, "Test");
    assertThat(node).isNotNull();
    ProguardMap map = new ProguardMap();
    map.readFromFile(getDexPath("mapping.txt").toFile());
    String code = ShowDisassemblyAction.getByteCode(myDexFile, node, map);

    // the expected data may change if baksmali is updated
    String expected = ".class public Loriginal/package/OriginalName;\n" +
                      ".super Ljava/lang/Object;\n" +
                      ".source \"Test.java\"\n" +
                      "\n" +
                      "\n" +
                      "# direct methods\n" +
                      ".method public constructor <init>()V\n" +
                      "    .registers 1\n" +
                      "\n" +
                      "    .prologue\n" +
                      "    .line 5\n" +
                      "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                      "\n" +
                      "    return-void\n" +
                      ".end method\n" +
                      "\n" +
                      "\n" +
                      "# virtual methods\n" +
                      ".method public originalGet()Ljava/lang/Integer;\n" +
                      "    .registers 2\n" +
                      "\n" +
                      "    .prologue\n" +
                      "    .line 7\n" +
                      "    const/16 v0, 0x2a\n" +
                      "\n" +
                      "    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;\n" +
                      "\n" +
                      "    move-result-object v0\n" +
                      "\n" +
                      "    return-object v0\n" +
                      ".end method\n" +
                      "\n" +
                      ".method public getList()Ljava/util/List;\n" +
                      "    .registers 2\n" +
                      "    .annotation system Ldalvik/annotation/Signature;\n" +
                      "        value = {\n" +
                      "            \"()\",\n" +
                      "            \"Ljava/util/List\",\n" +
                      "            \"<\",\n" +
                      "            \"Ljava/lang/Boolean;\",\n" +
                      "            \">;\"\n" +
                      "        }\n" +
                      "    .end annotation\n" +
                      "\n" +
                      "    .prologue\n" +
                      "    .line 11\n" +
                      "    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;\n" +
                      "\n" +
                      "    move-result-object v0\n" +
                      "\n" +
                      "    return-object v0\n" +
                      ".end method\n";
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
