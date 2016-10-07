/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.attrs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class AttributeDefinition {
  private final String myName;
  private final List<String> myParentStyleables = ContainerUtil.newSmartList();
  private final Set<AttributeFormat> myFormats = EnumSet.noneOf(AttributeFormat.class);
  private final List<String> myValues = new ArrayList<>();
  private final Map<String, String> myStyleable2DocValue = new HashMap<>();
  private final Map<String, String> myValueDoc = new HashMap<>();
  private String myGlobalDocValue;
  private String myAttrGroup;

  public AttributeDefinition(@NotNull String name) {
    this(name, null, Collections.<AttributeFormat>emptySet());
  }

  public AttributeDefinition(@NotNull String name, @Nullable String parentStyleableName, @NotNull Collection<AttributeFormat> formats) {
    myName = name;
    if (parentStyleableName != null && !myParentStyleables.contains(parentStyleableName)) {
      myParentStyleables.add(parentStyleableName);
    }
    myFormats.addAll(formats);
  }

  public void addValue(@NotNull String name) {
    myValues.add(name);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public List<String> getParentStyleables() {
    return myParentStyleables;
  }

  @NotNull
  public Set<AttributeFormat> getFormats() {
    return Collections.unmodifiableSet(myFormats);
  }

  @Nullable
  public String getAttrGroup() {
    return myAttrGroup;
  }

  public void setAttrGroup(@Nullable String attrGroup) {
    myAttrGroup = attrGroup;
  }

  public void addFormats(@NotNull Collection<AttributeFormat> format) {
    myFormats.addAll(format);
  }

  @NotNull
  public String[] getValues() {
    return ArrayUtil.toStringArray(myValues);
  }

  @Nullable
  public String getDocValue(@Nullable String parentStyleable) {
    if (parentStyleable == null || !myStyleable2DocValue.containsKey(parentStyleable)) {
      return myGlobalDocValue;
    }
    return myStyleable2DocValue.get(parentStyleable);
  }

  public void addDocValue(@NotNull String docValue, @Nullable String parentStyleable) {
    if (parentStyleable == null || myGlobalDocValue == null) {
      myGlobalDocValue = docValue;
    }
    if (parentStyleable != null) {
      myStyleable2DocValue.put(parentStyleable, docValue);
    }
  }

  @Override
  public String toString() {
    return myName + " [" + myFormats + ']';
  }

  public void addValueDoc(@NotNull String value, @NotNull String doc) {
    myValueDoc.put(value, doc);
  }

  @Nullable
  public String getValueDoc(@NotNull String value) {
    return myValueDoc.get(value);
  }

  /**
   * Checks whether attribute is deprecated by looking up "deprecated" in its documenting comment
   */
  public boolean isAttributeDeprecated() {
    final String doc = getDocValue(null);
    return doc != null && StringUtil.containsIgnoreCase(doc, "deprecated");
  }

  public boolean isValueDeprecated(@NotNull String value) {
    final String doc = myValueDoc.get(value);
    return doc != null && StringUtil.containsIgnoreCase(doc, "deprecated");
  }
}
