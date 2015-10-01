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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.google.common.collect.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This class represents styles in ThemeEditor
 * In addition to {@link ThemeEditorStyle}, it knows about current {@link Configuration} used in ThemeEditor
 * TODO: move Configuration independent methods to ThemeEditorStyle
 */
public class ConfiguredThemeEditorStyle extends ThemeEditorStyle {

  private final @NotNull StyleResourceValue myStyleResourceValue;
  private final @NotNull Configuration myConfiguration;

  /**
   * Source module of the theme, set to null if the theme comes from external libraries or the framework.
   * For currently edited theme stored in {@link ThemeEditorContext#getCurrentContextModule()}.
   */
  private final @Nullable Module mySourceModule;

  public ConfiguredThemeEditorStyle(final @NotNull Configuration configuration,
                                    final @NotNull StyleResourceValue styleResourceValue,
                                    final @Nullable Module sourceModule) {
    super(configuration.getConfigurationManager(), ResolutionUtils.getQualifiedStyleName(styleResourceValue));
    myStyleResourceValue = styleResourceValue;
    myConfiguration = configuration;
    mySourceModule = sourceModule;
  }

  /**
   * @return url representation of this style,
   * Result will start either with {@value SdkConstants#ANDROID_STYLE_RESOURCE_PREFIX} or {@value SdkConstants#STYLE_RESOURCE_PREFIX}
   */
  @NotNull
  public String getStyleResourceUrl() {
    return ResourceUrl.create(myStyleResourceValue).toString();
  }

  /**
   * Returns StyleResourceValue for current Configuration
   */
  @NotNull
  public StyleResourceValue getStyleResourceValue() {
    return myStyleResourceValue;
  }

  /**
   * Returns whether this style is editable.
   */
  public boolean isReadOnly() {
    return !isProjectStyle();
  }

  /**
   * Returns all the style attributes and its values. For each attribute, multiple {@link ConfiguredElement} can be returned
   * representing the multiple values in different configurations for each item.
   * TODO: needs to be deleted, as we don't use this method except tests
   */
  @NotNull
  public ImmutableCollection<ConfiguredElement<ItemResourceValue>> getConfiguredValues() {
    // Get a list of all the items indexed by the item name. Each item contains a list of the
    // possible values in this theme in different configurations.
    //
    // If item1 has multiple values in different configurations, there will be an
    // item1 = {folderConfiguration1 -> value1, folderConfiguration2 -> value2}
    final ImmutableList.Builder<ConfiguredElement<ItemResourceValue>> itemResourceValues = ImmutableList.builder();

    if (isFramework()) {
      assert myConfiguration.getFrameworkResources() != null;

      com.android.ide.common.resources.ResourceItem styleItem =
        myConfiguration.getFrameworkResources().getResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
      // Go over all the files containing the resource.
      for (ResourceFile file : styleItem.getSourceFileList()) {
        ResourceValue styleResourceValue = file.getValue(ResourceType.STYLE, styleItem.getName());
        FolderConfiguration folderConfiguration = file.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            itemResourceValues.add(ConfiguredElement.create(folderConfiguration, value));
          }
        }
      }
    }
    else {
      for (ResourceItem styleDefinition : getStyleResourceItems()) {
        ResourceValue styleResourceValue = styleDefinition.getResourceValue(isFramework());
        FolderConfiguration folderConfiguration = styleDefinition.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            // We use the qualified name since apps and libraries can use the same attribute name twice with and without "android:"
            itemResourceValues.add(ConfiguredElement.create(folderConfiguration, value));
          }
        }
      }
    }

    return itemResourceValues.build();
  }

  /**
   * Returns the names of all the parents of this style. Parents might differ depending on the folder configuration, this returns all the
   * variants for this style.
   */
  public Collection<ConfiguredElement<String>> getParentNames() {
    if (isFramework()) {
      // Framework themes do not have multiple parents so we just get the only one.
      ConfiguredThemeEditorStyle parent = getParent();
      if (parent != null) {
        return ImmutableList.of(ConfiguredElement.create(new FolderConfiguration(), parent.getQualifiedName()));
      }
      // The theme has no parent (probably the main "Theme" style)
      return Collections.emptyList();
    }

    ImmutableList.Builder<ConfiguredElement<String>> parents = ImmutableList.builder();
    for (final ResourceItem styleItem : getStyleResourceItems()) {
      StyleResourceValue style = (StyleResourceValue)styleItem.getResourceValue(false);
      assert style != null;
      String parentName = ResolutionUtils.getParentQualifiedName(style);
      if (parentName != null) {
        parents.add(ConfiguredElement.create(styleItem.getConfiguration(), parentName));
      }
    }
    return parents.build();
  }

  public boolean hasItem(@Nullable EditedStyleItem item) {
    //TODO: add isOverriden() method to EditedStyleItem
    return item != null && getStyleResourceValue().getItem(item.getName(), item.isFrameworkAttr()) != null;
  }

  public ItemResourceValue getItem(@NotNull String name, boolean isFramework) {
    return getStyleResourceValue().getItem(name, isFramework);
  }

  /**
   * See {@link #getParent(ThemeResolver)}
   */
  public ConfiguredThemeEditorStyle getParent() {
    return getParent(null);
  }

  /**
   * @param themeResolver theme resolver that would be used to look up parent theme by name
   *                      Pass null if you don't care about resulting ThemeEditorStyle source module (which would be null in that case)
   * @return the style parent
   */
  @Nullable("if this is a root style")
  public ConfiguredThemeEditorStyle getParent(@Nullable ThemeResolver themeResolver) {
    ResourceResolver resolver = myConfiguration.getResourceResolver();
    assert resolver != null;

    StyleResourceValue parent = resolver.getParent(getStyleResourceValue());
    if (parent == null) {
      return null;
    }

    if (themeResolver == null) {
      return ResolutionUtils.getStyle(myConfiguration, ResolutionUtils.getQualifiedStyleName(parent), null);
    }
    else {
      return themeResolver.getTheme(ResolutionUtils.getQualifiedStyleName(parent));
    }
  }

  @Override
  public String toString() {
    if (!isReadOnly()) {
      return "[" + getName() + "]";
    }

    return getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || (!(obj instanceof ConfiguredThemeEditorStyle))) {
      return false;
    }

    return getQualifiedName().equals(((ConfiguredThemeEditorStyle)obj).getQualifiedName());
  }

  @Override
  public int hashCode() {
    return getQualifiedName().hashCode();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  /**
   * Plain getter, see {@link #mySourceModule} for field description.
   */
  @Nullable
  public Module getSourceModule() {
    return mySourceModule;
  }
}
