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
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.StarState;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NlFlagPropertyItemValue extends PTableItem implements NlProperty {
  private final String myName;
  private final NlFlagPropertyItem myFlags;
  private final int myMaskValue;

  public NlFlagPropertyItemValue(@NotNull String name, int maskValue, @NotNull NlFlagPropertyItem flags) {
    myName = name;
    myFlags = flags;
    myMaskValue = maskValue;
    setParent(flags);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getNamespace() {
    // Values do not have a namespace.
    // Used in PNameRenderer to not indicate the tools namespace for flag values.
    return null;
  }

  @Override
  @NotNull
  public StarState getStarState() {
    return StarState.NOT_STAR_ABLE;
  }

  @Override
  public void setStarState(@NotNull StarState starState) {
    throw new IllegalStateException();
  }

  @Override
  public String getValue() {
    return myFlags.isItemSet(this) ? SdkConstants.VALUE_TRUE : SdkConstants.VALUE_FALSE;
  }

  public boolean getMaskValue() {
    if (myMaskValue == 0) {
      return myFlags.getMaskValue() == 0;
    }
    return (myMaskValue & myFlags.getMaskValue()) == myMaskValue;
  }

  @Override
  @Nullable
  public String getResolvedValue() {
    return getValue();
  }

  @Override
  public boolean isDefaultValue(@Nullable String value) {
    return false;
  }

  @Override
  @Nullable
  public String resolveValue(@Nullable String value) {
    return value;
  }

  @Override
  public void setValue(@Nullable Object value) {
    if (value == null) {
      value = SdkConstants.VALUE_FALSE;
    }
    myFlags.setItem(this, SdkConstants.VALUE_TRUE.equalsIgnoreCase(value.toString()));
  }

  @NotNull
  @Override
  public NlProperty getChildProperty(@NotNull String itemName) {
    throw new UnsupportedOperationException(itemName);
  }

  @NotNull
  @Override
  public NlProperty getDesignTimeProperty() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public AttributeDefinition getDefinition() {
    return myFlags.getDefinition();
  }

  @NotNull
  @Override
  public List<NlComponent> getComponents() {
    return myFlags.getComponents();
  }

  @Nullable
  @Override
  public ResourceResolver getResolver() {
    return myFlags.getResolver();
  }

  @NotNull
  @Override
  public NlModel getModel() {
    return myFlags.getModel();
  }

  @Nullable
  @Override
  public XmlTag getTag() {
    return myFlags.getTag();
  }

  @Nullable
  @Override
  public String getTagName() {
    return myFlags.getTagName();
  }

  @Override
  public boolean isEditable(int col) {
    return true;
  }
}
