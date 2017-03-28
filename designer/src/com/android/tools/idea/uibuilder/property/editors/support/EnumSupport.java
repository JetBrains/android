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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class EnumSupport {
  protected final NlProperty myProperty;

  public EnumSupport(@NotNull NlProperty property) {
    myProperty = property;
  }

  @NotNull
  public abstract List<ValueWithDisplayString> getAllValues();

  @NotNull
  public ValueWithDisplayString createValue(@NotNull String editorValue) {
    if (editorValue.isEmpty()) {
      editorValue = null;
    }
    String resolvedValue = myProperty.resolveValue(editorValue);
    if (StringUtil.isEmpty(resolvedValue)) {
      return ValueWithDisplayString.UNSET;
    }
    String hint = editorValue == null ? "default" : (!editorValue.equals(resolvedValue) ? editorValue : null);
    return createFromResolvedValue(resolvedValue, editorValue, hint);
  }

  @NotNull
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    return new ValueWithDisplayString(resolvedValue, value, hint);
  }
}
