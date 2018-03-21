/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.property.NlProperty;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class EmptyProperty implements NlProperty {

  public static final EmptyProperty INSTANCE = new EmptyProperty();

  private EmptyProperty() {}

  @NotNull
  @Override
  public String getName() {
    return SdkConstants.ATTR_TEXT_SIZE;
  }

  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  @Nullable
  @Override
  public String getValue() {
    return null;
  }

  @Nullable
  @Override
  public String getResolvedValue() {
    return null;
  }

  @Override
  public boolean isDefaultValue(@Nullable String value) {
    return false;
  }

  @Nullable
  @Override
  public String resolveValue(@Nullable String value) {
    return null;
  }

  @Override
  public void setValue(@Nullable Object value) {
    if (value != null) {
      throw new IllegalStateException();
    }
  }

  @Override
  @NotNull
  public String getTooltipText() {
    return "";
  }

  @Nullable
  @Override
  public AttributeDefinition getDefinition() {
    return null;
  }

  @NotNull
  @Override
  public List<NlComponent> getComponents() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public ResourceResolver getResolver() {
    return null;
  }

  @NotNull
  @Override
  public NlModel getModel() {
    throw new IllegalStateException();
  }

  @Nullable
  @Override
  public XmlTag getTag() {
    return null;
  }

  @Nullable
  @Override
  public String getTagName() {
    return null;
  }

  @NotNull
  @Override
  public NlProperty getChildProperty(@NotNull String name) {
    throw new IllegalStateException();
  }

  @NotNull
  @Override
  public NlProperty getDesignTimeProperty() {
    throw new IllegalStateException();
  }
}
