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
package com.android.tools.adtui.workbench;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class WorkBenchManagerTest {
  private WorkBench myWorkBench1, myWorkBench2, myWorkBench3, myWorkBench4, myWorkBench5;

  private WorkBenchManager myManager;

  @Before
  public void before() {
    myManager = new WorkBenchManager();
    myWorkBench1 = createWorkBench("A");
    myWorkBench2 = createWorkBench("A");
    myWorkBench3 = createWorkBench("A");
    myWorkBench4 = createWorkBench("B");
    myWorkBench5 = createWorkBench("B");
  }

  @Test
  public void testUpdateOtherWorkBenches() {
    myManager.updateOtherWorkBenches(myWorkBench2);
    verify(myWorkBench1).updateModel();
    verify(myWorkBench2, never()).updateModel();
    verify(myWorkBench3).updateModel();
    verify(myWorkBench4, never()).updateModel();
    verify(myWorkBench5, never()).updateModel();
  }

  @Test
  public void testUpdateOtherWorkBenchesAfterUnregister() {
    myManager.unregister(myWorkBench3);
    myManager.updateOtherWorkBenches(myWorkBench2);
    verify(myWorkBench1).updateModel();
    verify(myWorkBench2, never()).updateModel();
    verify(myWorkBench3, never()).updateModel();
    verify(myWorkBench4, never()).updateModel();
    verify(myWorkBench5, never()).updateModel();
  }

  @Test
  public void testStoreDefaultLayout() {
    myManager.storeDefaultLayout();
    verify(myWorkBench1).storeDefaultLayout();
    verify(myWorkBench4).storeDefaultLayout();
  }

  @Test
  public void testRestoreDefaultLayout() {
    myManager.restoreDefaultLayout();
    verify(myWorkBench1).restoreDefaultLayout();
    verify(myWorkBench2).updateModel();
    verify(myWorkBench3).updateModel();
    verify(myWorkBench4).restoreDefaultLayout();
    verify(myWorkBench5).updateModel();
  }

  private WorkBench createWorkBench(@NotNull String name) {
    WorkBench workBench = mock(WorkBench.class);
    when(workBench.getName()).thenReturn(name);
    myManager.register(workBench);
    return workBench;
  }
}
