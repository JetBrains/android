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

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.LayoutLog;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.commons.Remapper;
import org.jetbrains.org.objectweb.asm.commons.RemappingClassAdapter;
import org.jetbrains.org.objectweb.asm.commons.SimpleRemapper;

import java.util.Collection;

/**
 * Rewrites classes applying the following transformations:
 * <ul>
 *   <li>Updates the class file version with a version runnable in the current JDK
 *   <li>Replaces onDraw, onMeasure and onLayout for custom views
 *   <li>Replaces any uses of java.text.SimpleDateFormat with android.icu.text.SimpleDateFormat to match the platform behaviour
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
public class ClassConverter {
  private static final String ORIGINAL_SUFFIX = "_Original";
  private static final String ERROR_METHOD_DESCRIPTION;
  private static final Remapper TYPE_REMAPPER =
    new SimpleRemapper(ImmutableMap.<String, String>builder()
                         .put("java/text/DateFormat", "android/icu/text/DateFormat")
                         .put("java/text/SimpleDateFormat", "android/icu/text/SimpleDateFormat")
                         .build());

  static {
    String desc;
    try {
       desc = Type.getMethodDescriptor(LayoutLog.class.getMethod("error", String.class, String.class, Throwable.class, Object.class));
    }
    catch (NoSuchMethodException e) {
      desc = "";
      // We control the API, so the method should always exist.
      assert false;
    }
    ERROR_METHOD_DESCRIPTION = desc;
  }

  /**
   * Rewrites the given class to a version runnable on the current JDK
   */
  @NotNull
  public static byte[] rewriteClass(@NotNull byte[] classData, int layoutlibApi) {
    int current = getCurrentClassVersion();
    return rewriteClass(classData, current, 0, layoutlibApi);
  }

  /**
   * Rewrites the given class to the given target class file version.
   */
  @NotNull
  public static byte[] rewriteClass(@NotNull byte[] classData, final int maxVersion, final int minVersion, int layoutlibApi) {
    assert maxVersion >= minVersion;

    final ClassWriter classWriter = new ClassWriter(0);
    ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5, classWriter) {
      private String myClassName;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        myClassName = name;
        if (version > maxVersion) {
          version = maxVersion;
        }
        if (version < minVersion) {
          version = minVersion;
        }
        super.visit(version, access, name, signature, superName, interfaces);
      }

      /**
       * Creates a new method that calls an existing "name"_Original method and catches any exception the method might throw.
       * The exception is logged via the Bridge logger.
       * <p/>
       * Only void return type methods are currently supported.
       */
      private void wrapMethod(int access, String name, String desc, String signature, String[] exceptions) {
        assert Type.getReturnType(desc) == Type.VOID_TYPE : "Non void return methods are not supported";

        MethodVisitor mw = super.visitMethod(access, name, desc, signature, exceptions);

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label tryHandler = new Label();
        mw.visitTryCatchBlock(tryStart, tryEnd, tryHandler, "java/lang/Throwable");
        //try{
        mw.visitLabel(tryStart);
        mw.visitVarInsn(Opcodes.ALOAD, 0); // this
        // push all the parameters
        Type[] argumentTypes = Type.getMethodType(desc).getArgumentTypes();
        int nLocals = 1;
        for (Type argType : argumentTypes) {
          mw.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), nLocals++);
        }
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, myClassName, name + ORIGINAL_SUFFIX, desc, false);
        mw.visitLabel(tryEnd);
        Label exit = new Label();
        mw.visitJumpInsn(Opcodes.GOTO, exit);
        //} catch(Throwable t) {
        mw.visitLabel(tryHandler);
        mw.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        int throwableIndex = nLocals++;
        mw.visitVarInsn(Opcodes.ASTORE, throwableIndex);

        //  Bridge.getLog().warning()
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "com/android/layoutlib/bridge/Bridge", "getLog",
                           "()Lcom/android/ide/common/rendering/api/LayoutLog;", false);
        mw.visitLdcInsn(LayoutLog.TAG_BROKEN);
        mw.visitLdcInsn(name + " error");
        mw.visitVarInsn(Opcodes.ALOAD, throwableIndex);
        mw.visitInsn(Opcodes.ACONST_NULL);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/android/ide/common/rendering/api/LayoutLog", "error", ERROR_METHOD_DESCRIPTION,
                           false);

        if ("onMeasure".equals(name)) {
          // For onMeasure we need to generate a call to setMeasureDimension to avoid an exception when no size is set
          mw.visitVarInsn(Opcodes.ALOAD, 0); // this
          mw.visitInsn(Opcodes.ICONST_0); // measuredWidth
          mw.visitInsn(Opcodes.ICONST_0); // measuredHeight
          mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, myClassName, "setMeasuredDimension", desc, false);
        }

        mw.visitLabel(exit);
        mw.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mw.visitInsn(Opcodes.RETURN);
        mw.visitMaxs(Math.max(argumentTypes.length + 1/*args + this*/, 5/*getLog + getLog parameters*/), nLocals);
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // We catch the exceptions from any onLayout, onMeasure or onDraw that match the signature from the View methods
        if (("onLayout".equals(name) && "(ZIIII)V".equals(desc) ||
             "onMeasure".equals(name) && "(II)V".equals(desc) ||
             "onDraw".equals(name) && "(Landroid/graphics/Canvas;)V".equals(desc)) &&
            ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)) {
          wrapMethod(access, name, desc, signature, exceptions);
          // Make the Original method private so that it does not end up calling the inherited method.
          int modifiedAccess = (access & ~Opcodes.ACC_PUBLIC & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PRIVATE;
          return super.visitMethod(modifiedAccess, name + ORIGINAL_SUFFIX, desc, signature, exceptions);
        }

        return super.visitMethod(access, name, desc, signature, exceptions);
      }
    };

    ClassReader reader = new ClassReader(classData);
    if (layoutlibApi >= 16) {
      // From layoutlib API 16, we rewrite all methods using SimpleDateFormat and DateFormat to use the android.icu versions
      classVisitor = new RemappingClassAdapter(classVisitor, TYPE_REMAPPER);
    }

    reader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

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
