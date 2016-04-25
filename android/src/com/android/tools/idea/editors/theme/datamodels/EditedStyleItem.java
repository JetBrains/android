/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Wrapper for {@link com.android.ide.common.rendering.api.ResourceValue} that allows to keep track of modifications and source so we can
 * serialize modifications back to the style file.
 * <p/>
 * If the attribute is declared locally in multiple resource folders, this class also contains the alternative values for the attribute.
 */
public class EditedStyleItem implements Comparable<EditedStyleItem> {
  private final static Logger LOG = Logger.getInstance(EditedStyleItem.class);
  private final static String DEPRECATED = "deprecated";

  private final ConfiguredThemeEditorStyle mySourceTheme;
  private final ConfiguredElement<ItemResourceValue> mySelectedValue;
  /** List of possible values (excluding the currently selected one) indexed by the configuration */
  private final Collection<ConfiguredElement<ItemResourceValue>> myNonSelectedValues;
  private final String myAttrGroup;

  /**
   * Constructs a new {@code EditedStyleItem} with the selected value by the current configuration plus all the values
   * that exist in the project but are not selected.
   */
  public EditedStyleItem(@NotNull ConfiguredElement<ItemResourceValue> selectedValue,
                         @NotNull Iterable<ConfiguredElement<ItemResourceValue>> nonSelectedValues,
                         @NotNull ConfiguredThemeEditorStyle sourceTheme) {
    mySourceTheme = sourceTheme;
    myNonSelectedValues = ImmutableList.copyOf(nonSelectedValues);
    mySelectedValue = selectedValue;

    AttributeDefinition attrDef = ResolutionUtils.getAttributeDefinition(sourceTheme.getConfiguration(), mySelectedValue.myValue);
    String attrGroup = (attrDef == null) ? null : attrDef.getAttrGroup();
    myAttrGroup = (attrGroup == null) ? "Other non-theme attributes." : attrGroup;
  }

  /**
   * Constructs a new {@code EditedStyleItem} that only contains a default value.
   */
  public EditedStyleItem(@NotNull ConfiguredElement<ItemResourceValue> selectedValue,
                         @NotNull ConfiguredThemeEditorStyle sourceTheme) {
    this(selectedValue, Collections.<ConfiguredElement<ItemResourceValue>>emptyList(), sourceTheme);
  }

  public ItemResourceValue getSelectedValue() {
    return mySelectedValue.myValue;
  }

  @NotNull
  public String getAttrGroup() {
    return myAttrGroup;
  }

  @NotNull
  public String getValue() {
    return ResolutionUtils.getQualifiedValue(getSelectedValue());
  }

  @NotNull
  public String getName() {
    return getSelectedValue().getName();
  }

  public boolean isFrameworkAttr() {
    return getSelectedValue().isFrameworkAttr();
  }

  @NotNull
  public ConfiguredThemeEditorStyle getSourceStyle() {
    return mySourceTheme;
  }

  /**
   * Returns the {@link FolderConfiguration} associated to the {@link #getValue} call.
   * <p/>
   * This can be used to retrieve the folder description from where the value was retrieved from.
   */
  @NotNull
  public FolderConfiguration getSelectedValueConfiguration() {
    return mySelectedValue.getConfiguration();
  }

  @NotNull
  public ConfiguredElement<ItemResourceValue> getSelectedItemResourceValue() {
    return mySelectedValue;
  }

  @NotNull
  public Collection<ConfiguredElement<ItemResourceValue>> getAllConfiguredItems() {
    return ImmutableList.<ConfiguredElement<ItemResourceValue>>builder()
      .add(mySelectedValue)
      .addAll(getNonSelectedItemResourceValues())
      .build();
  }

  @NotNull
  public Collection<ConfiguredElement<ItemResourceValue>> getNonSelectedItemResourceValues() {
    return myNonSelectedValues;
  }

  /**
   * Returns whether this attribute value points to an attr reference.
   */
  public boolean isAttr() {
    ResourceUrl url = ResourceUrl.parse(getSelectedValue().getRawXmlValue(), getSelectedValue().isFramework());
    return url != null && url.type == ResourceType.ATTR;
  }

  @Override
  public String toString() {
    StringBuilder output = new StringBuilder(
      String.format("[%1$s] %2$s = %3$s (%4$s)", mySourceTheme, getName(), getValue(), mySelectedValue.myFolderConfiguration));

    for (ConfiguredElement<ItemResourceValue> item : myNonSelectedValues) {
      output.append('\n')
        .append(String.format("   %1$s = %2$s (%3$s)", item.myValue.getName(), item.myValue.getValue(), item.getConfiguration()));
    }

    return output.toString();
  }

  @NotNull
  public String getQualifiedName() {
    return ResolutionUtils.getQualifiedItemName(getSelectedValue());
  }

  public String getAttrPropertyName() {
    if (!isAttr()) {
      return "";
    }

    String propertyName = Splitter.on('/').limit(2).splitToList(getValue()).get(1);
    return (getValue().startsWith(SdkConstants.ANDROID_THEME_PREFIX) ?
      SdkConstants.PREFIX_ANDROID :
      "") + propertyName;
  }

  public boolean isDeprecated() {
    AttributeDefinition def = ResolutionUtils.getAttributeDefinition(mySourceTheme.getConfiguration(), getSelectedValue());
    String doc = (def == null) ? null : def.getDocValue(null);
    return (doc != null && StringUtil.containsIgnoreCase(doc, DEPRECATED));
  }

  public boolean isPublicAttribute() {
    if (!getSelectedValue().isFrameworkAttr()) {
      return true;
    }
    Configuration configuration = mySourceTheme.getConfiguration();
    IAndroidTarget target = configuration.getTarget();
    if (target == null) {
      LOG.error("Unable to get IAndroidTarget.");
      return false;
    }

    AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, configuration.getModule());
    if (androidTargetData == null) {
      LOG.error("Unable to get AndroidTargetData.");
      return false;
    }

    return androidTargetData.isResourcePublic(ResourceType.ATTR.getName(), getName());
  }

  @Override
  public int compareTo(EditedStyleItem that) {
    return getName().compareTo(that.getName());
  }
}
