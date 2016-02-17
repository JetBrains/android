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

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.ClassNode;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.rendering.ClassConverter.*;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

public class ClassConverterTest extends TestCase {
  public void testClassVersionToJdk() {
    assertEquals("1.5", classVersionToJdk(49));
    assertEquals("1.6", classVersionToJdk(50));
    assertEquals("1.7", classVersionToJdk(51));
    assertEquals("1.8", classVersionToJdk(52));
    assertEquals("1.4", classVersionToJdk(48));
    assertEquals("1.3", classVersionToJdk(47));
    assertEquals("1.2", classVersionToJdk(46));
    assertEquals("1.1", classVersionToJdk(45));
  }

  public void testJdkToClassVersion() {
    assertEquals(-1, jdkToClassVersion("?"));
    assertEquals(49, jdkToClassVersion("1.5"));
    assertEquals(49, jdkToClassVersion("1.5 "));
    assertEquals(49, jdkToClassVersion("1.5.0"));
    assertEquals(49, jdkToClassVersion("1.5.0_50"));
    assertEquals(50, jdkToClassVersion("1.6"));
    assertEquals(50, jdkToClassVersion("1.6.1"));
    assertEquals(51, jdkToClassVersion("1.7.0"));
    assertEquals(45, jdkToClassVersion("1.1"));
  }

  public void testFindHighestMajorVersion() {
    InconvertibleClassError v1 = new InconvertibleClassError(null, "foo1", 49, 0);
    InconvertibleClassError v2 = new InconvertibleClassError(null, "foo2", 51, 0);
    InconvertibleClassError v3 = new InconvertibleClassError(null, "foo3", 49, 0);
    assertEquals(51, findHighestMajorVersion(Lists.<Throwable>newArrayList(v1, v2, v3)));
  }

  public void testGetCurrentClassVersion() {
    assertTrue(getCurrentClassVersion() >= 50);
  }

  public void testGetCurrentJdkVersion() {
    assertTrue(getCurrentJdkVersion().startsWith("1."));
  }

  public void testMangling() throws Exception {
    // Compile
    //     public class Test { public static int test() { return 42; } }
    // then compile with javac Test.java and take the binary contents of Test.class
    byte[] data = new byte[] {
      (byte)202, (byte)254, (byte)186, (byte)190, (byte)0, (byte)0, (byte)0, (byte)50, (byte)0, (byte)15,
      (byte)10, (byte)0, (byte)3, (byte)0, (byte)12, (byte)7, (byte)0, (byte)13, (byte)7, (byte)0,
      (byte)14, (byte)1, (byte)0, (byte)6, (byte)60, (byte)105, (byte)110, (byte)105, (byte)116, (byte)62,
      (byte)1, (byte)0, (byte)3, (byte)40, (byte)41, (byte)86, (byte)1, (byte)0, (byte)4, (byte)67,
      (byte)111, (byte)100, (byte)101, (byte)1, (byte)0, (byte)15, (byte)76, (byte)105, (byte)110, (byte)101,
      (byte)78, (byte)117, (byte)109, (byte)98, (byte)101, (byte)114, (byte)84, (byte)97, (byte)98, (byte)108,
      (byte)101, (byte)1, (byte)0, (byte)4, (byte)116, (byte)101, (byte)115, (byte)116, (byte)1, (byte)0,
      (byte)3, (byte)40, (byte)41, (byte)73, (byte)1, (byte)0, (byte)10, (byte)83, (byte)111, (byte)117,
      (byte)114, (byte)99, (byte)101, (byte)70, (byte)105, (byte)108, (byte)101, (byte)1, (byte)0, (byte)9,
      (byte)84, (byte)101, (byte)115, (byte)116, (byte)46, (byte)106, (byte)97, (byte)118, (byte)97, (byte)12,
      (byte)0, (byte)4, (byte)0, (byte)5, (byte)1, (byte)0, (byte)4, (byte)84, (byte)101, (byte)115,
      (byte)116, (byte)1, (byte)0, (byte)16, (byte)106, (byte)97, (byte)118, (byte)97, (byte)47, (byte)108,
      (byte)97, (byte)110, (byte)103, (byte)47, (byte)79, (byte)98, (byte)106, (byte)101, (byte)99, (byte)116,
      (byte)0, (byte)33, (byte)0, (byte)2, (byte)0, (byte)3, (byte)0, (byte)0, (byte)0, (byte)0,
      (byte)0, (byte)2, (byte)0, (byte)1, (byte)0, (byte)4, (byte)0, (byte)5, (byte)0, (byte)1,
      (byte)0, (byte)6, (byte)0, (byte)0, (byte)0, (byte)29, (byte)0, (byte)1, (byte)0, (byte)1,
      (byte)0, (byte)0, (byte)0, (byte)5, (byte)42, (byte)183, (byte)0, (byte)1, (byte)177, (byte)0,
      (byte)0, (byte)0, (byte)1, (byte)0, (byte)7, (byte)0, (byte)0, (byte)0, (byte)6, (byte)0,
      (byte)1, (byte)0, (byte)0, (byte)0, (byte)1, (byte)0, (byte)9, (byte)0, (byte)8, (byte)0,
      (byte)9, (byte)0, (byte)1, (byte)0, (byte)6, (byte)0, (byte)0, (byte)0, (byte)27, (byte)0,
      (byte)1, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)3, (byte)16, (byte)42, (byte)172,
      (byte)0, (byte)0, (byte)0, (byte)1, (byte)0, (byte)7, (byte)0, (byte)0, (byte)0, (byte)6,
      (byte)0, (byte)1, (byte)0, (byte)0, (byte)0, (byte)1, (byte)0, (byte)1, (byte)0, (byte)10,
      (byte)0, (byte)0, (byte)0, (byte)2, (byte)0, (byte)11
    };

