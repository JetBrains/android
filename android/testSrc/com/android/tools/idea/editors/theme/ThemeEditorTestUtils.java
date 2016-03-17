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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class ThemeEditorTestUtils {
  private ThemeEditorTestUtils() {}

  /**
   * Returns the attributes that were defined in the theme itself and not its parents.
   */
  public static Collection<EditedStyleItem> getStyleLocalValues(@NotNull final ConfiguredThemeEditorStyle style) {
    final Set<String> localAttributes = Sets.newHashSet();
    for (ConfiguredElement<ItemResourceValue> value : style.getConfiguredValues()) {
      localAttributes.add(ResolutionUtils.getQualifiedItemName(value.getElement()));
    }

    return Collections2
      .filter(ThemeAttributeResolver.resolveAll(style, style.getConfiguration().getConfigurationManager()), new Predicate<EditedStyleItem>() {
        @Override
        public boolean apply(@Nullable EditedStyleItem input) {
          assert input != null;
          return localAttributes.contains(input.getQualifiedName());
        }
      });
  }
}
