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
package com.android.tools.idea.run.activity;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unit tests for {@link DefaultActivityLocatorUnitTest}
 */
public class DefaultActivityLocatorUnitTest extends TestCase {
  public void testComputeDefaultActivity_emptyActivitiesList() {
    String defaultActivity = DefaultActivityLocator.computeDefaultActivity(ImmutableList.of(), null);
    assertThat(defaultActivity).isNull();
  }

  public void testComputeDefaultActivity_singleNotLaunchableActivity() {
    String defaultActivity =
      DefaultActivityLocator.computeDefaultActivity(ImmutableList.of(new StubActivityWrapper("activity.name", false, false)), null);
    assertThat(defaultActivity).isNull();
  }

  public void testComputeDefaultActivity_singleLaunchableActivity() {
    String defaultActivity =
      DefaultActivityLocator.computeDefaultActivity(ImmutableList.of(new StubActivityWrapper("activity.name", true, false)), null);
    assertThat(defaultActivity).isEqualTo("activity.name");
  }

  public void testComputeDefaultActivity_prefersDefaultActivity() {
    String defaultActivity =
      DefaultActivityLocator.computeDefaultActivity(ImmutableList.of(
        new StubActivityWrapper("activity.name", true, false),
        new StubActivityWrapper("default.activity.name", true, true)
      ), null);
    assertThat(defaultActivity).isEqualTo("default.activity.name");
  }

  public void testComputeDefaultActivity_noDefaultActivityReturnFirstOne() {
    String defaultActivity =
      DefaultActivityLocator.computeDefaultActivity(ImmutableList.of(
        new StubActivityWrapper("activity.name.1", true, false),
        new StubActivityWrapper("activity.name.2", true, false)
      ), null);
    assertThat(defaultActivity).isEqualTo("activity.name.1");
  }


  static class StubActivityWrapper extends DefaultActivityLocator.ActivityWrapper {
    @NotNull final String qualifiedName;
    final boolean launchable;
    final boolean isDefaultActivity;

    StubActivityWrapper(@NotNull String qualifiedName, boolean launchable, boolean isDefaultActivity) {
      this.qualifiedName = qualifiedName;
      this.launchable = launchable;
      this.isDefaultActivity = isDefaultActivity;
    }

    @Override
    public boolean hasCategory(@NotNull String name) {
      if (launchable && (name.equals(AndroidUtils.LAUNCH_CATEGORY_NAME) || name.equals(AndroidUtils.LEANBACK_LAUNCH_CATEGORY_NAME))) {
        return true;
      }
      if (isDefaultActivity && name.equals(AndroidUtils.DEFAULT_CATEGORY_NAME)) {
        return true;
      }
      return false;
    }

    @Override
    public boolean hasAction(@NotNull String name) {
      if (launchable && name.equals(AndroidUtils.LAUNCH_ACTION_NAME)) {
        return true;
      }
      return false;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return qualifiedName;
    }

    @Nullable
    @Override
    public Boolean getExported() { return null; }

    @Override
    public boolean hasIntentFilter() { return false; }
  }
}