    assertTrue(isValidClassFile(data));
    assertFalse(isValidClassFile(new byte[100]));

    assertEquals(50, getMajorVersion(data));
    assertEquals(0, getMinorVersion(data));
    assertEquals(0xCAFEBABE, getMagic(data));

    ClassLoader classLoader = new TestClassLoader(data);
    Class<?> clz = classLoader.loadClass("Test");
    assertNotNull(clz);
    Object result = clz.getMethod("test").invoke(null);
    assertEquals(Integer.valueOf(42), result);
    Class<?> oldClz = clz;

    data = rewriteClass(data, 48, Integer.MIN_VALUE, Integer.MAX_VALUE);
    assertEquals(48, getMajorVersion(data));
    classLoader = new TestClassLoader(data);
    clz = classLoader.loadClass("Test");
    assertNotNull(clz);
    assertNotSame(clz, oldClz);
    result = clz.getMethod("test").invoke(null);
    assertEquals(Integer.valueOf(42), result);

    data = rewriteClass(data, Integer.MAX_VALUE, 52, Integer.MAX_VALUE); // latest known
    assertEquals(52, getMajorVersion(data));
    classLoader = new TestClassLoader(data);
    clz = classLoader.loadClass("Test");
    assertNotNull(clz);
    result = clz.getMethod("test").invoke(null);
    assertEquals(Integer.valueOf(42), result);

