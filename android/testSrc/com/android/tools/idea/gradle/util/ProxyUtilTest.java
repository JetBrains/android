/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.util.ProxyUtil.WrapperInvocationHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import junit.framework.TestCase;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;

import static com.android.tools.idea.gradle.util.ProxyUtil.isValidProxyObject;
import static com.android.tools.idea.gradle.util.ProxyUtil.reproxy;

/**
 * Tests for {@link ProxyUtil}
 */
public class ProxyUtilTest extends TestCase {
  private MyInterface myProxy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProxy = createProxyInstance(true);
  }

  public void testSupportedTypes() throws Exception {
    assertTypeIsSupported(AndroidProject.class.getPackage(), AndroidProject.class);
    assertTypeIsSupported(NativeAndroidProject.class.getPackage(), NativeAndroidProject.class);
  }

  private static void assertTypeIsSupported(Package reproxy, Class<?> clazz) {
    if (!clazz.isPrimitive() && clazz.getPackage().equals(reproxy)) {
      for (Method method : clazz.getMethods()) {
        if (Modifier.isPublic(method.getModifiers())) {
          assertTypeIsSupported(reproxy, method.getReturnType());
        }
      }
    }
    else {
      assertTrue("Unsupported type " + clazz, ProxyUtil.isSupported(clazz));
    }
  }

  public void testReproxy() throws Exception {
    MyInterface reproxy = reproxy(MyInterface.class, myProxy);
    assertNotSame(reproxy, myProxy);
    assertNotNull(reproxy);
    assertProxyEquals(myProxy, reproxy);
  }

  public void testEquality() throws Exception {
    MyInterface reproxy = reproxy(MyInterface.class, myProxy);
    MyInterface reproxy2 = reproxy(MyInterface.class, myProxy);
    assertNotNull(reproxy);
    assertNotNull(reproxy2);
    assertNotSame(reproxy, myProxy);
    assertFalse(myProxy.equals(reproxy));
    assertFalse(myProxy.hashCode() == reproxy.hashCode());
    assertFalse(reproxy.equals(myProxy));
    assertFalse(reproxy.hashCode() == myProxy.hashCode());
    assertTrue(reproxy.equals(reproxy));
    assertTrue(reproxy.hashCode() == reproxy.hashCode());
    assertFalse(reproxy.equals(reproxy2));
    assertFalse(reproxy.hashCode() == reproxy2.hashCode());
    assertFalse(reproxy2.equals(reproxy));
    assertFalse(reproxy2.hashCode() == reproxy.hashCode());
  }

  public void testToString() throws Exception {
    MyInterface reproxy = reproxy(MyInterface.class, myProxy);
    assertNotNull(myProxy.toString());
    assertNotNull(reproxy);
    assertNotNull(reproxy.toString());
    System.out.println("");
  }

  public void testNewMethod() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    MyInterface reproxy = reproxy(MyInterface.class, myProxy);

    assertNotNull(reproxy);
    InvocationHandler handler = Proxy.getInvocationHandler(reproxy);
    assertTrue(handler instanceof WrapperInvocationHandler);
    WrapperInvocationHandler wrapper = (WrapperInvocationHandler)handler;
    Method m = MyInterface.class.getMethod("getString");
    wrapper.values.remove(m.toGenericString());

    try {
      reproxy.getString();
      fail("Removed method should throw an exception");
    } catch (UnsupportedMethodException e) {
      // Expected.
    }
  }

  public void testValidityPositive() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    MyInterface reproxy = reproxy(MyInterface.class, myProxy);
    assertNotNull(reproxy);

    assertTrue(isValidProxyObject(reproxy));
  }

  public void testValidityNegative() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    MyInterface reproxy = reproxy(MyInterface.class, myProxy);
    assertNotNull(reproxy);
    InvocationHandler handler = Proxy.getInvocationHandler(reproxy);
    assertTrue(handler instanceof WrapperInvocationHandler);
    WrapperInvocationHandler wrapper = (WrapperInvocationHandler)handler;
    Method m = MyInterface.class.getMethod("getString");
    wrapper.values.remove(m.toGenericString());

    assertFalse(isValidProxyObject(reproxy)); // Removed method should result in validity check failure
  }

  private static MyInterface createProxyInstance(boolean recurse) {
    final MyInterfaceImpl delegate = new MyInterfaceImpl(recurse);
    return (MyInterface)Proxy.newProxyInstance(MyInterface.class.getClassLoader(), new Class[]{MyInterface.class}, (o, method, objects) -> {
      try {
        return method.invoke(delegate, objects);
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
    });
  }

  private static void assertProxyCollectionEquals(@Nullable Map<String, Collection<MyInterface>> expected,
                                                  @Nullable Map<String, Collection<MyInterface>> actual) {
    if (expected == null) {
      assertNull(actual);
      return;
    }
    assertNotNull(actual);
    assertEquals(expected.keySet(), actual.keySet());
    for (String key : expected.keySet()) {
      assertProxyCollectionEquals(expected.get(key), actual.get(key));
    }
  }

  private static void assertProxyEquals(MyInterface expected, MyInterface actual) {
    assertEquals(expected.getString(), actual.getString());
    assertEquals(expected.getFile(), actual.getFile());
    assertEquals(expected.getNativeBoolean(), actual.getNativeBoolean());
    assertEquals(expected.getStringCollection(), actual.getStringCollection());
    assertEquals(expected.getBooleanList(), actual.getBooleanList());
    assertEquals(expected.getStringSet(), actual.getStringSet());

    assertProxyCollectionEquals(expected.getProxyCollection(), actual.getProxyCollection());
    assertProxyCollectionEquals(expected.getProxyList(), actual.getProxyList());
    assertProxyCollectionEquals(expected.getMapToProxy(), actual.getMapToProxy());

    UnsupportedMethodException exception = null;
    try {
      expected.doesNotExist();
      fail("Original method should throw.");
    }
    catch (UnsupportedMethodException e) {
      // Expected.
      exception = e;
    }

    try {
      actual.doesNotExist();
      fail("Reproxy should also throw.");
    }
    catch (UnsupportedMethodException e) {
      assertEquals(e.getClass(), exception.getClass());
      assertEquals(e.getMessage(), exception.getMessage());
    }
  }

  private static void assertProxyCollectionEquals(@Nullable Collection<MyInterface> expected, @Nullable Collection<MyInterface> actual) {
    if (expected == null) {
      assertNull(actual);
      return;
    }
    assertNotNull(actual);
    Iterator<MyInterface> a = expected.iterator();
    Iterator<MyInterface> b = actual.iterator();

    while (a.hasNext()) {
      assertTrue(b.hasNext());
      assertProxyEquals(a.next(), b.next());
    }
    assertFalse(b.hasNext());
  }

  interface MyInterface {
    String getString();

    File getFile();

    boolean getNativeBoolean();

    @Nullable
    Collection<String> getStringCollection();

    @Nullable
    Collection<MyInterface> getProxyCollection();

    @Nullable
    List<Boolean> getBooleanList();

    @Nullable
    List<MyInterface> getProxyList();

    @Nullable
    Set<String> getStringSet();

    @Nullable
    Map<String, Collection<MyInterface>> getMapToProxy();

    boolean doesNotExist() throws UnsupportedMethodException;
  }

  static class MyInterfaceImpl implements MyInterface {

    final boolean recurse;

    MyInterfaceImpl(boolean recurse) {
      this.recurse = recurse;
    }

    @Override
    public String getString() {
      return "aString";
    }

    @Override
    public File getFile() {
      return new File("a/sample/file");
    }

    @Override
    public boolean getNativeBoolean() {
      return true;
    }

    @Override
    public Collection<String> getStringCollection() {
      return Lists.newArrayList("one", "two", "three");
    }

    @Override
    public Collection<MyInterface> getProxyCollection() {
      return recurse ? Sets.newHashSet(createProxyInstance(false)) : null;
    }

    @Override
    public List<Boolean> getBooleanList() {
      return ImmutableList.of(false, true);
    }

    @Override
    public List<MyInterface> getProxyList() {
      return recurse ? Lists.newArrayList(createProxyInstance(false)) : null;
    }

    @Override
    public Set<String> getStringSet() {
      return Sets.newHashSet("a", "a", "b");
    }

    @Override
    public Map<String, Collection<MyInterface>> getMapToProxy() {
      if (!recurse) return null;

      return ImmutableMap.of("one", Sets.newHashSet(new MyInterfaceImpl(false)), "two",
                             Lists.newArrayList(createProxyInstance(false), createProxyInstance(false)));
    }

    @Override
    public boolean doesNotExist() throws UnsupportedMethodException {
      throw new UnsupportedMethodException("This method doesn't exist");
    }
  }
}
