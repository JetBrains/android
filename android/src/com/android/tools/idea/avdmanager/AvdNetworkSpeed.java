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
package com.android.tools.idea.avdmanager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A list of supported Network standards, which, if set, dictate maximum emulator network speeds.
 */
public enum AvdNetworkSpeed {
  FULL("Full"),  // Emulator won't restrict network speed
  LTE("LTE"), // Long Term Evolution (4G)
  HSDPA("HSDPA"), // High-Speed Downlink Packet Access (3.5G)
  UMTS("UMTS"), // Universal Mobile Telecommunications System (3G)
  EDGE("EDGE"), // Enhanced Data Rates for GSM Evolution (Pre-3G)
  GPRS("GPRS"), // General Packet Radio Service (2.5G)
  HSCSD("HSCSD"), // High-Speed Circuit-Switched Data (4x faster than GSM)
  GSM("GSM"); // Global System for Mobile Communications (2G)

  @NotNull private final String myName;

  AvdNetworkSpeed(@NotNull String name) {
    myName = name;
  }

  public static AvdNetworkSpeed fromName(@Nullable String name) {
    for (AvdNetworkSpeed type : AvdNetworkSpeed.values()) {
      if (type.myName.equalsIgnoreCase(name)) {
        return type;
      }
    }
    return FULL;
  }

  /**
   * The value needs to be converted before sent off to the emulator as a valid parameter
   */
  @NotNull
  public String getAsParameter() {
    return myName.toLowerCase(Locale.US);
  }

  @Override
  public String toString() {
    return myName;
  }
}
