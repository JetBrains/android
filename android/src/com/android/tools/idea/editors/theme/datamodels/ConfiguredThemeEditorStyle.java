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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValueImpl;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class represents styles in ThemeEditor. In addition to {@link ThemeEditorStyle},
 * it knows about current {@link Configuration} used in ThemeEditor.
 * TODO: Move Configuration independent methods to ThemeEditorStyle.
 */
public class ConfiguredThemeEditorStyle extends ThemeEditorStyle {
  private final @NotNull StyleResourceValueImpl myStyleResourceValue;
  private final @NotNull Configuration myConfiguration;

  /**
   * Source module of the theme, set to null if the theme comes from external libraries or the framework.
   * For currently edited theme stored in {@link ThemeEditorContext#getCurrentContextModule()}.
   */
  private final @Nullable Module mySourceModule;

  public ConfiguredThemeEditorStyle(@NotNull Configuration configuration,
                                    @NotNull StyleResourceValue styleResourceValue,
                                    @Nullable Module sourceModule) {
    super(configuration.getConfigurationManager(), styleResourceValue.asReference());
    myStyleResourceValue = StyleResourceValueImpl.copyOf(styleResourceValue);
    myConfiguration = configuration;
    mySourceModule = sourceModule;
  }

  /**
   * Returns the url representation of this style. The result will start either with
   * {@value SdkConstants#ANDROID_STYLE_RESOURCE_PREFIX} or {@value SdkConstants#STYLE_RESOURCE_PREFIX}.
   */
  @NotNull
  public String getStyleResourceUrl() {
    return myStyleResourceValue.getResourceUrl().toString();
  }

  /**
   * Returns StyleResourceValueImpl for the current Configuration.
   */
  @NotNull
  public StyleResourceValueImpl getStyleResourceValue() {
    return myStyleResourceValue;
  }

  /**
   * Returns whether this style is editable.
   */
  public boolean isReadOnly() {
    return !isProjectStyle();
  }

  public StyleItemResourceValue getItem(@NotNull String name, boolean isFramework) {
    // TODO: namespaces
    return getStyleResourceValue().getItem(ResourceNamespace.fromBoolean(isFramework), name);
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
  @Nullable/*if this is a root style*/
  public ConfiguredThemeEditorStyle getParent(@Nullable ThemeResolver themeResolver) {
    ResourceResolver resolver = myConfiguration.getResourceResolver();
    assert resolver != null;

    StyleResourceValue parent = resolver.getParent(getStyleResourceValue());
    if (parent == null) {
      return null;
    }

    if (themeResolver == null) {
      return ResolutionUtils.getThemeEditorStyle(myConfiguration, parent.asReference(), null);
    }
    else {
      return themeResolver.getTheme(parent.asReference());
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
    if (!(obj instanceof ConfiguredThemeEditorStyle)) {
      return false;
    }

    return getStyleReference().equals(((ConfiguredThemeEditorStyle)obj).getStyleReference());
  }

  @Override
  public int hashCode() {
    return getStyleReference().hashCode();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }
}
