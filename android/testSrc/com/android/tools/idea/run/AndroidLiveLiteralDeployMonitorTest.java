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
package com.android.tools.idea.run;

import org.junit.Assert;
import org.junit.Test;

public class AndroidLiveLiteralDeployMonitorTest {

  @Test
  public void classNameToHelperName() {
    String name = "com.example.compose.MainActivity.Greeting.<anonymous>.<anonymous>.<anonymous>";
    String expected = "com.example.compose.LiveLiterals$MainActivityKt";
    String helper = AndroidLiveLiteralDeployMonitor.qualifyNameToHelperClassName(name);
    Assert.assertEquals(expected, helper);

    name = "com.example.compose.MainActivity.Greeting";
    expected = "com.example.compose.LiveLiterals$MainActivityKt";
    helper = AndroidLiveLiteralDeployMonitor.qualifyNameToHelperClassName(name);
    Assert.assertEquals(expected, helper);

    name = "com.example.compose.MainActivityKt.Greeting";
    expected = "com.example.compose.LiveLiterals$MainActivityKt";
    helper = AndroidLiveLiteralDeployMonitor.qualifyNameToHelperClassName(name);
    Assert.assertEquals(expected, helper);
  }

  @Test
  public void invalidClassNameToHelperName() {
    String name = "Greeting";
    String expected = "no.name.space.from.LiveLiterals$LiveLiteralMonitorKt";
    String helper = AndroidLiveLiteralDeployMonitor.qualifyNameToHelperClassName(name);
    Assert.assertEquals(expected, helper);
  }

}
