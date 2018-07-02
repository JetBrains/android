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

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
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
  @NotNull private final ResourceReference myAttr;
  private boolean myQualifyName;
  @NotNull private final List<ResourceReference> myParentStyleables;
  @NotNull private final Set<AttributeFormat> myFormats;
  private List<String> myValues;
  /** Keys are styleables, values are doc strings for this attr in the context of the styleable. */
  @NotNull private final Map<ResourceReference, String> myStyleable2DocValue;
  /** Keys are attr values, values are the corresponding doc strings. */
  private HashMap<String, String> myValueDoc;
  /** Mapping of flag/enum names to their int value. */
  private HashMap<String, Integer> myValueMappings;
  private String myGlobalDocValue;
  private String myAttrGroup;
  @Nullable private final String myLibraryName;

  public AttributeDefinition(@NotNull ResourceNamespace namespace, @NotNull String name) {
    this(namespace, name, null, null, Collections.emptySet());
  }

  public AttributeDefinition(@NotNull ResourceNamespace namespace,
                             @NotNull String name,
                             @Nullable String libraryName,
                             @Nullable ResourceReference parentStyleable,
                             @NotNull Collection<AttributeFormat> formats) {
    assert name.indexOf(':') < 0;
    myAttr = ResourceReference.attr(namespace, name);
    myLibraryName = libraryName;
    myParentStyleables = parentStyleable == null ? ContainerUtil.newSmartList() : ContainerUtil.newSmartList(parentStyleable);
    myFormats = formats.isEmpty() ? EnumSet.noneOf(AttributeFormat.class) : EnumSet.copyOf(formats);
    myStyleable2DocValue = new HashMap<>();
  }

  public void addValue(@NotNull String name) {
    if (myValues == null) {
      myValues = new ArrayList<>();
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
  public ResourceReference getResourceReference() {
    return myAttr;
  }

  @NotNull
  public String getName() {
    //TODO(namespaces): Always return the non-qualified name when the callers are updated to handle that.
    return myQualifyName ? myAttr.getQualifiedName() : myAttr.getName();
  }

  @Nullable
  public String getLibraryName() {
    return myLibraryName;
  }

  @NotNull
  public List<ResourceReference> getParentStyleables() {
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

  public void addFormats(@NotNull Collection<AttributeFormat> formats) {
    myFormats.addAll(formats);
  }

  @NotNull
  public String[] getValues() {
    return myValues == null ? ArrayUtil.EMPTY_STRING_ARRAY : ArrayUtil.toStringArray(myValues);
  }

  @Nullable
  public String getDocValue(@Nullable ResourceReference parentStyleable) {
    String doc = parentStyleable == null ? null : myStyleable2DocValue.get(parentStyleable);
    return doc == null ? myGlobalDocValue : doc;
  }

  /**
   * @deprecated Use {@link #getDocValue(ResourceReference)}
   */
  @Deprecated
  @Nullable
  public String getDocValueByParentStyleableName(@Nullable String parentStyleable) {
    if (parentStyleable == null) {
      return myGlobalDocValue;
    }
    String doc = myStyleable2DocValue.get(ResourceReference.styleable(ResourceNamespace.TODO(), parentStyleable));
    if (doc == null) {
      doc = myStyleable2DocValue.get(ResourceReference.styleable(ResourceNamespace.ANDROID, parentStyleable));
    }
    return doc == null ? myGlobalDocValue : doc;
  }

  public void addDocValue(@NotNull String docValue, @Nullable ResourceReference parentStyleable) {
    if (parentStyleable == null || myGlobalDocValue == null) {
      myGlobalDocValue = docValue;
    }
    if (parentStyleable != null) {
      myStyleable2DocValue.put(parentStyleable, docValue);
    }
  }

  @Override
  public String toString() {
    return getName() + " [" + myFormats + ']';
  }

  public void addValueDoc(@NotNull String value, @NotNull String doc) {
    if (myValueDoc == null) {
      myValueDoc = new HashMap<>();
    }

    myValueDoc.put(value, doc);
  }

  @Nullable
  public String getValueDoc(@NotNull String value) {
    return myValueDoc == null ? null : myValueDoc.get(value);
  }

  /**
   * Checks whether attribute is deprecated by looking up "deprecated" in its documenting comment.
   */
  public boolean isAttributeDeprecated() {
    return myGlobalDocValue != null && StringUtil.containsIgnoreCase(myGlobalDocValue, "deprecated");
  }

  public boolean isValueDeprecated(@NotNull String value) {
    String doc = getValueDoc(value);
    return doc != null && StringUtil.containsIgnoreCase(doc, "deprecated");
  }

  /**
   * Returns a shallow copy of this attribute definition for which the {@link #getName()} will return a qualified name.
   *
   * @return the new attribute definition
   */
  @NotNull
  public AttributeDefinition cloneWithQualifiedName() {
    try {
      AttributeDefinition copy = (AttributeDefinition)super.clone();
      copy.myQualifyName = true;
      return copy;
    }
    catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }
}