    // Make sure that that class cannot actually be loaded in the regular way
    try {
      final byte[] finalData = data;
      ClassLoader cl = new ClassLoader() {
        @Override
        protected Class<?> findClass(String s) throws ClassNotFoundException {
          assertEquals("Test", s);
          return defineClass(null, finalData, 0, finalData.length);
        }
      };
      cl.loadClass("Test");
      if (getCurrentClassVersion() < 52) {
        fail("Expected class loading error");
      }
    } catch (UnsupportedClassVersionError e) {
      // pass
    } catch (Throwable t) {
      fail("Expected class loading error");
    }
  }

  // Method that generates the binary dump of the view below:
  //
  //class TestView extends View {
  //  public TestView(Context context) {
  //    super(context);
  //  }
  //
  //  @Override
  //  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
  //    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  //  }
  //}
  //
  // The binary dump was created by using ASMifier running:
  // java -classpath asm-debug-all-5.0.2.jar:. org.objectweb.asm.util.ASMifier TestView.class
  private static byte[] dumpTestViewClass () throws Exception {
    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    cw.visit(V1_7, ACC_SUPER, "TestView", null, "android/view/View", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Landroid/content/Context;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKESPECIAL, "android/view/View", "<init>", "(Landroid/content/Context;)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PROTECTED, "onMeasure", "(II)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitMethodInsn(INVOKESPECIAL, "android/view/View", "onMeasure", "(II)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(3, 3);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  public void testMethodWrapping() throws Exception {
    byte[] data = ClassConverterTest.dumpTestViewClass();

    assertTrue(isValidClassFile(data));
    byte[] modified = rewriteClass(data, Integer.MAX_VALUE);
    assertTrue(isValidClassFile(data));

    // Parse both classes and compare
    ClassNode classNode = new ClassNode();
    ClassReader classReader = new ClassReader(modified);
    classReader.accept(classNode, 0);

    assertEquals(3, classNode.methods.size());
    final Set<String> methods = new HashSet<String>();
    classNode.accept(new ClassVisitor(ASM5) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        methods.add(name + desc);
        return super.visitMethod(access, name, desc, signature, exceptions);
      }
    });
    assertTrue(methods.contains("onMeasure(II)V"));
    assertTrue(methods.contains("onMeasure_Original(II)V"));
  }

  public void testMethodWrapping2() throws Exception {
    final byte[] firstData = getFirstOnMeasureClass();
    final byte[] secondData = getSecondOnMeasureClass();
    assertTrue(isValidClassFile(firstData));
    assertTrue(isValidClassFile(secondData));

    ClassLoader loader = new ClassLoader() {
      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.equals("FirstClass")) {
          return defineClass(name, firstData, 0, firstData.length);
        }
        if (name.equals("SecondClass")) {
          return defineClass(name, secondData, 0, secondData.length);
        }
        return super.findClass(name);
      }
    };
    Class<?> clazz = loader.loadClass("FirstClass");
    Object o = clazz.newInstance();
    int outValue = (Integer) clazz.getMethod("test").invoke(o);
    assertEquals(1, outValue);
    clazz = loader.loadClass("SecondClass");
    o = clazz.newInstance();
    outValue = (Integer)clazz.getMethod("test").invoke(o);
    assertEquals(2, outValue);


    // Modify the classes and repeat.
    final byte[] modifiedFirstData = rewriteClass(firstData, 50, 0, Integer.MAX_VALUE);
    final byte[] modifiedSecondData = rewriteClass(secondData, 50, 0, Integer.MAX_VALUE);
    loader = new ClassLoader() {
      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.equals("FirstClass")) {
          return defineClass(name, modifiedFirstData, 0, modifiedFirstData.length);
        }
        if (name.equals("SecondClass")) {
          return defineClass(name, modifiedSecondData, 0, modifiedSecondData.length);
        }
        return super.findClass(name);
      }
    };
    clazz = loader.loadClass("FirstClass");
    o = clazz.newInstance();
    outValue = (Integer) clazz.getMethod("test").invoke(o);
    assertEquals(1, outValue);
    clazz = loader.loadClass("SecondClass");
    o = clazz.newInstance();
    outValue = (Integer)clazz.getMethod("test").invoke(o);
    assertEquals(2, outValue);
  }

  private static byte[] getFirstOnMeasureClass() {
    // Use asmifier to convert the compiled version of following class to generation code:
    // public class FirstClass {
    // int a;
    // protected void onMeasure(int x, int y) {a = 1;}
    // public int test() { onMeasure(0,0); return a;}}

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "FirstClass", null, "java/lang/Object", null);

    {
      fv = cw.visitField(0, "a", "I", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PROTECTED, "onMeasure", "(II)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_1);
      mv.visitFieldInsn(PUTFIELD, "FirstClass", "a", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 3);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "test", "()I", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "FirstClass", "onMeasure", "(II)V", false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "FirstClass", "a", "I");
      mv.visitInsn(IRETURN);
      mv.visitMaxs(3, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private static byte[] getSecondOnMeasureClass() {
    // Use asmifier to convert the compiled version of following class to generation code:
    // class SecondClass extends FirstClass {@Override protected void onMeasure(int x, int y) {super.onMeasure(x, y); a = 2;}}

    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "SecondClass", null, "FirstClass", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "FirstClass", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PROTECTED, "onMeasure", "(II)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitMethodInsn(INVOKESPECIAL, "FirstClass", "onMeasure", "(II)V", false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_2);
      mv.visitFieldInsn(PUTFIELD, "SecondClass", "a", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(3, 3);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private static class TestClassLoader extends RenderClassLoader {
    final byte[] myData;
    public TestClassLoader(byte[] data) {
      super(null,Integer.MAX_VALUE);
      myData = data;
    }

    @Override
    protected List<URL> getExternalJars() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    protected Class<?> load(String name) throws ClassNotFoundException {
      assertEquals("Test", name);
      Class<?> clz = loadClass(name, myData);
      assertNotNull(clz);
      return clz;
    }
  }
}
