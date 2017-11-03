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
public class AttributeDefinition implements Cloneable {
  private String myName;
  private final List<String> myParentStyleables;
  private final Set<AttributeFormat> myFormats;
  private LinkedList<String> myValues;
  private final Map<String, String> myStyleable2DocValue;
  private HashMap<String, String> myValueDoc;
  /** Mapping of flag/enum names to their int value */
  private HashMap<String, Integer> myValueMappings;
  private String myGlobalDocValue;
  private String myAttrGroup;
  private final String myLibraryName;

  public AttributeDefinition(@NotNull String name) {
    this(name, null, null, Collections.emptySet());
  }

  public AttributeDefinition(@NotNull String name,
                             @Nullable String libraryName,
                             @Nullable String parentStyleableName,
                             @NotNull Collection<AttributeFormat> formats) {
    myName = name;
    myLibraryName = libraryName;
    myParentStyleables = parentStyleableName == null ? ContainerUtil.newSmartList() : ContainerUtil.newSmartList(parentStyleableName);
    myFormats = formats.isEmpty() ? EnumSet.noneOf(AttributeFormat.class) : EnumSet.copyOf(formats);
    myStyleable2DocValue = new HashMap<>();
  }

  public void addValue(@NotNull String name) {
    if (myValues == null) {
      myValues = new LinkedList<>();
    }

    myValues.add(name);
  }

  public void addValueMapping(@NotNull String flagName, @NotNull Integer intValue) {
    if (myValueMappings == null) {
      myValueMappings = new HashMap<>();
    }

    myValueMappings.put(flagName, intValue);
  }

  /**
   * For flag or enum attributes, it returns the int value of the value name or null if the mapping does not exist
   */
  @Nullable
  public Integer getValueMapping(@NotNull String flagName) {
    return myValueMappings != null ? myValueMappings.get(flagName) : null;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public String getLibraryName() {
    return myLibraryName;
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
    return myValues == null ? ArrayUtil.EMPTY_STRING_ARRAY : ArrayUtil.toStringArray(myValues);
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
    if (myValueDoc == null) {
      myValueDoc = new HashMap<>();
    }

    myValueDoc.put(value, doc);
  }

  @Nullable
  public String getValueDoc(@NotNull String value) {
    return myValueDoc != null ? myValueDoc.get(value) : null;
  }

  /**
   * Checks whether attribute is deprecated by looking up "deprecated" in its documenting comment
   */
  public boolean isAttributeDeprecated() {
    final String doc = getDocValue(null);
    return doc != null && StringUtil.containsIgnoreCase(doc, "deprecated");
  }

  public boolean isValueDeprecated(@NotNull String value) {
    final String doc = getValueDoc(value);
    return doc != null && StringUtil.containsIgnoreCase(doc, "deprecated");
  }

  /**
   * Returns a shallow copy of this attribute definition under a different name.
   *
   * @param name the name to give the new attribute definition
   * @return the new attribute definition
   */
  @NotNull
  public AttributeDefinition cloneWithName(@NotNull String name) {
    try {
      AttributeDefinition copy = (AttributeDefinition)super.clone();
      copy.myName = name;
      return copy;
    }
    catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }
}
