/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.testing.swing.TableModelEventArgumentMatcher;
import java.util.Collections;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableModelTest {
  @Test
  public void setOnline() {
    // Arrange
    AvdInfo avd = Mockito.mock(AvdInfo.class);
    TableModelListener listener = Mockito.mock(TableModelListener.class);

    VirtualDeviceTableModel model = new VirtualDeviceTableModel(Collections.singletonList(TestVirtualDevices.pixel5Api31(avd)));
    model.addTableModelListener(listener);

    Key key = new VirtualDeviceName("Pixel_5_API_31");

    // Act
    model.setOnline(key, true);

    // Assert
    Object device = new VirtualDevice.Builder()
      .setKey(key)
      .setName("Pixel 5 API 31")
      .setOnline(true)
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setAndroidVersion(new AndroidVersion(31))
      .setAvdInfo(avd)
      .build();

    assertEquals(Collections.singletonList(device), model.getDevices());

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));
  }
}
