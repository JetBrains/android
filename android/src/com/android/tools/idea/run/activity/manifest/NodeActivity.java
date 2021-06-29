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
package com.android.tools.idea.run.activity.manifest;

import com.android.tools.idea.run.activity.DefaultActivityLocator;
import com.android.tools.manifest.parser.components.IntentFilter;
import com.android.tools.manifest.parser.components.ManifestActivityInfo;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of {@link DefaultActivityLocation.ActivityWrapper} using a
 * {@link ManifestActivityInfo} as source of data.
 */
public class NodeActivity extends DefaultActivityLocator.ActivityWrapper {

  protected ManifestActivityInfo activity;

  public NodeActivity(@NotNull ManifestActivityInfo activity) {
    this.activity = activity;
  }

  @Override
  public boolean hasCategory(@NotNull String name) {
    return activity.hasCategory(name);
  }

  @Override
  public boolean hasAction(@NotNull String name) {
    return activity.hasAction(name);
  }

  @Override
  public boolean isEnabled() {
    return activity.isEnabled();
  }

  @Nullable
  @Override
  public Boolean getExported() {
    return activity.getExported();
  }

  @Override
  public boolean hasIntentFilter() {
    return !activity.getIntentFilters().isEmpty();
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return activity.getQualifiedName();
  }

  @NotNull
  public List<IntentFilter> getIntentFilters() {
    return activity.getIntentFilters();
  }
}
