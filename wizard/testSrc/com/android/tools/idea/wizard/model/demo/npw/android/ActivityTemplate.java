/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.wizard.model.demo.npw.android;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * A mock representation of Activity FreeMarker templates (normally loaded from disk).
 */
public final class ActivityTemplate {
  public static final ActivityTemplate MOBILE_BLANK = new ActivityTemplate("Blank Activity", "Activity Name", "Layout Name", "Title");
  public static final ActivityTemplate MOBLIE_EMPTY = new ActivityTemplate("Empty Activity", "Activity Name", "Layout Name");
  public static final ActivityTemplate MOBILE_ADMOB =
    new ActivityTemplate("Google AdMob Ads Activity", "Activity Name", "Layout Name", "Title", "Menu Resource Name", "Ad Format");
  public static final ActivityTemplate WEAR_ALWAYS_ON = new ActivityTemplate("Always On Wear Activity", "Activity Name", "Layout Name");
  public static final ActivityTemplate WEAR_BLANK =
    new ActivityTemplate("Blank Wear Activity", "Activity Name", "Layout Name", "Round Layout Name", "Rectangular Layout Name");
  public static final ActivityTemplate WEAR_FACE = new ActivityTemplate("Watch Face", "Service Name", "Layout Name", "Style");
  public static final ActivityTemplate TV =
    new ActivityTemplate("Android TV Activity", "Activity Name", "Main Layout Name", "Main Fragment", "Title", "Details Activity",
                         "Details Layout Name", "Details Fragment");

  @NotNull private final String myName;
  @NotNull private final List<String> myParameters;

  public ActivityTemplate(@NotNull String name, @NotNull String... parameters) {
    myName = name;
    myParameters = Arrays.asList(parameters);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public List<String> getParameters() {
    return myParameters;
  }
}
