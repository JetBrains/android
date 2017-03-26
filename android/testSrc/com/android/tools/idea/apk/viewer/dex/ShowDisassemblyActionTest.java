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

import com.android.tools.idea.apk.viewer.dex.tree.DexElementNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.apk.viewer.dex.PackageTreeCreatorTest.getTestDexFile;
import static com.google.common.truth.Truth.assertThat;

public class ShowDisassemblyActionTest {
  private DexBackedDexFile myDexFile;
  private DexElementNode myPackageTree;

  @Before
  public void setUp() throws IOException {
    myDexFile = getTestDexFile("Test.dex");
    myPackageTree = new PackageTreeCreator(null, false).constructPackageTree(myDexFile);
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
    DexElementNode node = getNode(myPackageTree, "Test.void <init>()");
    assertThat(node).isNotNull();
    assertThat(ShowDisassemblyAction.canDisassemble(node)).isTrue();
  }

  @Test
  public void getByteCode_class() {
    DexElementNode node = getNode(myPackageTree, "Test.void <init>()");
    assertThat(node).isNotNull();
    String code = ShowDisassemblyAction.getByteCode(myDexFile, node);

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
