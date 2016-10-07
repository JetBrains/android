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
package com.android.tools.idea.gradle;

import com.android.tools.idea.gradle.util.ProxyUtil;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.io.FileUtil;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link InternalAndroidModelView}.
 */
public class InternalAndroidModelViewTest extends AndroidGradleTestCase {

  public void testCreateTreeNode() throws Exception {
    InternalAndroidModelView view = InternalAndroidModelView.getInstance(getProject());
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("test");
    assertEquals(0, node.getChildCount());

    MyInterface proxyObject = ProxyUtil.reproxy(MyInterface.class, createProxyInstance(true));
    assertNotNull(proxyObject);
    view.addProxyObject(node, proxyObject);
    assertEquals(11, node.getChildCount());

    DefaultMutableTreeNode childAtZero = (DefaultMutableTreeNode) node.getChildAt(0);
    assertEquals(0, childAtZero.getChildCount());
    assertEquals("File -> " + FileUtil.toSystemDependentName("/a/sample/file"), childAtZero.getUserObject());

    DefaultMutableTreeNode childAtOne = (DefaultMutableTreeNode) node.getChildAt(1);
    assertEquals(0, childAtOne.getChildCount());
    assertEquals("FileUnderProject -> " + FileUtil.toSystemDependentName("b/sample/file"), childAtOne.getUserObject());

    DefaultMutableTreeNode childAtTwo = (DefaultMutableTreeNode) node.getChildAt(2);
    assertEquals(0, childAtTwo.getChildCount());
    assertEquals("Name -> aName", childAtTwo.getUserObject());

    DefaultMutableTreeNode childAtThree = (DefaultMutableTreeNode) node.getChildAt(3);
    assertEquals(0, childAtThree.getChildCount());
    assertEquals("NativeBoolean -> true", childAtThree.getUserObject());

    DefaultMutableTreeNode childAtFour = (DefaultMutableTreeNode) node.getChildAt(4);
    assertEquals(0, childAtFour.getChildCount());
    assertEquals("doesNotExist -> Error: org.gradle.tooling.model.UnsupportedMethodException", childAtFour.getUserObject());

    // BooleanList and it's children
    DefaultMutableTreeNode childAtFive = (DefaultMutableTreeNode) node.getChildAt(5);
    assertEquals(2, childAtFive.getChildCount());
    assertEquals("BooleanList", childAtFive.getUserObject());

    DefaultMutableTreeNode booleanListChildAtZero = (DefaultMutableTreeNode) childAtFive.getChildAt(0);
    assertEquals(0, booleanListChildAtZero.getChildCount());
    assertEquals("false", booleanListChildAtZero.getUserObject());

    DefaultMutableTreeNode booleanListChildAtOne = (DefaultMutableTreeNode) childAtFive.getChildAt(1);
    assertEquals(0, booleanListChildAtOne.getChildCount());
    assertEquals("true", booleanListChildAtOne.getUserObject());

    // MapToProxy and it's children
    DefaultMutableTreeNode childAtSix = (DefaultMutableTreeNode) node.getChildAt(6);
    assertEquals(2, childAtSix.getChildCount());
    assertEquals("MapToProxy", childAtSix.getUserObject());

    DefaultMutableTreeNode mapToProxyChildAtZero = (DefaultMutableTreeNode) childAtSix.getChildAt(0);
    assertEquals(1, mapToProxyChildAtZero.getChildCount()); // The object value in the map.
    assertEquals("one", mapToProxyChildAtZero.getUserObject());

    DefaultMutableTreeNode mapToProxyChildAtOne = (DefaultMutableTreeNode) childAtSix.getChildAt(1);
    assertEquals(2, mapToProxyChildAtOne.getChildCount()); // The proxy object values in the map.
    assertEquals("two", mapToProxyChildAtOne.getUserObject());

    // ProxyCollection and it's children
    DefaultMutableTreeNode childAtSeven = (DefaultMutableTreeNode) node.getChildAt(7);
    assertEquals(1, childAtSeven.getChildCount());
    assertEquals("ProxyCollection", childAtSeven.getUserObject());

    DefaultMutableTreeNode ProxyCollectionChildAtZero = (DefaultMutableTreeNode) childAtSeven.getChildAt(0);
    assertEquals(11, ProxyCollectionChildAtZero.getChildCount()); // The child proxy object attributes.
    assertEquals("aName", ProxyCollectionChildAtZero.getUserObject());  // Name derived from child.

    // ProxyList and it's children
    DefaultMutableTreeNode childAtEight = (DefaultMutableTreeNode) node.getChildAt(8);
    assertEquals(1, childAtEight.getChildCount());
    assertEquals("ProxyList", childAtEight.getUserObject());

    DefaultMutableTreeNode ProxyListChildAtZero = (DefaultMutableTreeNode) childAtEight.getChildAt(0);
    assertEquals(11, ProxyListChildAtZero.getChildCount()); // The child proxy object attributes.
    assertEquals("aName", ProxyListChildAtZero.getUserObject());  // Name derived from child.

    // StringCollection and it's children
    DefaultMutableTreeNode childAtNine = (DefaultMutableTreeNode) node.getChildAt(9);
    assertEquals(3, childAtNine.getChildCount());
    assertEquals("StringCollection", childAtNine.getUserObject());

    DefaultMutableTreeNode stringCollectionChildAtZero = (DefaultMutableTreeNode) childAtNine.getChildAt(0);
    assertEquals(0, stringCollectionChildAtZero.getChildCount());
    assertEquals("one", stringCollectionChildAtZero.getUserObject());

    DefaultMutableTreeNode stringCollectionChildAtOne = (DefaultMutableTreeNode) childAtNine.getChildAt(1);
    assertEquals(0, stringCollectionChildAtOne.getChildCount());
    assertEquals("three", stringCollectionChildAtOne.getUserObject()); // Sorted alphabetically

    DefaultMutableTreeNode stringCollectionChildAtTwo = (DefaultMutableTreeNode) childAtNine.getChildAt(2);
    assertEquals(0, stringCollectionChildAtTwo.getChildCount());
    assertEquals("two", stringCollectionChildAtTwo.getUserObject());

    // StringSet and it's children
    DefaultMutableTreeNode childAtTen = (DefaultMutableTreeNode) node.getChildAt(10);
    assertEquals(2, childAtTen.getChildCount());
    assertEquals("StringSet", childAtTen.getUserObject());

    DefaultMutableTreeNode stringSetChildAtZero = (DefaultMutableTreeNode) childAtTen.getChildAt(0);
    assertEquals(0, stringSetChildAtZero.getChildCount());
    assertEquals("a", stringSetChildAtZero.getUserObject());

    DefaultMutableTreeNode stringSetChildAtOne = (DefaultMutableTreeNode) childAtTen.getChildAt(1);
    assertEquals(0, stringSetChildAtOne.getChildCount());
    assertEquals("b", stringSetChildAtOne.getUserObject());

  }

