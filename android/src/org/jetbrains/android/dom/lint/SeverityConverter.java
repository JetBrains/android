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
package org.jetbrains.android.dom.lint;

import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class SeverityConverter extends ResolvingConverter<Severity> {
  private static final List<Severity> ALL_VALUES = ImmutableList.copyOf(Severity.values());

  @NotNull
  @Override
  public Collection<Severity> getVariants(@NotNull ConvertContext context) {
    return ALL_VALUES;
  }

  @Nullable
  @Override
  public Severity fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (s == null) {
      return null;
    }

    for (Severity severity : Severity.values()) {
      if (severity.name().equalsIgnoreCase(s)) {
        return severity;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String toString(@Nullable Severity severity, @NotNull ConvertContext context) {
    if (severity == null) {
      return null;
    }
    return StringUtil.toLowerCase(severity.name());
  }
}
