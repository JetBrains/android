/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ModifyDeviceSetDialogTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private ModifyDeviceSetDialog myDialog;

  @Before
  public void initDialog() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      Project project = myRule.getProject();
      Key key = new Key("Pixel_4_API_29");

      Device device = new VirtualDevice.Builder()
        .setName("Pixel 4 API 29")
        .setKey(key)
        .setAndroidDevice(Mockito.mock(AndroidDevice.class))
        .build();

      PropertiesComponent properties = new ProjectPropertiesComponentImpl();
      Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27Z"), ZoneId.of("America/Los_Angeles"));

      DevicesSelectedService service = new DevicesSelectedService(project, p -> properties, clock);
      service.setDeviceKeysSelectedWithDialog(Collections.singleton(key));

      myDialog = new ModifyDeviceSetDialog(project, new ModifyDeviceSetDialogTableModel(Collections.singletonList(device)), p -> service);
    });
  }

  @After
  public void disposeOfDialog() {
    ApplicationManager.getApplication().invokeAndWait(myDialog::disposeIfNeeded);
  }

  @Test
  public void initTable() {
    // Act
    myDialog.getTable().setSelected(false, 0);

    // Assert
    assertTrue(myDialog.isOKActionEnabled());
  }
}
