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
package com.android.tools.idea.avdmanager.ui;

import com.android.tools.idea.avdmanager.ui.ApiLevelComparator;
import junit.framework.TestCase;

public class ApiLevelComparatorTest extends TestCase {
  public void test() {
    assertTrue(new ApiLevelComparator().compare("5", "6") < 0);
    assertTrue(new ApiLevelComparator().compare("6", "5") > 0);
    assertTrue(new ApiLevelComparator().compare("5", "5") == 0);

    assertTrue(new ApiLevelComparator().compare("9", "10") < 0);
    assertTrue(new ApiLevelComparator().compare("10", "9") > 0);

    assertTrue(new ApiLevelComparator().compare("a", "b") < 0);
    assertTrue(new ApiLevelComparator().compare("b", "a") > 0);
    assertTrue(new ApiLevelComparator().compare("a", "a") == 0);

    // When mixing, place codenames last
    assertTrue(new ApiLevelComparator().compare("19", "A") < 0);
    assertTrue(new ApiLevelComparator().compare("A", "19") > 0);

    // ...unless it's a known codename:
    assertTrue(new ApiLevelComparator().compare("Lollipop", "22") < 0);
    assertTrue(new ApiLevelComparator().compare("22", "Lollipop") > 0);
    assertTrue(new ApiLevelComparator().compare("Lollipop", "19") > 0);
    assertTrue(new ApiLevelComparator().compare("19", "Lollipop") < 0);
  }
}