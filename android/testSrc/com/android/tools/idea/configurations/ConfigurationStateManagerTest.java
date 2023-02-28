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
package com.android.tools.idea.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

public class ConfigurationStateManagerTest extends AndroidTestCase {
  public void test1() {
    Project project = getProject();
    assertNotNull(project);

    StudioConfigurationStateManager manager = (StudioConfigurationStateManager)StudioConfigurationStateManager.get(project);
    assertNotNull(manager);
    assertSame(manager, StudioConfigurationStateManager.get(project));

    manager.getProjectState().setLocale("en-rUS");
    assertEquals("en-rUS", manager.getProjectState().getLocale());

    ConfigurationStateManager.State firstState = manager.getState();
    manager.setProjectState(new ConfigurationProjectState());

    manager.getProjectState().setLocale("de");
    assertEquals("de", manager.getProjectState().getLocale());

    ConfigurationStateManager.State secondState = manager.getState();
    manager.loadState(firstState);
    assertEquals("en-rUS", manager.getProjectState().getLocale());
    manager.loadState(secondState);
    manager.getProjectState().setLocale("de");

    assertTrue(manager.getProjectState().isPickTarget());
    manager.getProjectState().setPickTarget(false);
    assertFalse(manager.getProjectState().isPickTarget());
  }

  public void test2() {
    Project project = getProject();
    assertNotNull(project);

    ConfigurationStateManager manager = StudioConfigurationStateManager.get(project);
    assertNotNull(manager);
    assertSame(manager, StudioConfigurationStateManager.get(project));

    VirtualFile file = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout.xml");
    ConfigurationFileState configState1 = new ConfigurationFileState();
    configState1.setTheme("@style/Theme.Holo.Light");
    configState1.setDeviceState("port");
    manager.setConfigurationState(file, configState1);

    ConfigurationStateManager.State state1 = manager.getState();

    ConfigurationFileState configState2 = new ConfigurationFileState();
    configState2.setTheme("@style/Theme.Dialog");
    configState2.setDeviceState("land");
    manager.setConfigurationState(file, configState2);

    ConfigurationStateManager.State state2 = manager.getState();

    ConfigurationFileState retrievedState1 = manager.getConfigurationState(file);
    assertNotNull(retrievedState1);
    assertEquals("@style/Theme.Dialog", retrievedState1.getTheme());
    assertEquals("land", retrievedState1.getDeviceState());

    manager.loadState(state1);

    ConfigurationFileState retrievedState2 = manager.getConfigurationState(file);
    assertNotNull(retrievedState2);
    assertEquals("@style/Theme.Holo.Light", retrievedState2.getTheme());
    assertEquals("port", retrievedState2.getDeviceState());

    assertNotSame(retrievedState1, configState1);

    manager.loadState(state2);

    ConfigurationFileState retrievedState3 = manager.getConfigurationState(file);
    assertNotNull(retrievedState3);
    assertEquals("@style/Theme.Dialog", retrievedState3.getTheme());
    assertEquals("land", retrievedState3.getDeviceState());
  }
}
