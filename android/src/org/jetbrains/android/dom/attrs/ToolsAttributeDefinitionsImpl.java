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
package org.jetbrains.android.dom.attrs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ToolsAttributeDefinitionsImpl implements AttributeDefinitions {
  @Nullable
  @Override
  public StyleableDefinition getStyleableByName(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<String> getAttributeNames() {
    return ToolsAttributeUtil.getAttributeNames();
  }

  @Nullable
  @Override
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    return ToolsAttributeUtil.getAttrDefByName(name);
  }

  @Nullable
  @Override
  public String getAttrGroupByName(@NotNull String name) {
    return "Tools";
  }
}
