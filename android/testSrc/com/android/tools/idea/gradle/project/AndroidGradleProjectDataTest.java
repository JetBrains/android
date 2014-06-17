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
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final MyInterfaceImpl delegate = new MyInterfaceImpl();
    myProxy =
      (MyInterface)Proxy.newProxyInstance(MyInterface.class.getClassLoader(), new Class[]{MyInterface.class}, new InvocationHandler() {
        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
          return method.invoke(delegate, objects);
        }
      });
  }

  public void testReproxy() throws Exception {
    MyInterface reproxy = AndroidGradleProjectData.reproxy(MyInterface.class, myProxy);
    assertNotSame(reproxy, myProxy);
    assertProxyEquals(myProxy, reproxy);
  }

  public void testSupportedTypes() throws Exception {
    assertTypeIsSupported(AndroidProject.class.getPackage(), AndroidProject.class);
  }

  private void assertTypeIsSupported(Package reproxy, Class<?> clazz) {
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

    Map<String, byte[]> checksums = data.getFileChecksums();
    assertEquals(4, checksums.size());
    assertContainsElements(checksums.keySet(), "build.gradle", "settings.gradle", "app/build.gradle", "lib/build.gradle");

    Map<String, AndroidGradleProjectData.ModuleData> modules = data.getModuleData();
    assertEquals(3, modules.size());

    AndroidGradleProjectData.ModuleData moduleData = modules.get(myAndroidFacet.getModule().getName());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(outputStream);
    oos.writeObject(data);
    oos.close();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(inputStream);
    AndroidGradleProjectData newData = (AndroidGradleProjectData)ois.readObject();
    ois.close();

    // Clear the sync state to make sure we set it correctly.
    syncState.setLastGradleSyncTimestamp(-1L);
    newData.applyTo(project);

    assertEquals(previousSyncTime, syncState.getLastGradleSyncTimestamp());
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
  }

  static class MyInterfaceImpl implements MyInterface {

    final boolean recurse;

    MyInterfaceImpl(boolean recurse) {
      this.recurse = recurse;
    }

    MyInterfaceImpl() {
      this(true);
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
      return recurse ? Sets.<MyInterface>newHashSet(new MyInterfaceImpl(false)) : null;
    }

    @Override
    public List<Boolean> getBooleanList() {
      return ImmutableList.of(false, true);
    }

    @Override
    public List<MyInterface> getProxyList() {
      return recurse ? Lists.<MyInterface>newArrayList(new MyInterfaceImpl(false)) : null;
    }

    @Override
    public Set<String> getStringSet() {
      return Sets.newHashSet("a", "a", "b");
    }

    @Override
    public Map<String, Collection<MyInterface>> getMapToProxy() {
      if (!recurse) return null;

      return ImmutableMap.<String, Collection<MyInterface>>of("one", Sets.<MyInterface>newHashSet(new MyInterfaceImpl(false)), "two", Lists
        .<MyInterface>newArrayList(new MyInterfaceImpl(false), new MyInterfaceImpl(false)));
    }
  }
}
