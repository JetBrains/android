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
package com.android.tools.idea.wizard.dynamic;

import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * Test cases for the {@link ScopedStateStore}
 */
public class ScopedStateStoreTest extends TestCase {

  private ScopedStateStore myStepState;
  private ScopedStateStore myPathState;
  private ScopedStateStore myWizardState;

  private List<Key> myUpdateHistory = Lists.newLinkedList();

  private ScopedStateStore.ScopedStoreListener myScopedStoreListener = new ScopedStateStore.ScopedStoreListener() {
    @Override
    public <T> void invokeUpdate(@Nullable Key<T> changedKey) {
      myUpdateHistory.add(changedKey);
    }
  };

  private void createAndLinkStates() {
    myWizardState = new ScopedStateStore(WIZARD, null, myScopedStoreListener);
    myPathState = new ScopedStateStore(PATH, myWizardState, myScopedStoreListener);
    myStepState = new ScopedStateStore(STEP, myPathState, myScopedStoreListener);
  }

  public void testBasics() throws Exception {
    myPathState = new ScopedStateStore(PATH, null, myScopedStoreListener);
    Key<String> testKey = myPathState.createKey("test", String.class);
    myPathState = new ScopedStateStore(PATH, null, myScopedStoreListener);
    myPathState.put(testKey, "value");
    assertEquals("value", myPathState.get(testKey));
    assertTrue(myUpdateHistory.contains(testKey));
    assertTrue(myPathState.getRecentUpdates().contains(testKey));

    assertEquals(1, myUpdateHistory.size());
    myPathState.remove(testKey);
    assertEquals(2, myUpdateHistory.size());
    assertNull(myPathState.get(testKey));

    // Test null values
    Key<String> testKey2 = myPathState.createKey("test2", String.class);
    myPathState.put(testKey2, null);
    assertNull(myPathState.get(testKey2));

    Key<String> testKeyStep = createKey("test2", STEP, String.class);
    try {
      myPathState.put(testKeyStep, "value");
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      myPathState.remove(testKeyStep);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    Key<String> testKeyWizard = createKey("test2", WIZARD, String.class);
    try {
      myPathState.put(testKeyWizard, "value");
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      myPathState.remove(testKeyWizard);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testScoping() throws Exception {
    createAndLinkStates();
    Object value1 = new Object();
    Key<Object> key1 = createKey("value1", STEP, Object.class);
    myStepState.put(key1, value1);
    // The value should appear in the step state but not the other states
    assertEquals(value1, myStepState.get(key1));
    assertTrue(myUpdateHistory.contains(key1));
    assertEquals(1, myUpdateHistory.size());

    // Test inserting into a low scope and bubbling up
    myUpdateHistory.clear();
    Object value2 = new Object();
    Key<Object> key2 = createKey("value2", PATH, Object.class);
    myStepState.put(key2, value2);
    assertEquals(value2, myPathState.get(key2));
    assertEquals(value2, myStepState.get(key2));

    // We should get an update for both the path state and the step state
    assertEquals(2, myUpdateHistory.size());

    // Check flatten and the scoped contents
    Map<String, Object> expectedContents = ImmutableMap.of("value1", value1, "value2", value2);
    assertEquals(expectedContents, myStepState.flatten());
    expectedContents = ImmutableMap.of("value2", value2);
    assertEquals(expectedContents, myPathState.flatten());
    expectedContents = ImmutableMap.of();
    assertEquals(expectedContents, myWizardState.flatten());
  }

  public void testPutAll() throws Exception {
    createAndLinkStates();
    Object value1 = new Object();
    Object value2 = new Object();
    Key<Object> key1 = createKey("value1", PATH, Object.class);
    Key<Object> key2 = createKey("value2", PATH, Object.class);
    Map<Key<Object>,Object> myValues = ImmutableMap.of(key1, value1, key2, value2);
    myStepState.putAll(myValues);

    Map<String, Object> expectedMap = ImmutableMap.of("value1", value1, "value2", value2);

    assertEquals(expectedMap, myPathState.flatten());
    assertEquals(expectedMap, myStepState.flatten());
    assertEquals(new HashMap<String, Object>(), myWizardState.flatten());
  }

  public void testListOperations() throws Exception {
    myPathState = new ScopedStateStore(PATH, null, myScopedStoreListener);
    @SuppressWarnings("unchecked")
    Key<List<String>> listKey = createKey("list", PATH, (Class<List<String>>) (Class) List.class);
    assertFalse(myPathState.containsKey(listKey));

    assertTrue(myPathState.listPush(listKey, "hello"));
    assertTrue(myPathState.listPush(listKey, "world"));

    assertTrue(myPathState.listRemove(listKey, "hello"));
    assertFalse(myPathState.listRemove(listKey, "hello"));

    assertEquals(Lists.newArrayList("world"), myPathState.get(listKey));
  }
}
