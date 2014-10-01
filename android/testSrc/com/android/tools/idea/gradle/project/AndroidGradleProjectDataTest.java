/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class AndroidGradleProjectDataTest extends AndroidGradleTestCase {

  MyInterface myProxy;

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

  private static MyInterface createProxyInstance(boolean recurse) {
    final MyInterfaceImpl delegate = new MyInterfaceImpl(recurse);
    return (MyInterface)Proxy.newProxyInstance(MyInterface.class.getClassLoader(), new Class[]{MyInterface.class}, new InvocationHandler() {
        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
          try {
            return method.invoke(delegate, objects);
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
        }
      });
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
      assertTrue("Unsupported type " + clazz, AndroidGradleProjectData.isSupported(clazz));
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProxy = createProxyInstance(true);
  }

  public void testReproxy() throws Exception {
    MyInterface reproxy = AndroidGradleProjectData.reproxy(MyInterface.class, myProxy);
    assertNotSame(reproxy, myProxy);
    assertNotNull(reproxy);
    assertProxyEquals(myProxy, reproxy);
  }

  public void testEquality() throws Exception {
    MyInterface reproxy = AndroidGradleProjectData.reproxy(MyInterface.class, myProxy);
    MyInterface reproxy2 = AndroidGradleProjectData.reproxy(MyInterface.class, myProxy);
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
    MyInterface reproxy = AndroidGradleProjectData.reproxy(MyInterface.class, myProxy);
    assertNotNull(myProxy.toString());
    assertNotNull(reproxy);
    assertNotNull(reproxy.toString());
  }

  public void testSupportedTypes() throws Exception {
    assertTypeIsSupported(AndroidProject.class.getPackage(), AndroidProject.class);
  }

  public void testEndToEnd() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("AndroidGradleProjectDataTest.testEndToEnd temporarily disabled");
      return;
    }
    loadProject("projects/projectWithAppandLib");

    Project project = myAndroidFacet.getModule().getProject();
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    long previousSyncTime = syncState.getLastGradleSyncTimestamp();

    AndroidGradleProjectData data = AndroidGradleProjectData.createFrom(project);
    assertNotNull(data);

    Map<String, byte[]> checksums = data.getFileChecksums();
    assertEquals(7, checksums.size());
    assertContainsElements(checksums.keySet(), "gradle.properties", "local.properties", "build.gradle", "settings.gradle",
                           "app/build.gradle", "lib/build.gradle");
    String home = System.getProperty("user.home");
    if (home != null) {
      File userProperties = new File(new File(home), ".gradle/gradle.properties");
      assertContainsElements(checksums.keySet(), userProperties.getPath());
    }

    Map<String, AndroidGradleProjectData.ModuleData> modules = data.getModuleData();
    assertEquals(3, modules.size());


    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(outputStream);
    oos.writeObject(data);
    oos.close();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(inputStream);
    AndroidGradleProjectData newData = (AndroidGradleProjectData)ois.readObject();
    ois.close();

    // Clear the sync state to make sure we set it correctly.
    syncState.resetTimestamp();
    assertTrue(newData.applyTo(project));

    assertEquals(previousSyncTime, syncState.getLastGradleSyncTimestamp());

    // Test applying without a module.
    String moduleName = myAndroidFacet.getModule().getName();
    Map<String, AndroidGradleProjectData.ModuleData> newModules = newData.getModuleData();
    newModules.remove(moduleName);
    assertFalse(newData.applyTo(project));
  }

  public void testNewMethod() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    MyInterface reproxy = AndroidGradleProjectData.reproxy(MyInterface.class, myProxy);

    assertNotNull(reproxy);
    InvocationHandler handler = Proxy.getInvocationHandler(reproxy);
    assertTrue(handler instanceof AndroidGradleProjectData.WrapperInvocationHandler);
    AndroidGradleProjectData.WrapperInvocationHandler wrapper = (AndroidGradleProjectData.WrapperInvocationHandler)handler;
    Method m = MyInterface.class.getMethod("getString");
    wrapper.values.remove(m.toGenericString());

    try {
      reproxy.getString();
      fail("Removed method should throw an exception");
    } catch (UnsupportedMethodException e) {
      // Expected.
    }
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

      return ImmutableMap.<String, Collection<MyInterface>>of("one", Sets.<MyInterface>newHashSet(new MyInterfaceImpl(false)), "two",
                                                              Lists.newArrayList(createProxyInstance(false), createProxyInstance(false)));
    }

    @Override
    public boolean doesNotExist() throws UnsupportedMethodException {
      throw new UnsupportedMethodException("This method doesn't exist");
    }
  }
}
