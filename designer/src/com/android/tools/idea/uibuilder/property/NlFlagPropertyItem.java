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
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.renderer.NlAttributeRenderer;
import com.android.tools.idea.uibuilder.property.renderer.NlPropertyRenderers;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NlFlagPropertyItem extends NlPropertyItem implements NlProperty {
  private List<PTableItem> myItems;
  private long myLastRead;
  private String myLastValue;
  private String myLastFormattedValue;
  private Set<String> myLastValues;
  private int myMaskValue;
  private boolean myExpanded;

  private static final Splitter VALUE_SPLITTER = Splitter.on("|").trimResults();

  protected NlFlagPropertyItem(@NotNull XmlName name,
                               @Nullable AttributeDefinition attributeDefinition,
                               @NotNull List<NlComponent> components,
                               @NotNull NlPropertiesManager propertiesManager) {
    super(name, attributeDefinition, components, propertiesManager);
    assert attributeDefinition != null;
  }

  protected NlFlagPropertyItem(@NotNull NlFlagPropertyItem property, @NotNull String namespace) {
    super(property, namespace);
  }

  @Override
  public boolean hasChildren() {
    return true;
  }

  @Override
  @NotNull
  public List<PTableItem> getChildren() {
    if (myItems == null) {
      assert myDefinition != null;
      myItems = Lists.newArrayListWithCapacity(myDefinition.getValues().length);
      for (String value : myDefinition.getValues()) {
        myItems.add(new NlFlagPropertyItemValue(value, lookupMaskValue(value), this));
      }
    }
    return myItems;
  }

  private int lookupMaskValue(@NotNull String value) {
    assert myDefinition != null;
    Integer mappedValue = myDefinition.getValueMapping(value);
    if (mappedValue != null) {
      return mappedValue;
    }
    int index = ArrayUtil.indexOf(myDefinition.getValues(), value);
    return index < 0 ? 0 : 1 << index;
  }

  @NotNull
  @Override
  public NlFlagPropertyItemValue getChildProperty(@NotNull String itemName) {
    for (PTableItem child : getChildren()) {
      if (child.getName().equals(itemName)) {
        return (NlFlagPropertyItemValue)child;
      }
    }
    throw new IllegalArgumentException(itemName);
  }

  @NotNull
  @Override
  public NlFlagPropertyItem getDesignTimeProperty() {
    if (SdkConstants.TOOLS_URI.equals(getNamespace())) {
      return this;
    }
    return new NlFlagPropertyItem(this, SdkConstants.TOOLS_URI);
  }

  @Override
  public boolean isExpanded() {
    return myExpanded;
  }

  @Override
  public void setExpanded(boolean expanded) {
    myExpanded = expanded;
  }

  @Override
  public boolean isEditable(int col) {
    return false;
  }

  @Override
  @Nullable
  public String getValue() {
    cacheValues();
    return myLastValue;
  }

  @Override
  public void setValue(@Nullable Object value) {
    String strValue = value == null ? null : value.toString();
    if (StringUtil.isEmpty(strValue)) {
      strValue = null;
    }
    setValueIgnoreDefaultValue(strValue, this::invalidateCachedValues);
  }

  public int getMaskValue() {
    return myMaskValue;
  }

  public String getFormattedValue() {
    cacheValues();
    return myLastFormattedValue;
  }

  @NotNull
  private Set<String> getValues() {
    cacheValues();
    return myLastValues;
  }

  private void invalidateCachedValues() {
    myLastValues = null;
  }

  private void cacheValues() {
    if (myLastValues != null && myLastRead == getModel().getModificationCount()) {
      return;
    }
    Set<String> values = Collections.emptySet();
    String rawValue = super.getValue();
    String formattedValue = "[]";
    if (rawValue != null) {
      List<String> valueList = VALUE_SPLITTER.splitToList(StringUtil.notNullize(rawValue));
      values = Sets.newHashSet(valueList);
      formattedValue = "[" + Joiner.on(", ").join(valueList) + "]";
    }
    String resolvedValue = resolveValue(rawValue);
    int maskValue = 0;
    if (resolvedValue != null) {
      Collection<String> maskValues =
        resolvedValue.equals(rawValue) ? values : VALUE_SPLITTER.splitToList(StringUtil.notNullize(resolvedValue));
      for (String value : maskValues) {
        maskValue |= lookupMaskValue(value);
      }
    }

    myLastValues = values;
    myLastValue = rawValue;
    myLastFormattedValue = formattedValue;
    myLastRead = getModel().getModificationCount();
    myMaskValue = maskValue;
  }

  public boolean isItemSet(@NotNull NlFlagPropertyItemValue item) {
    return isItemSet(item.getName());
  }

  public boolean isItemSet(@NotNull String itemName) {
    return getValues().contains(itemName);
  }

  public void setItem(@NotNull NlFlagPropertyItemValue changedItem, boolean on) {
    Set<String> removed = on ? Collections.emptySet() : Collections.singleton(changedItem.getName());
    Set<String> added = on ? Collections.singleton(changedItem.getName()) : Collections.emptySet();
    updateItems(added, removed);
  }

  public void updateItems(@NotNull Set<String> added, @NotNull Set<String> removed) {
    Set<String> values = getValues();
    StringBuilder builder = new StringBuilder();
    // Enumerate over myItems in order to generate a string with the elements in a predictable order:
    getChildren().stream()
      .filter(item -> values.contains(item.getName()) && !removed.contains(item.getName()) || added.contains(item.getName()))
      .forEach(item -> {
        if (builder.length() > 0) {
          builder.append("|");
        }
        builder.append(item.getName());
      });
    String newValue = builder.length() == 0 ? null : builder.toString();
    setValue(newValue);
  }

  @Override
  public void mousePressed(@NotNull PTable table, @NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
    NlAttributeRenderer renderer = NlPropertyRenderers.getInstance().get(this);
    renderer.mousePressed(event, rectRightColumn);
  }
}
