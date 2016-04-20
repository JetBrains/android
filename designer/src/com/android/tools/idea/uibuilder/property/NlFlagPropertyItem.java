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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NlFlagPropertyItem extends NlPropertyItem implements NlProperty {
  private List<PTableItem> myItems;
  private long myLastRead;
  private String myLastValue;
  private String myLastFormattedValue;
  private Set<String> myLastValues;
  private boolean myExpanded;

  private static final Splitter VALUE_SPLITTER = Splitter.on("|").trimResults();

  protected NlFlagPropertyItem(@NotNull NlComponent component,
                               @NotNull XmlAttributeDescriptor descriptor,
                               @Nullable AttributeDefinition attributeDefinition) {
    super(component, descriptor, attributeDefinition);
    assert attributeDefinition != null;
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
        myItems.add(new NlFlagPropertyItemValue(value, this));
      }
    }
    return myItems;
  }

  @NotNull
  @Override
  public NlProperty getChildProperty(@NotNull String itemName) {
    for (PTableItem child : getChildren()) {
      if (child.getName().equals(itemName)) {
        return (NlProperty)child;
      }
    }
    throw new IllegalArgumentException(itemName);
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

  public String getFormattedValue() {
    cacheValues();
    return myLastFormattedValue;
  }

  @NotNull
  private Set<String> getValues() {
    cacheValues();
    return myLastValues;
  }

  private void cacheValues() {
    if (myLastRead == myComponent.getModel().getModificationCount()) {
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

    myLastValues = values;
    myLastValue = rawValue;
    myLastFormattedValue = formattedValue;
    myLastRead = myComponent.getModel().getModificationCount();
  }

  public boolean isItemSet(@NotNull NlFlagPropertyItemValue item) {
    return isItemSet(item.getName());
  }

  public boolean isItemSet(@NotNull String itemName) {
    return getValues().contains(itemName);
  }

  public boolean isAnyItemSet(@NotNull String... itemNames) {
    for (String itemName : itemNames) {
      if (getValues().contains(itemName)) {
        return true;
      }
    }
    return false;
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
}
