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

import static com.android.tools.idea.apk.dex.DexFiles.getDexFile;
import static org.junit.Assert.assertEquals;

public class PackageTreeCreatorTest {
  @Test
  public void simpleMethodReferenceTree() throws IOException {
    DexBackedDexFile dexFile = getTestDexFile("Test.dex");
    PackageTreeNode packageTreeNode = new PackageTreeCreator().constructPackageTree(dexFile);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, packageTreeNode, 0);
    assertEquals("root: 3,6\n" +
                 "  Test: 3,3\n" +
                 "    void <init>(): 1,1\n" +
                 "    java.lang.Integer get(): 1,1\n" +
                 "    java.util.List getList(): 1,1\n" +
                 "  java: 0,3\n" +
                 "    lang: 0,2\n" +
                 "      Integer: 0,1\n" +
                 "        java.lang.Integer valueOf(int): 0,1\n" +
                 "      Object: 0,1\n" +
                 "        void <init>(): 0,1\n" +
                 "    util: 0,1\n" +
                 "      Collections: 0,1\n" +
                 "        java.util.List emptyList(): 0,1\n", sb.toString());
    assertEquals(6, dexFile.getMethodCount());
  }

  @Test
  public void fieldsAndMethodReferenceTree() throws IOException {
    DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
    PackageTreeNode packageTreeNode = new PackageTreeCreator().constructPackageTree(dexFile);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, packageTreeNode, 0);
    assertEquals("root: 27,33\n" +
                 "  com: 27,27\n" +
                 "    example: 27,27\n" +
                 "      MyAbstractClas: 6,6\n" +
                 "        void <init>(): 1,1\n" +
                 "        com.example.MyAbstractClas getInstance(): 1,1\n" +
                 "        void privateMethod(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "        void publicMethod(): 1,1\n" +
                 "      MyAbstractClas$1: 3,3\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "      BuildConfig: 2,2\n" +
                 "        void <clinit>(): 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        java.lang.String APPLICATION_ID: 0,0\n" +
                 "        java.lang.String BUILD_TYPE: 0,0\n" +
                 "        boolean DEBUG: 0,0\n" +
                 "        java.lang.String FLAVOR: 0,0\n" +
                 "        int VERSION_CODE: 0,0\n" +
                 "        java.lang.String VERSION_NAME: 0,0\n" +
                 "      MainActivity: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void onCreate(android.os.Bundle): 1,1\n" +
                 "      MyClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void method(): 1,1\n" +
                 "        com.example.MyClass anon: 0,0\n" +
                 "        com.example.MyAbstractClas initializedField: 0,0\n" +
                 "        int privateIntField: 0,0\n" +
                 "        java.lang.String privateString: 0,0\n" +
                 "        int publicIntField: 0,0\n" +
                 "        java.lang.String publicStringField: 0,0\n" +
                 "      MyClass$NonStaticInnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      MyClass$StaticClass$InnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass$StaticClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "        com.example.MyClass$StaticClass this$0: 0,0\n" +
                 "      MyClass$StaticClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        com.example.MyClass method(): 1,1\n" +
                 "      MyClass$1: 1,1\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      R$attr: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      R$color: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int colorAccent: 0,0\n" +
                 "        int colorPrimary: 0,0\n" +
                 "        int colorPrimaryDark: 0,0\n" +
                 "      R$mipmap: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int ic_launcher: 0,0\n" +
                 "        int ic_launcher_round: 0,0\n" +
                 "      R$string: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int app_name: 0,0\n" +
                 "      R: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "  java: 0,4\n" +
                 "    lang: 0,2\n" +
                 "      Object: 0,1\n" +
                 "        void <init>(): 0,1\n" +
                 "      Boolean: 0,1\n" +
                 "        boolean parseBoolean(java.lang.String): 0,1\n" +
                 "      System: 0,0\n" +
                 "        java.io.PrintStream err: 0,0\n" +
                 "        java.io.PrintStream out: 0,0\n" +
                 "    io: 0,2\n" +
                 "      PrintStream: 0,2\n" +
                 "        void println(int): 0,1\n" +
                 "        void println(java.lang.String): 0,1\n" +
                 "  android: 0,2\n" +
                 "    app: 0,2\n" +
                 "      Activity: 0,2\n" +
                 "        void <init>(): 0,1\n" +
                 "        void onCreate(android.os.Bundle): 0,1\n", sb.toString());
    assertEquals(33, dexFile.getMethodCount());
  }

  @NotNull
  private static DexBackedDexFile getTestDexFile(String filename) throws IOException {
    Path dexPath = Paths.get(AndroidTestBase.getTestDataPath(), "apk/" + filename);
    return getDexFile(Files.readAllBytes(dexPath));
  }

  private static void dumpTree(StringBuffer sb, @NotNull PackageTreeNode node, int depth) {
    sb.append(StringUtil.repeatSymbol(' ', depth * 2));
    sb.append(node.getName());
    sb.append(": ");
    sb.append(node.getDefinedMethodsCount());
    sb.append(',');
    sb.append(node.getMethodRefCount());
    sb.append('\n');

    for (int i = 0; i < node.getChildCount(); i++) {
      dumpTree(sb, (PackageTreeNode)node.getChildAt(i), depth + 1);
    }
  }
}