  private MyInterface createProxyInstance(boolean recurse) {
    final MyInterfaceImpl delegate = new MyInterfaceImpl(recurse);
    return (MyInterface)Proxy.newProxyInstance(MyInterface.class.getClassLoader(), new Class[]{MyInterface.class}, new InvocationHandler() {
      @Override
      public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
        try {
          return method.invoke(delegate, objects);
        }
        catch (InvocationTargetException e) {
          throw e.getCause();
        }
      }
    });
  }

  @SuppressWarnings("unused") // accessed via reflection
  public interface MyInterface {
    String getName();

    File getFile();

    File getFileUnderProject();

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

  public class MyInterfaceImpl implements MyInterface {

    final boolean recurse;

    MyInterfaceImpl(boolean recurse) {
      this.recurse = recurse;
    }

    @Override
    public String getName() {
      return "aName";
    }

    @Override
    public File getFile() {
      return new File("/a/sample/file");
    }

    @Override
    public File getFileUnderProject() {
      return new File(getProject().getBasePath() + "/b/sample/file");
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

      return ImmutableMap.<String, Collection<MyInterface>>of("one", Sets.<MyInterface>newHashSet(new MyInterfaceImpl(false)), "two",
                                                              Lists.newArrayList(createProxyInstance(false), createProxyInstance(false)));
    }

    @Override
    public boolean doesNotExist() throws UnsupportedMethodException {
      throw new UnsupportedMethodException("This method doesn't exist");
    }
  }
}
