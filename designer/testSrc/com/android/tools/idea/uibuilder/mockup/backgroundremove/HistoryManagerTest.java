/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.backgroundremove;

import junit.framework.TestCase;

public class HistoryManagerTest extends TestCase {

  public void testSetOriginalImage() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    final Object firstObject = new Object();
    manager.setOriginalImage(firstObject);
    assertEquals(manager.getCurrentObject(), firstObject);
  }

  public void testSetMaxHistory() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    manager.setMaxHistory(2);
    for (int i = 0; i < 5; i++) {
      manager.pushUndo(new Object());
    }
    manager.undo();
    manager.undo();
    assertFalse(manager.canUndo());
  }

  public void testPushImage() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    final Object firstObject = new Object();
    manager.setOriginalImage(firstObject);
    Object secondObject = new Object();
    manager.pushUndo(secondObject);
    assertEquals(secondObject, manager.getCurrentObject());
  }

  public void testUndo() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    final Object firstObject = new Object();
    manager.setOriginalImage(firstObject);
    Object secondObject = new Object();
    manager.pushUndo(secondObject);
    manager.undo();
    assertEquals(firstObject, manager.getCurrentObject());
  }

  public void testGetCurrentObject() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    final Object firstObject = new Object();
    manager.setOriginalImage(firstObject);
    assertEquals(firstObject, manager.getCurrentObject());
    Object secondObject = new Object();
    manager.pushUndo(secondObject);
    assertEquals(secondObject, manager.getCurrentObject());
  }

  public void testRedo() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    final Object firstObject = new Object();
    manager.setOriginalImage(firstObject);
    assertEquals(firstObject, manager.getCurrentObject());
    Object secondObject = new Object();
    manager.pushUndo(secondObject);
    assertEquals(secondObject, manager.getCurrentObject());
    manager.undo();
    assertEquals(firstObject, manager.getCurrentObject());
    manager.redo();
    assertEquals(secondObject, manager.getCurrentObject());

  }

  public void testCanUndo() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    final Object firstObject = new Object();
    assertFalse(manager.canUndo());
    manager.setOriginalImage(firstObject);
    assertFalse(manager.canUndo());

    Object secondObject = new Object();
    manager.pushUndo(secondObject);
    assertTrue(manager.canUndo());
    manager.pushUndo(new Object());
    assertTrue(manager.canUndo());
    manager.undo();
    assertTrue(manager.canUndo());
    manager.undo();
    assertFalse(manager.canUndo());
  }

  public void testCanRedo() throws Exception {
    final HistoryManager<Object> manager = new HistoryManager<>();
    final Object firstObject = new Object();
    assertFalse(manager.canRedo());
    manager.setOriginalImage(firstObject);
    assertFalse(manager.canRedo());

    Object secondObject = new Object();
    manager.pushUndo(secondObject);
    assertFalse(manager.canRedo());
    manager.undo();
    assertTrue(manager.canRedo());
    manager.pushUndo(secondObject);
    assertFalse(manager.canRedo());
  }
}