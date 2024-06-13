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
package com.android.tools.dom.attrs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.environment.Logger;
import kotlin.text.StringsKt;

import java.util.*;

/**
 * Information about an attr resource. The same attr resource may be declared in multiple places in XML
 * including declarations at the top level and inside styleables. This class combines information about
 * the attr resource from all its declarations.
 */
public final class AttributeDefinition implements Cloneable {
  private final static String[] EMPTY_STRING_ARRAY = new String[0];
  @NonNull private final ResourceReference myAttr;
  @Nullable private final String myLibraryName;
  @Nullable private String myGlobalDescription;
  /** @see com.android.ide.common.rendering.api.AttrResourceValue#getGroupName() */
  @Nullable private String myGroupName;
  /** Mapping of flag/enum names to their integer values. */
  @NonNull private Map<String, Integer> myValueMappings = Collections.emptyMap();
  /** Keys are flag/enum names, values are their descriptions. */
  @NonNull private Map<String, String> myValueDescriptions = Collections.emptyMap();
  @NonNull private Set<AttributeFormat> myFormats;
  // TODO: Consider moving style-specific descriptions to StyleableDefinitionImpl.
  /** Keys are styleables, values are descriptions for this attribute in the context of the styleable. */
  @Nullable private Map<ResourceReference, String> myDescriptionsInStyleableContexts;

  public AttributeDefinition(@NonNull ResourceNamespace namespace, @NonNull String name) {
    this(namespace, name, null, null);
  }

  public AttributeDefinition(@NonNull ResourceNamespace namespace,
                             @NonNull String name,
                             @Nullable String libraryName,
                             @Nullable Collection<AttributeFormat> formats) {
    assert name.indexOf(':') < 0;
    myAttr = ResourceReference.attr(namespace, name);
    myLibraryName = libraryName;
    myFormats = formats == null || formats.isEmpty() ? EnumSet.noneOf(AttributeFormat.class) : EnumSet.copyOf(formats);
  }

  public AttributeDefinition(@NonNull AttributeDefinition other) {
    myAttr = other.myAttr;
    myLibraryName = other.myLibraryName;
    myGlobalDescription = other.myGlobalDescription;
    myGroupName = other.myGroupName;
    myValueMappings = other.myValueMappings;
    myValueDescriptions = other.myValueDescriptions;
    myFormats = EnumSet.copyOf(other.myFormats);
    myDescriptionsInStyleableContexts = other.myDescriptionsInStyleableContexts == null ?
        null : new HashMap<>(other.myDescriptionsInStyleableContexts);
  }

  /**
   * For flag or enum attributes, it returns the int value for the value name, or null if the mapping does not exist.
   */
  @Nullable
  public Integer getValueMapping(@NonNull String flagName) {
    return myValueMappings.get(flagName);
  }

  @NonNull
  public ResourceReference getResourceReference() {
    return myAttr;
  }

  @NonNull
  public String getName() {
    return myAttr.getName();
  }

  @Nullable
  public String getLibraryName() {
    return myLibraryName;
  }

  @NonNull
  public Set<AttributeFormat> getFormats() {
    return Collections.unmodifiableSet(myFormats);
  }

  /**
   * @see com.android.ide.common.rendering.api.AttrResourceValue#getGroupName()
   */
  @Nullable
  public String getGroupName() {
    return myGroupName;
  }

  @NonNull
  public String[] getValues() {
    return myValueMappings.isEmpty() ? EMPTY_STRING_ARRAY : myValueMappings.keySet().toArray(EMPTY_STRING_ARRAY);
  }

  @Nullable
  public String getDescription(@Nullable ResourceReference parentStyleable) {
    String description = parentStyleable == null || myDescriptionsInStyleableContexts == null ?
        null : myDescriptionsInStyleableContexts.get(parentStyleable);
    return description == null ? myGlobalDescription : description;
  }

  /**
   * @deprecated Use {@link #getDescription(ResourceReference)}.
   */
  @Deprecated
  @Nullable
  public String getDescriptionByParentStyleableName(@Nullable String parentStyleable) {
    if (parentStyleable == null || myDescriptionsInStyleableContexts == null) {
      return myGlobalDescription;
    }
    String description = myDescriptionsInStyleableContexts.get(ResourceReference.styleable(ResourceNamespace.TODO(), parentStyleable));
    if (description == null) {
      description = myDescriptionsInStyleableContexts.get(ResourceReference.styleable(ResourceNamespace.ANDROID, parentStyleable));
    }
    return description == null ? myGlobalDescription : description;
  }

  @Nullable
  public String getValueDescription(@NonNull String value) {
    return myValueDescriptions.get(value);
  }

  /**
   * Checks whether attribute is deprecated by looking up "deprecated" in its description.
   */
  public boolean isAttributeDeprecated() {
    return myGlobalDescription != null && StringsKt.contains(myGlobalDescription, "deprecated", true);
  }

  public boolean isValueDeprecated(@NonNull String value) {
    String description = getValueDescription(value);
    return description != null && StringsKt.contains(description, "deprecated", true);
  }

  void addFormats(@NonNull Collection<AttributeFormat> formats) {
    myFormats.addAll(formats);
  }

  public void setValueMappings(@NonNull Map<String, Integer> valueMappings) {
    if (!myValueMappings.isEmpty() && !myValueMappings.equals(valueMappings)) {
      getLog().warn("An attempt to redefine value mappings of " + myAttr.getQualifiedName());
    }
    myValueMappings = Collections.unmodifiableMap(valueMappings);
  }

  void setValueDescriptions(@NonNull Map<String, String> valueDescriptions) {
    if (!myValueDescriptions.isEmpty() && !myValueDescriptions.equals(valueDescriptions)) {
      getLog().warn("An attempt to redefine value descriptions of " + myAttr.getQualifiedName());
    }
    myValueDescriptions = Collections.unmodifiableMap(valueDescriptions);
  }

  void setGroupName(@Nullable String groupName) {
    myGroupName = groupName;
  }

  void setDescription(@NonNull String description, @Nullable ResourceReference parentStyleable) {
    if (parentStyleable == null || myGlobalDescription == null) {
      myGlobalDescription = description;
    }
    if (parentStyleable != null) {
      if (myDescriptionsInStyleableContexts == null) {
        myDescriptionsInStyleableContexts = new HashMap<>(3);
      }
      myDescriptionsInStyleableContexts.put(parentStyleable, description);
    }
  }

  @Override
  public String toString() {
    return myAttr.getQualifiedName() + " [" + myFormats + ']';
  }

  private static Logger getLog() {
    return Logger.getInstance(AttributeDefinition.class);
  }
}
