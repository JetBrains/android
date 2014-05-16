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
package com.android.tools.idea.wizard;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.*;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

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
    assertEquals(new Pair<String, Key<String>>("value", testKey), myPathState.get(testKey));
    assertTrue(myUpdateHistory.contains(testKey));
    assertTrue(myPathState.getRecentUpdates().contains(testKey));

    assertEquals(1, myUpdateHistory.size());
    myPathState.remove(testKey);
    assertEquals(2, myUpdateHistory.size());
    assertNull(myPathState.get(testKey).first);
    assertNull(myPathState.get(testKey).second);

    // Test null values
    Key<String> testKey2 = myPathState.createKey("test2", String.class);
    myPathState.put(testKey2, null);
    assertNull(myPathState.get(testKey2).first);
    assertNotNull(myPathState.get(testKey2).second);

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
    assertNull(myWizardState.get(key1).second);
    assertNull(myPathState.get(key1).second);
    Key<Object> scopedKey1 = myStepState.createKey("value1", Object.class);
    assertEquals(new Pair<Object, Key<Object>>(value1, scopedKey1), myStepState.get(key1));
    assertTrue(myUpdateHistory.contains(key1));
    assertEquals(1, myUpdateHistory.size());

    // Test inserting into a low scope and bubbling up
    myUpdateHistory.clear();
    Object value2 = new Object();
    Key<Object> key2 = createKey("value2", PATH, Object.class);
    myStepState.put(key2, value2);
    assertNull(myWizardState.get(key2).second);
    assertEquals(new Pair<Object, Key<Object>>(value2, key2), myPathState.get(key2));
    assertEquals(new Pair<Object, Key<Object>>(value2, key2), myStepState.get(key2));

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
}
