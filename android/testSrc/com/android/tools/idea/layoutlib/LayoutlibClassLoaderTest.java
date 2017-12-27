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
package com.android.tools.idea.layoutlib;

import com.google.common.io.CharSource;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class LayoutlibClassLoaderTest {

  /**
   * Simplify the output from the ASM Textifier so we do not get the comments or Opcodes into the output
   */
  private static String simplify(String s) {
    try {
      return CharSource.wrap(s).readLines().stream().filter(l -> !l.trim().isEmpty() && !l.startsWith("   ") && !l.trim().startsWith("//"))
        .collect(Collectors.joining("\n"));
    }
    catch (IOException e) {
      e.printStackTrace();
      return "";
    }
  }


  @Test
  public void generateBuildFile() throws Exception {
    Map<String, String> definedClasses = new HashMap<>();
    LayoutlibClassLoader.generate(TestBuild.class, (name, classBytes) -> {
      StringWriter writer = new StringWriter();
      ClassReader reader = new ClassReader(classBytes);
      TraceClassVisitor visitor = new TraceClassVisitor(new PrintWriter(writer));
      reader.accept(visitor, 0);

      definedClasses.put(name, simplify(writer.toString()));
    });

    assertEquals(3, definedClasses.size()); // Outer class + 2 inner classes
    assertEquals("public class android/os/Build {\n" +
                 "  public static INNERCLASS android/os/Build$InnerClass2 android/os/Build InnerClass2\n" +
                 "  public static INNERCLASS android/os/Build$InnerClass android/os/Build InnerClass\n" +
                 "  public final static Ljava/lang/String; TEST_FIELD = \"TestValue\"\n" +
                 "  public <init>()V\n" +
                 "  private static privateMethod()Ljava/lang/String;\n" +
                 "  public static getSerial()Ljava/lang/String;\n" +
                 "}",
                 definedClasses.get("android.os.Build"));

    assertEquals("public class android/os/Build$InnerClass {\n" +
                 "  public static INNERCLASS android/os/Build$InnerClass android/os/Build InnerClass\n" +
                 "  public final static Ljava/lang/String; TEST_INNER_FIELD = \"TestInnerValue\"\n" +
                 "  public final static I INNER_VALUE = 1\n" +
                 "  public <init>()V\n" +
                 "}",
                 definedClasses.get("android.os.Build$InnerClass"));

    assertEquals("public class android/os/Build$InnerClass2 {\n" +
                 "  public static INNERCLASS android/os/Build$InnerClass2 android/os/Build InnerClass2\n" +
                 "  public final static Ljava/lang/String; TEST_INNER_FIELD2\n" +
                 "  public <init>()V\n" +
                 "  static <clinit>()V\n" +
                 "}",
                 definedClasses.get("android.os.Build$InnerClass2"));
  }
}