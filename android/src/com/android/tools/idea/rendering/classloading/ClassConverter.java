/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;

/**
 * Rewrites classes applying the following transformations:
 * <ul>
 *   <li>Updates the class file version with a version runnable in the current JDK
 *   <li>Replaces onDraw, onMeasure and onLayout for custom views
 * </ul>
 * Note that it does not attempt to handle cases where class file constructs cannot
 * be represented in the target version. This is intended for uses such as for example
 * the Android R class, which is simple and can be converted to pretty much any class file
 * version, which makes it possible to load it in an IDE layout render execution context
 * even if it has been compiled for a later version.
 * <p/>
 * For custom views (classes that inherit from android.view.View or any widget in android.widget.*)
 * the onDraw, onMeasure and onLayout methods are replaced with methods that capture any exceptions thrown.
 * This way we avoid custom views breaking the rendering.
 */
public final class ClassConverter {
  private static final int ourCurrentJdkClassVersion = jdkToClassVersion(SystemInfo.JAVA_VERSION);

   /**
   * Rewrites the given class applying the given transformations.
   *
   * @param classData the input class binary contents.
   * @param transformations a {@link ClassTransform} that provides the transformations to be applied by this rewrite.
   * @param flags optional {@link ClassWriter} flags to use. (0 for none)
   * @param classLocator {@link PseudoClassLocator} to use when the class writing process needs to load additional types.
   */
  @NotNull
  public static byte[] rewriteClass(@NotNull byte[] classData,
                                    @NotNull ClassTransform transformations,
                                    int flags,
                                    @NotNull PseudoClassLocator classLocator) {
    ClassReader reader = new ClassReader(classData);
    final ClassWriter classWriter = new ClassWriterWithPseudoClassLocator(flags, classLocator);
    ClassVisitor classVisitor = transformations.invoke(classWriter);
    reader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

    return classWriter.toByteArray();
  }

  /**
   * Rewrites the given class applying the given transformations.
   *
   * @param classData the input class binary contents.
   * @param transformations a {@link ClassTransform} that provides the transformations to be applied by this rewrite.
   * @param classLocator {@link PseudoClassLocator} to use when the class writing process needs to load additional types.
   */
  @NotNull
  static byte[] rewriteClass(@NotNull byte[] classData,
                             @NotNull ClassTransform transformations,
                             @NotNull PseudoClassLocator classLocator) {
    return rewriteClass(classData, transformations, ClassWriter.COMPUTE_FRAMES, classLocator);
  }

  /** Converts a JDK string like 1.6.0_65 to the corresponding class file version number, e.g. 50 */
  public static int jdkToClassVersion(@NotNull String version) { // e.g. 1.6.0_b52
    final JavaVersion javaVersion = JavaVersion.tryParse(version);
    return javaVersion != null ? javaVersion.feature + 44 : -1;
  }

  /** Converts a class file version number  JDK string like 1.6.0_65 to the corresponding class file version number, e.g. 50 */
  public static String classVersionToJdk(int version) {
    if (version >= 53) {
      return Integer.toString(version - 53 + 9);
    }
    return "1." + Integer.toString(version - 44); // 47 => 1.3, 50 => 1.6, ...
  }

  /** Return the classfile version of the current JDK */
  public static int getCurrentClassVersion() {
    return ourCurrentJdkClassVersion;
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
