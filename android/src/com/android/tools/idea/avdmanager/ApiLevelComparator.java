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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.SdkVersionInfo;

import java.util.Comparator;

/** Compared API level strings numerically when possible */
public class ApiLevelComparator implements Comparator<String> {
  @Override
  public int compare(String s1, String s2) {
    int api1 = -1; // not a valid API level
    int api2 = -1;
    try {
      if (!s1.isEmpty() && Character.isDigit(s1.charAt(0))) {
        api1 = Integer.parseInt(s1);
      } else {
        api1 = SdkVersionInfo.getApiByPreviewName(s1, false);
      }
    }
    catch (NumberFormatException e) {
      // ignore; still negative value
    }
    try {
      if (!s2.isEmpty() && Character.isDigit(s2.charAt(0))) {
        api2 = Integer.parseInt(s2);
      } else {
        api2 = SdkVersionInfo.getApiByPreviewName(s2, false);
      }
    }
    catch (NumberFormatException e) {
      // ignore; still negative value
    }
    if (api1 != -1 && api2 != -1) {
      return api1 - api2; // descending order
    }
    else if (api1 != -1) {
      // Only the first value is a number: Sort preview platforms to the end
      return -1;
    }
    else if (api2 != -1) {
      // Only the second value is a number: Sort preview platforms to the end
      return 1;
    }
    else {
      // Alphabetic sort when both API versions are codenames
      return s1.compareTo(s2);
    }
  }
}
