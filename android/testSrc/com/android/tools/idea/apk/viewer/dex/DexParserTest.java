/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class DexParserTest {
  @Test
  public void simpleMethodReferenceTree() throws IOException {
    DexBackedDexFile dexFile = getTestDexFile();
    PackageTreeNode packageTreeNode = DexParser.constructMethodRefTreeForDex(dexFile);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, packageTreeNode, 0);
    assertEquals("root: 6\n" +
                 "  Test: 3\n" +
                 "    void <init>(): 1\n" +
                 "    java.lang.Integer get(): 1\n" +
                 "    java.util.List getList(): 1\n" +
                 "  java: 3\n" +
                 "    lang: 2\n" +
                 "      Integer: 1\n" +
                 "        java.lang.Integer valueOf(int): 1\n" +
                 "      Object: 1\n" +
                 "        void <init>(): 1\n" +
                 "    util: 1\n" +
                 "      Collections: 1\n" +
                 "        java.util.List emptyList(): 1\n", sb.toString());
    assertEquals(6, dexFile.getMethodCount());
  }

  @NotNull
  private static DexBackedDexFile getTestDexFile() throws IOException {
    Path testDataPath = Paths.get(AndroidTestBase.getAbsoluteTestDataPath());
    Path dexPath = testDataPath.resolve("apk/Test.dex");
    return DexParser.getDexFile(Files.readAllBytes(dexPath));
  }

  private static void dumpTree(StringBuffer sb, @NotNull PackageTreeNode node, int depth) {
    sb.append(StringUtil.repeatSymbol(' ', depth * 2));
    sb.append(node.getName());
    sb.append(": ");
    sb.append(node.getMethodRefCount());
    sb.append('\n');

    for (int i = 0; i < node.getChildCount(); i++) {
      dumpTree(sb, (PackageTreeNode)node.getChildAt(i), depth + 1);
    }
  }
}

