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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Support class for enumerated property types.
 *
 * The elements being shown and their display can be controlled with this class.
 */
public abstract class EnumSupport {
  protected final NlProperty myProperty;

  public EnumSupport(@NotNull NlProperty property) {
    myProperty = property;
  }

  /**
   * Return the list of values to be shown in a enum editor
   * @return
   */
  @NotNull
  public abstract List<ValueWithDisplayString> getAllValues();

  /**
   * Customize the rendering of an enum value.
   * @return true if the default rendering should be skipped.
   */
  public boolean customizeCellRenderer(@NotNull ColoredListCellRenderer<ValueWithDisplayString> renderer,
                                       @NotNull ValueWithDisplayString value,
                                       boolean selected) {
    return false;
  }

  /**
   * Creates a {@link ValueWithDisplayString} which may be customized per property type.
   */
  @NotNull
  public ValueWithDisplayString createValue(@NotNull String editorValue) {
    if (editorValue.isEmpty()) {
      editorValue = null;
    }
    String resolvedValue = myProperty.resolveValue(editorValue);
    if (StringUtil.isEmpty(resolvedValue)) {
      return ValueWithDisplayString.UNSET;
    }
    String hint = StringUtil.isEmpty(editorValue) ? "default" : (!editorValue.equals(resolvedValue) ? editorValue : null);
    return createFromResolvedValue(resolvedValue, editorValue, hint);
  }

  @NotNull
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    return new ValueWithDisplayString(resolvedValue, value, hint);
  }
}
