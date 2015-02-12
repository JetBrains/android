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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Utility methods for style resolution.
 */
public class StyleResolver {
  private static final Logger LOG = Logger.getInstance(StyleResolver.class);

  private final Cache<String, ThemeEditorStyle> myStylesCache = CacheBuilder.newBuilder().build();
  private final AttributeDefinitions myAttributeDefinition;
  private final LocalResourceRepository myResourceResolver;
  private final Project myProject;
  private final Configuration myConfiguration;

  public StyleResolver(@NotNull Configuration configuration) {
    myConfiguration = configuration;
    myProject = configuration.getModule().getProject();

    myResourceResolver = AppResourceRepository.getAppResources(configuration.getModule(), true);
    if (myResourceResolver == null) {
      myAttributeDefinition = null;
      LOG.error("Unable to get AppResourceRepository.");
      return;
    }

    IAndroidTarget target = configuration.getTarget();
    if (target == null) {
      myAttributeDefinition = null;
      LOG.error("Unable to get IAndroidTarget.");
      return;
    }

    AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, configuration.getModule());
    if (androidTargetData == null) {
      myAttributeDefinition = null;
      LOG.error("Unable to get AndroidTargetData.");
      return;
    }

    Project project = configuration.getModule().getProject();
    myAttributeDefinition = androidTargetData.getAttrDefs(project);
  }

  /**
   * Returns the style name, including the appropriate namespace.
   */
  @NotNull
  static String getQualifiedStyleName(@NotNull StyleResourceValue style) {
    return (style.isFramework() ? SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX : SdkConstants.STYLE_RESOURCE_PREFIX) + style.getName();
  }

  /**
   * Returns the item name, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedItemName(@NotNull ItemResourceValue item) {
    return (item.isFrameworkAttr() ? SdkConstants.ANDROID_PREFIX : "") + item.getName();
  }

  @Nullable
  public AttributeDefinitions getAttributeDefinitions() {
    return myAttributeDefinition;
  }

  @Nullable
  private StyleResourceValue resolveFrameworkStyle(String styleName) {
    ResourceValue value =
      myConfiguration.getFrameworkResources().getConfiguredResources(myConfiguration.getFullConfig()).get(ResourceType.STYLE)
        .get(styleName);

    if (value != null && value instanceof StyleResourceValue) {
      return (StyleResourceValue)value;
    }

    return null;
  }

  @Nullable
  public ThemeEditorStyle getStyle(@NotNull final String qualifiedStyleName) {
    try {
      return myStylesCache.get(qualifiedStyleName, new Callable<ThemeEditorStyle>() {
        @Override
        public ThemeEditorStyle call() throws Exception {
          if (qualifiedStyleName.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX)) {
            ResourceValue value = resolveFrameworkStyle(qualifiedStyleName.substring(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX.length()));
            if (value == null) {
              throw new ExecutionException("Resource not found for framework style " + qualifiedStyleName, null);
            }

            return new ThemeEditorStyle(StyleResolver.this, myProject, myConfiguration, (StyleResourceValue)value, null);
          }

          List<ResourceItem> resources;
          if (qualifiedStyleName.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
            resources = myResourceResolver
              .getResourceItem(ResourceType.STYLE, qualifiedStyleName.substring(SdkConstants.STYLE_RESOURCE_PREFIX.length()));

          }
          else {
            resources = myResourceResolver.getResourceItem(ResourceType.STYLE, qualifiedStyleName);
          }

          if (resources == null || resources.isEmpty()) {
            throw new ExecutionException("Resource not found for " + qualifiedStyleName, null);
          }

          ResourceValue value = resources.get(0).getResourceValue(false);
          if (!(value instanceof StyleResourceValue)) {
            throw new ExecutionException("Resource not a style for " + qualifiedStyleName, null);
          }

          XmlTag xmlTag = LocalResourceRepository.getItemTag(myProject, resources.get(0));
          return new ThemeEditorStyle(StyleResolver.this, myProject, myConfiguration, (StyleResourceValue)value, xmlTag);
        }
      });
    }
    catch (ExecutionException e) {
      LOG.warn("Unable to retrieve style", e);
    }

    return null;
  }
}
