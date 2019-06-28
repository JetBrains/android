// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.attrs;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Information about an attr resource. The same attr resource may be declared in multiple places in XML
 * including declarations at the top level and inside styleables. This class combines information about
 * the attr resource from all its declarations.
 */
public final class AttributeDefinition implements Cloneable {
  @NotNull private final ResourceReference myAttr;
  @Nullable private final String myLibraryName;
  @Nullable private String myGlobalDescription;
  /** @see com.android.ide.common.rendering.api.AttrResourceValue#getGroupName() */
  @Nullable private String myGroupName;
  /** Mapping of flag/enum names to their integer values. */
  @NotNull private Map<String, Integer> myValueMappings = Collections.emptyMap();
  /** Keys are flag/enum names, values are their descriptions. */
  @NotNull private Map<String, String> myValueDescriptions = Collections.emptyMap();
  @NotNull private Set<AttributeFormat> myFormats;
  // TODO: Consider moving style-specific descriptions to StyleableDefinitionImpl.
  /** Keys are styleables, values are descriptions for this attribute in the context of the styleable. */
  @Nullable private Map<ResourceReference, String> myDescriptionsInStyleableContexts;

  public AttributeDefinition(@NotNull ResourceNamespace namespace, @NotNull String name) {
    this(namespace, name, null, null);
  }

  public AttributeDefinition(@NotNull ResourceNamespace namespace,
                             @NotNull String name,
                             @Nullable String libraryName,
                             @Nullable Collection<AttributeFormat> formats) {
    assert name.indexOf(':') < 0;
    myAttr = ResourceReference.attr(namespace, name);
    myLibraryName = libraryName;
    myFormats = formats == null || formats.isEmpty() ? EnumSet.noneOf(AttributeFormat.class) : EnumSet.copyOf(formats);
  }

  public AttributeDefinition(@NotNull AttributeDefinition other) {
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
  public Integer getValueMapping(@NotNull String flagName) {
    return myValueMappings.get(flagName);
  }

  @NotNull
  public ResourceReference getResourceReference() {
    return myAttr;
  }

  @NotNull
  public String getName() {
    return myAttr.getName();
  }

  @Nullable
  public String getLibraryName() {
    return myLibraryName;
  }

  @NotNull
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

  @NotNull
  public String[] getValues() {
    return myValueMappings.isEmpty() ? ArrayUtilRt.EMPTY_STRING_ARRAY : ArrayUtilRt.toStringArray(myValueMappings.keySet());
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
  public String getValueDescription(@NotNull String value) {
    return myValueDescriptions.get(value);
  }

  /**
   * Checks whether attribute is deprecated by looking up "deprecated" in its description.
   */
  public boolean isAttributeDeprecated() {
    return myGlobalDescription != null && StringUtil.containsIgnoreCase(myGlobalDescription, "deprecated");
  }

  public boolean isValueDeprecated(@NotNull String value) {
    String description = getValueDescription(value);
    return description != null && StringUtil.containsIgnoreCase(description, "deprecated");
  }

  void addFormats(@NotNull Collection<AttributeFormat> formats) {
    myFormats.addAll(formats);
  }

  public void setValueMappings(@NotNull Map<String, Integer> valueMappings) {
    if (!myValueMappings.isEmpty() && !myValueMappings.equals(valueMappings)) {
      getLog().warn("An attempt to redefine value mappings of " + myAttr.getQualifiedName());
    }
    myValueMappings = Collections.unmodifiableMap(valueMappings);
  }

  void setValueDescriptions(@NotNull Map<String, String> valueDescriptions) {
    if (!myValueDescriptions.isEmpty() && !myValueDescriptions.equals(valueDescriptions)) {
      getLog().warn("An attempt to redefine value descriptions of " + myAttr.getQualifiedName());
    }
    myValueDescriptions = Collections.unmodifiableMap(valueDescriptions);
  }

  void setGroupName(@Nullable String groupName) {
    myGroupName = groupName;
  }

  void setDescription(@NotNull String description, @Nullable ResourceReference parentStyleable) {
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
