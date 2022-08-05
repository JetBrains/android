/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.devicemanager.physicaltab.Battery.ChargingType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BatteryTest {
  private static final Object AC = new Battery(ChargingType.AC, 73);
  private static final Object WIRELESS = new Battery(ChargingType.WIRELESS, 73);
  private static final Object NOT_CHARGING = new Battery(ChargingType.NOT_CHARGING, 73);

  @Test
  public void newBatteryAc() {
    // Arrange
    List<String> output = Arrays.asList("Current Battery Service state:",
                                        "  AC powered: true",
                                        "  USB powered: false",
                                        "  Wireless powered: false",
                                        "  Max charging current: 500000",
                                        "  Max charging voltage: 5000000",
                                        "  Charge counter: 2002000",
                                        "  status: 2",
                                        "  health: 2",
                                        "  present: true",
                                        "  level: 73",
                                        "  scale: 100",
                                        "  voltage: 4184",
                                        "  temperature: 297",
                                        "  technology: Li-ion",
                                        "");

    // Act
    Object battery = Battery.newBattery(output);

    // Assert
    assertEquals(Optional.of(AC), battery);
  }

  @Test
  public void newBatteryUsb() {
    // Arrange
    List<String> output = Arrays.asList("Current Battery Service state:",
                                        "  AC powered: false",
                                        "  USB powered: true",
                                        "  Wireless powered: false",
                                        "  Max charging current: 500000",
                                        "  Max charging voltage: 5000000",
                                        "  Charge counter: 2002000",
                                        "  status: 2",
                                        "  health: 2",
                                        "  present: true",
                                        "  level: 73",
                                        "  scale: 100",
                                        "  voltage: 4184",
                                        "  temperature: 297",
                                        "  technology: Li-ion",
                                        "");

    // Act
    Object battery = Battery.newBattery(output);

    // Assert
    assertEquals(Optional.of(new Battery(ChargingType.USB, 73)), battery);
  }

  @Test
  public void newBatteryWireless() {
    // Arrange
    List<String> output = Arrays.asList("Current Battery Service state:",
                                        "  AC powered: false",
                                        "  USB powered: false",
                                        "  Wireless powered: true",
                                        "  Max charging current: 500000",
                                        "  Max charging voltage: 5000000",
                                        "  Charge counter: 2002000",
                                        "  status: 2",
                                        "  health: 2",
                                        "  present: true",
                                        "  level: 73",
                                        "  scale: 100",
                                        "  voltage: 4184",
                                        "  temperature: 297",
                                        "  technology: Li-ion",
                                        "");

    // Act
    Object battery = Battery.newBattery(output);

    // Assert
    assertEquals(Optional.of(WIRELESS), battery);
  }

  @Test
  public void newBattery() {
    // Arrange
    List<String> output = Arrays.asList("Current Battery Service state:",
                                        "  AC powered: false",
                                        "  USB powered: false",
                                        "  Wireless powered: false",
                                        "  Max charging current: 500000",
                                        "  Max charging voltage: 5000000",
                                        "  Charge counter: 2002000",
                                        "  status: 2",
                                        "  health: 2",
                                        "  present: true",
                                        "  level: 73",
                                        "  scale: 100",
                                        "  voltage: 4184",
                                        "  temperature: 297",
                                        "  technology: Li-ion",
                                        "");

    // Act
    Object battery = Battery.newBattery(output);

    // Assert
    assertEquals(Optional.of(NOT_CHARGING), battery);
  }

  @Test
  public void toStringNotCharging() {
    // Act
    Object string = NOT_CHARGING.toString();

    // Assert
    assertEquals("Battery 73%", string);
  }

  @Test
  public void toStringWireless() {
    // Act
    Object string = WIRELESS.toString();

    // Assert
    assertEquals("Wireless", string);
  }

  @Test
  public void toStringAc() {
    // Act
    Object string = AC.toString();

    // Assert
    assertEquals("AC", string);
  }
}
