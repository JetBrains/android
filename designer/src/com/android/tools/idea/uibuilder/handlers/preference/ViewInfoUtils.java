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
package com.android.tools.idea.uibuilder.handlers.preference;

import com.android.ide.common.rendering.api.ViewInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static com.android.SdkConstants.FQCN_LIST_VIEW;

final class ViewInfoUtils {
  private ViewInfoUtils() {
  }

  @Nullable
  static ViewInfo findViewWithName(@NotNull Collection<ViewInfo> rootViews, @NotNull String name) {
    return rootViews.stream()
      .map(rootView -> findViewWithName(rootView, name))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static ViewInfo findViewWithName(@NotNull ViewInfo parent, @NotNull String name) {
    if (parent.getClassName().equals(name)) {
      return parent;
    }

    Optional<ViewInfo> view = parent.getChildren().stream()
      .map(child -> findViewWithName(child, name))
      .filter(Objects::nonNull)
      .findFirst();

    return view.orElse(null);
  }
}
