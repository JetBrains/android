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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import com.android.tools.dom.attrs.StyleableDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes built-in attributes defined in the "tools" namespace.
 *
 * @see ToolsAttributeUtil#getAttributeNames()
 */
public class ToolsAttributeDefinitionsImpl implements AttributeDefinitions {
  private static final Set<ResourceReference> ATTRIBUTE_REFERENCES =
      ToolsAttributeUtil.getAttributeNames().stream()
          .map(name -> ResourceReference.attr(ResourceNamespace.TOOLS, name)).collect(Collectors.toSet());

  @Override
  @Nullable
  public StyleableDefinition getStyleableDefinition(@NotNull ResourceReference styleable) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  @Nullable
  public StyleableDefinition getStyleableByName(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Set<ResourceReference> getAttrs() {
    return ATTRIBUTE_REFERENCES;
  }

  @Override
  @Nullable
  public AttributeDefinition getAttrDefinition(@NotNull ResourceReference attr) {
    if (attr.getNamespace() != ResourceNamespace.TOOLS) {
      return null;
    }
    return ToolsAttributeUtil.getAttrDefByName(attr.getName());
  }

  @Deprecated
  @Override
  @Nullable
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    return ToolsAttributeUtil.getAttrDefByName(name);
  }

  @Override
  @Nullable
  public String getAttrGroup(@NotNull ResourceReference attr) {
    return "Tools";
  }
}
