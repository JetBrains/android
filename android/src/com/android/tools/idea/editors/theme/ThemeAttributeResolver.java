/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.qualifiers.RestrictedConfiguration;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Helper class to get all the items and values defined in the given style, taking into account the inheritance
 */
public class ThemeAttributeResolver {
  private static final Logger LOG = Logger.getInstance(ThemeAttributeResolver.class);

  final private ConfigurationManager myManager;
  final private ConfiguredThemeEditorStyle myStyle;
  final private MultiMap<String, ConfiguredElement<ItemResourceValue>> myItemValueMap =
    new MultiMap<String, ConfiguredElement<ItemResourceValue>>();

  private ThemeAttributeResolver(ConfiguredThemeEditorStyle style, ConfigurationManager manager) {
    myStyle = style;
    myManager = manager;
  }

  /**
   * @return RestrictedConfiguration that matches to compatible and doesn't match to other FolderConfigurations where the style is defined
   */
  @Nullable("if there is no configuration that matches to restrictions")
  private static RestrictedConfiguration getRestrictedConfiguration(@NotNull ThemeEditorStyle theme,
                                                                    @NotNull FolderConfiguration compatible) {
    ArrayList<FolderConfiguration> incompatibles = Lists.newArrayList();
    for (FolderConfiguration folder : theme.getFolders()) {
      if (!compatible.equals(folder)) {
        incompatibles.add(folder);
      }
    }
    return RestrictedConfiguration.restrict(compatible, incompatibles);
  }

  private void resolveFromInheritance(@NotNull ThemeEditorStyle themeEditorStyle,
                                      @NotNull FolderConfiguration configuration,
                                      @NotNull RestrictedConfiguration restricted,
                                      @NotNull Set<String> seenAttributes) {

    RestrictedConfiguration styleRestricted = getRestrictedConfiguration(themeEditorStyle, configuration);
    if (styleRestricted == null) {
      LOG.warn(configuration + " is unreachable");
      return;
    }
    styleRestricted = restricted.intersect(styleRestricted);
    if (styleRestricted == null) {
      return;
    }

    Set<String> newSeenAttributes = new HashSet<String>(seenAttributes);
    for (ItemResourceValue item : themeEditorStyle.getValues(configuration)) {
      String itemName = ResolutionUtils.getQualifiedItemName(item);
      if (!newSeenAttributes.contains(itemName)) {
        myItemValueMap.putValue(itemName, ConfiguredElement.create(styleRestricted.getAny(), item));
        newSeenAttributes.add(itemName);
      }
    }
    String parentName = themeEditorStyle.getParentName(configuration);

    if (parentName == null) {
      // We have reached the top of the theme hierarchy (i.e "android:Theme")
      return;
    }
    ThemeEditorStyle parent = new ThemeEditorStyle(myManager, parentName);
    for (FolderConfiguration folder : parent.getFolders()) {
      resolveFromInheritance(parent, folder, styleRestricted, newSeenAttributes);
    }
  }

  @NotNull
  private List<EditedStyleItem> resolveAll() {
    ThemeEditorStyle theme = new ThemeEditorStyle(myManager, myStyle.getQualifiedName());
    for (FolderConfiguration folder : theme.getFolders()) {
      resolveFromInheritance(myStyle, folder, new RestrictedConfiguration(), new HashSet<String>());
    }

    List<EditedStyleItem> result = Lists.newArrayList();
    FolderConfiguration configuration = myStyle.getConfiguration().getFullConfig();
    for (String key : myItemValueMap.keySet()) {
      Collection<ConfiguredElement<ItemResourceValue>> itemValues = myItemValueMap.get(key);
      final ConfiguredElement<ItemResourceValue> selectedValue =
        (ConfiguredElement<ItemResourceValue>)configuration.findMatchingConfigurable(Lists.<Configurable>newArrayList(itemValues));
      if (selectedValue == null) {
        // TODO: there is NO value for this attribute in the current config,so instead we need to show "no value for current device"
        result.add(new EditedStyleItem(itemValues.iterator().next(), itemValues, myStyle));
      }
      else {
        itemValues.remove(selectedValue);
        assert !itemValues.contains(selectedValue);
        result.add(new EditedStyleItem(selectedValue, itemValues, myStyle));
      }
    }
    return result;
  }

  public static List<EditedStyleItem> resolveAll(ConfiguredThemeEditorStyle style, ConfigurationManager manager) {
    return new ThemeAttributeResolver(style, manager).resolveAll();
  }
}
