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
package com.android.tools.idea.rendering;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.Collection;

/**
 * Rewrites classes from one class file version to another.
 *
 * Note that it does not attempt to handle cases where class file constructs cannot
 * be represented in the target version. This is intended for uses such as for example
 * the Android R class, which is simple and can be converted to pretty much any class file
 * version, which makes it possible to load it in an IDE layout render execution context
 * even if it has been compiled for a later version.
 */
public class ClassConverter {
  /**
   * Rewrites the given class to a version runnable on the current JDK
   */
  @NotNull
  public static byte[] rewriteClass(@NotNull byte[] classData) {
    int current = getCurrentClassVersion();
    return rewriteClass(classData, current, 0);
  }

  /**
   * Rewrites the given class to the given target class file version.
   */
  @NotNull
  public static byte[] rewriteClass(@NotNull byte[] classData, final int maxVersion, final int minVersion) {
    assert maxVersion >= minVersion;

    ClassWriter classWriter = new ClassWriter(0);
    ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5, classWriter) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (version > maxVersion) {
          version = maxVersion;
        }
        if (version < minVersion) {
          version = minVersion;
        }
        super.visit(version, access, name, signature, superName, interfaces);
      }
    };
    ClassReader reader = new ClassReader(classData);
    reader.accept(classVisitor, 0);
    return classWriter.toByteArray();
  }

  /** Converts a JDK string like 1.6.0_65 to the corresponding class file version number, e.g. 50 */
  public static int jdkToClassVersion(@NotNull String version) { // e.g. 1.6.0_b52
    int dot = version.indexOf('.');
    if (dot != -1) {
      dot++;
      int end = version.length();
      for (int i = dot; i < end; i++) {
        if (!Character.isDigit(version.charAt(i))) {
          end = i;
          break;
        }
      }
      if (end > dot) {
        int major = Integer.valueOf(version.substring(dot, end));
        if (major > 0) {
          return major + 44; // 1.3 => 47, ... 1.6 => 50, 1.7 => 51, ...
        }
      }
    }

    return -1;
  }

  /** Converts a class file version number  JDK string like 1.6.0_65 to the corresponding class file version number, e.g. 50 */
  public static String classVersionToJdk(int version) {
    return "1." + Integer.toString(version - 44); // 47 => 1.3, 50 => 1.6, ...
  }

  /** Given a set of exceptions, return the highest known classfile version format */
  public static int findHighestMajorVersion(Collection<Throwable> list) {
    int result = 0;
    for (Throwable t : list) {
      if (t instanceof InconvertibleClassError) {
        InconvertibleClassError error = (InconvertibleClassError)t;
        result = Math.max(result, error.getMajor());
      }
    }

    return result;
  }

  /** Return the current JDK version number */
  public static String getCurrentJdkVersion() {
    String version = SystemInfo.JAVA_VERSION;
    int suffix = version.indexOf('_');
    if (suffix != -1) {
      version = version.substring(0, suffix);
    }

    return version;
  }

  /** Return the classfile version of the current JDK */
  public static int getCurrentClassVersion() {
    return jdkToClassVersion(SystemInfo.JAVA_VERSION);
  }

  /** Returns true if the given class file data represents a valid class */
  public static boolean isValidClassFile(@NotNull byte[] classData) {
    // See http://en.wikipedia.org/wiki/Java_class_file
    return classData.length >= 7 && getMagic(classData) == 0xCAFEBABE;
  }

  /** Returns the magic number of the given class file */
  public static int getMagic(@NotNull byte[] classData) {
    // See http://en.wikipedia.org/wiki/Java_class_file
    return Ints.fromBytes(classData[0], classData[1], classData[2], classData[3]);
  }

  /** Returns the major class file version */
  public static short getMajorVersion(byte[] classData) {
    // See http://en.wikipedia.org/wiki/Java_class_file
    return Shorts.fromBytes(classData[6], classData[7]);
  }

  /** Returns the minor class file version */
  public static short getMinorVersion(byte[] classData) {
    // See http://en.wikipedia.org/wiki/Java_class_file
    return Shorts.fromBytes(classData[4], classData[5]);
  }
}
