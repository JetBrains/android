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

import java.net.URL;

import static com.android.tools.idea.rendering.ClassConverter.*;

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
    assertTrue(ClassConverter.getCurrentClassVersion() >= 50);
  }

  public void testGetCurrentJdkVersion() {
    assertTrue(ClassConverter.getCurrentJdkVersion().startsWith("1."));
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

    assertTrue(ClassConverter.isValidClassFile(data));
    assertFalse(ClassConverter.isValidClassFile(new byte[100]));

    assertEquals(50, ClassConverter.getMajorVersion(data));
    assertEquals(0, ClassConverter.getMinorVersion(data));
    assertEquals(0xCAFEBABE, ClassConverter.getMagic(data));

    ClassLoader classLoader = new TestClassLoader(data);
    Class<?> clz = classLoader.loadClass("Test");
    assertNotNull(clz);
    Object result = clz.getMethod("test").invoke(null);
    assertEquals(Integer.valueOf(42), result);
    Class<?> oldClz = clz;

    data = ClassConverter.rewriteClass(data, 48, Integer.MIN_VALUE);
    assertEquals(48, ClassConverter.getMajorVersion(data));
    classLoader = new TestClassLoader(data);
    clz = classLoader.loadClass("Test");
    assertNotNull(clz);
    assertNotSame(clz, oldClz);
    result = clz.getMethod("test").invoke(null);
    assertEquals(Integer.valueOf(42), result);

    data = ClassConverter.rewriteClass(data, Integer.MAX_VALUE, 52); // latest known
    assertEquals(52, ClassConverter.getMajorVersion(data));
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
      fail("Expected class loading error");
    } catch (UnsupportedClassVersionError e) {
      // pass
    } catch (Throwable t) {
      fail("Expected class loading error");
    }
  }

  private static class TestClassLoader extends RenderClassLoader {
    final byte[] myData;
    public TestClassLoader(byte[] data) {
      super(null);
      myData = data;
    }

    @Override
    protected URL[] getExternalJars() {
      return new URL[0];
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
