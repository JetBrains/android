/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceDefinitionListTest {
  @Test
  public void deviceDefinitionList() {
    // Arrange
    var definitions = List.of(Definitions.mockPhone(),
                              Definitions.mockTablet(),
                              Definitions.mockWearOsDefinition(),
                              Definitions.mockDesktop(),
                              Definitions.mockDefinition("android-tv", Definitions.mockHardware(0), "Android TV (1080p)", "tv_1080p"),
                              Definitions.mockAutomotiveDefinition(),
                              Definitions.mockLegacyDefinition());

    var supplier = Mockito.mock(DeviceSupplier.class);
    Mockito.when(supplier.get()).thenReturn(definitions);

    // Act
    new DeviceDefinitionList(supplier);
  }
}
