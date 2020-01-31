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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValueImpl;
import com.android.tools.idea.configurations.Configuration;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents styles in ThemeEditor. In addition to {@link ThemeEditorStyle},
 * it knows about current {@link Configuration} used in ThemeEditor.
 * TODO: Move Configuration independent methods to ThemeEditorStyle.
 */
public class ConfiguredThemeEditorStyle extends ThemeEditorStyle {
  private final @NotNull StyleResourceValueImpl myStyleResourceValue;

  public ConfiguredThemeEditorStyle(@NotNull Configuration configuration,
                                    @NotNull StyleResourceValue styleResourceValue) {
    super(configuration.getConfigurationManager(), styleResourceValue.asReference());
    myStyleResourceValue = StyleResourceValueImpl.copyOf(styleResourceValue);
  }

  /**
   * Returns StyleResourceValueImpl for the current Configuration.
   */
  @NotNull
  public StyleResourceValueImpl getStyleResourceValue() {
    return myStyleResourceValue;
  }

  /**
   * Returns whether this style is editable.
   */
  public boolean isReadOnly() {
    return !isProjectStyle();
  }

  @Override
  public String toString() {
    if (!isReadOnly()) {
      return "[" + getName() + "]";
    }

    return getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ConfiguredThemeEditorStyle)) {
      return false;
    }

    return getStyleReference().equals(((ConfiguredThemeEditorStyle)obj).getStyleReference());
  }

  @Override
  public int hashCode() {
    return getStyleReference().hashCode();
  }
}
