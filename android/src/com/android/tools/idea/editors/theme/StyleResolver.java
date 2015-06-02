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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Utility methods for style resolution.
 */
public class StyleResolver {
  @SuppressWarnings("ConstantNamingConvention") private static final Logger LOG = Logger.getInstance(StyleResolver.class);

  private final Cache<String, ThemeEditorStyle> myStylesCache = CacheBuilder.newBuilder().build();
  private final Configuration myConfiguration;

  public StyleResolver(@NotNull Configuration configuration) {
    myConfiguration = configuration;
  }

  /**
   * Returns the style name, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedStyleName(@NotNull StyleResourceValue style) {
    return (style.isFramework() ? SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX : SdkConstants.STYLE_RESOURCE_PREFIX) + style.getName();
  }

  /**
   * Returns the item name, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedItemName(@NotNull ItemResourceValue item) {
    return (item.isFrameworkAttr() ? SdkConstants.PREFIX_ANDROID : "") + item.getName();
  }

  @NotNull
  public ThemeEditorStyle getStyle(@NotNull final String qualifiedStyleName) {
    try {
      return myStylesCache.get(qualifiedStyleName, new Callable<ThemeEditorStyle>() {
        @Override
        public ThemeEditorStyle call() throws Exception {
          if (qualifiedStyleName.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX)) {
            String styleName = qualifiedStyleName.substring(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX.length());
            return new ThemeEditorStyle(StyleResolver.this, myConfiguration, styleName, true);
          }

          String styleName = qualifiedStyleName;
          if (qualifiedStyleName.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
            styleName = qualifiedStyleName.substring(SdkConstants.STYLE_RESOURCE_PREFIX.length());
          }
          return new ThemeEditorStyle(StyleResolver.this, myConfiguration, styleName, false);
        }
      });
    }
    catch (ExecutionException e) {
      LOG.warn("Unable to retrieve style", e);
      throw new IllegalArgumentException(e);
    }
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull Configuration configuration, @NotNull ItemResourceValue itemResValue) {
    AttributeDefinitions definitions;
    Module module = configuration.getModule();

    if (itemResValue.isFrameworkAttr()) {
      IAndroidTarget target = configuration.getTarget();
      assert target != null;

      AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, module);
      assert androidTargetData != null;

      definitions = androidTargetData.getAllAttrDefs(module.getProject());
    }
    else {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : String.format("Module %s is not an Android module", module.getName());

      definitions = facet.getLocalResourceManager().getAttributeDefinitions();
    }
    if (definitions == null) {
      return null;
    }
    return definitions.getAttrDefByName(itemResValue.getName());
  }
}
