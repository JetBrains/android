/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.SwingWorker;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ConnectedDevicesWorkerDelegateTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private SwingWorker myDelegate;

  @Test
  public void construct() {
    // Arrange
    Function<Project, Stream<IDevice>> getDdmlibDevices = project -> {
      IDevice device = Mockito.mock(IDevice.class);
      Mockito.when(device.getSerialNumber()).thenReturn("emulator-5554");

      return Stream.of(device);
    };

    Runnable newConnectedDevicesWorkerDelegate = () ->
      myDelegate = new ConnectedDevicesWorkerDelegate(myRule.getProject(), null, getDdmlibDevices);

    ApplicationManager.getApplication().invokeAndWait(newConnectedDevicesWorkerDelegate);

    // Act
    Object devices = myDelegate.construct();

    // Assert
    assertEquals(Collections.emptyList(), devices);
  }
}
