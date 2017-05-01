/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.flags;

import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.Flags;
import com.android.flags.overrides.PropertyOverrides;

/**
 * A collection of all feature flags used by Android Studio. These flags can be used to gate
 * features entirely or branch internal logic of features, e.g. for experimentation or easy
 * rollback.
 *
 * For information on how to add your own flags, see the README.md file under
 * "//tools/base/flags".
 */
public final class StudioFlags {
  private static final Flags FLAGS = new Flags(new PropertyOverrides());

  private static final FlagGroup NPW = new FlagGroup(FLAGS, "npw", "New Project Wizard");
  public static final Flag<Boolean> NPW_NEW_PROJECT = Flag.create(
    NPW, "new.project", "Migrate \"New Project\"",
    "Use the new wizard framework for the \"New > New Project...\" wizard flow.",
    true);

  public static final Flag<Boolean> NPW_NEW_MODULE = Flag.create(
    NPW, "new.module", "Migrate \"New Module\"",
    "Use the new wizard framework for the \"New > New Module...\" wizard flow.",
    false);

  public static final Flag<Boolean> NPW_IMPORT_MODULE = Flag.create(
    NPW, "import.module", "Migrate \"Import Module\"",
    "Use the new wizard framework for the \"New > Import Module...\" wizard flow.",
    true);

  public static final Flag<Boolean> NPW_GALLERY = Flag.create(
    NPW, "gallery", "Migrate \"Gallery\"",
    "Use the new wizard framework when user selects \"New > Activity > Gallery...\" from the right-click context menu.",
    true);

  public static final Flag<Boolean> NPW_KOTLIN = Flag.create(
    NPW, "kotlin", "Enable Kotlin projects",
    "Add an option in the new wizard flow to create a Kotlin project.",
    true);

  private StudioFlags() {
  }
}
