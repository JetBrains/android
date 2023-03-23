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

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX_LEN;
import static com.android.SdkConstants.PREFIX_ANDROID;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.lint.checks.ApiLookup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.sdk.AndroidPlatforms;
import com.android.tools.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for style resolution.
 */
public class ResolutionUtils {
  private static final Logger LOG = Logger.getInstance(ResolutionUtils.class);

  // Utility methods class isn't meant to be constructed, all methods are static.
  private ResolutionUtils() { }

  /**
   * @deprecated Avoid qualified style and theme names and use {@link ResourceReference}s instead.
   */
  @Deprecated
  @NotNull
  public static ResourceReference getStyleReference(@NotNull String themeName) {
    if (themeName.startsWith(ANDROID_NS_NAME_PREFIX)) {
      return ResourceReference.style(ResourceNamespace.ANDROID, themeName.substring(ANDROID_NS_NAME_PREFIX.length()));
    }
    else {
      assert themeName.indexOf(':') < 0;
      return ResourceReference.style(ResourceNamespace.TODO(), themeName);
    }
  }

  /**
   * @return qualified name of a style from ResourceUrl representation
   * e.g. for "@android:style/Theme" returns "android:Theme" or for "@style/AppTheme" returns "AppTheme"
   */
  @NotNull
  public static String getQualifiedNameFromResourceUrl(@NotNull String styleResourceUrl) {
    ResourceUrl url = ResourceUrl.parse(styleResourceUrl);
    assert url != null : styleResourceUrl;
    return url.namespace != null ? url.namespace + ':' + url.name : url.name;
  }

  /**
   * @return name without qualifier
   * e.g. for "android:Theme" returns "Theme" or for "AppTheme" returns "AppTheme"
   */
  @NotNull
  public static String getNameFromQualifiedName(@NotNull String qualifiedName) {
    int colonIndex = qualifiedName.indexOf(':');
    return colonIndex != -1 ? qualifiedName.substring(colonIndex + 1) : qualifiedName;
  }

  /**
   * Returns the style name, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedStyleName(@NotNull StyleResourceValue style) {
    String name = style.getName();
    return style.isFramework() ? PREFIX_ANDROID + name : name;
  }

  /**
   * Returns the name of the attr for this item, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedItemAttrName(@NotNull StyleItemResourceValue item) {
    ResourceReference attr = item.getAttr();
    return attr != null ? attr.getRelativeResourceUrl(ResourceNamespace.TODO()).getQualifiedName() : item.getAttrName();
  }

  @Nullable
  public static ConfiguredThemeEditorStyle getThemeEditorStyle(@NotNull Configuration configuration,
                                                               @NotNull ResourceReference styleReference) {
    ResourceResolver resolver = configuration.getResourceResolver();
    StyleResourceValue style = resolver.getStyle(styleReference);
    return style == null ? null : new ConfiguredThemeEditorStyle(configuration, style);
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull Configuration configuration,
                                                           @NotNull StyleItemResourceValue itemResValue) {
    ResourceReference attr = itemResValue.getAttr();
    return attr == null ? null : getAttributeDefinition(configuration.getModule(), attr);
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull Module module, @NotNull ResourceReference attr) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : String.format("Module %s is not an Android module", module.getName());

    AttributeDefinitions definitions = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getAttributeDefinitions();
    return definitions.getAttrDefinition(attr);
  }

  /**
   * @deprecated Use {@link ##getAttributeDefinition(Module, ResourceReference)}.
   */
  @Deprecated
  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull Module module, @Nullable Configuration configuration,
                                                           @NotNull String name) {
    AttributeDefinitions definitions;

    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
      IAndroidTarget target;
      if (configuration == null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        assert facet != null;
        target = ConfigurationManager.getOrCreateInstance(module).getDefaultTarget(); // same as getHighestApiTarget();
      }
      else {
        target = configuration.getRealTarget();
      }
      assert target != null;

      AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, AndroidPlatforms.getInstance(module));
      assert androidTargetData != null;

      definitions = androidTargetData.getAllAttrDefs();
    }
    else {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : String.format("Module %s is not an Android module", module.getName());

      definitions = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getAttributeDefinitions();
    }
    return definitions.getAttrDefByName(getNameFromQualifiedName(name));
  }

  /**
   * Returns the Api level at which was defined the attribute or value with the name passed as argument.
   * Returns -1 if the name argument is null or not the name of a framework attribute or resource,
   * or if it is the name of a framework attribute or resource defined in API 1, or if no Lint client found.
   */
  public static int getOriginalApiLevel(@Nullable String name, @NotNull Project project) {
    if (name == null) {
      return -1;
    }

    ApiLookup apiLookup = LintIdeClient.getApiLookup(project);
    if (apiLookup == null) {
      // There is no Lint API database for this project
      LOG.warn("Could not find Lint client for project " + project.getName());
      return -1;
    }

    ResourceUrl resUrl = ResourceUrl.parse(name);
    if (resUrl == null) {
      // It is an attribute
      if (!name.startsWith(ANDROID_NS_NAME_PREFIX)) {
        // not an android attribute
        return -1;
      }
      return apiLookup.getFieldVersions("android/R$attr", name.substring(ANDROID_NS_NAME_PREFIX_LEN)).min();
    } else {
      if (!resUrl.isFramework()) {
        // not an android value
        return -1;
      }
      return apiLookup.getFieldVersions("android/R$" + resUrl.type, IdeResourcesUtil.getFieldNameByResourceName(resUrl.name)).min();
    }
  }

  @Nullable/*if this style doesn't have parent*/
  public static String getParentQualifiedName(@NotNull StyleResourceValue style) {
    ResourceReference parent = style.getParentStyle();
    if (parent == null) {
      return null;
    }

    return parent.getRelativeResourceUrl(ResourceNamespace.TODO()).getQualifiedName();
  }

  @NotNull
  public static Collection<StyleItemResourceValue> getThemeAttributes(@NotNull ResourceResolver resolver, final @NotNull String themeUrl) {
    Map<String, StyleItemResourceValue> allItems = new HashMap<>();
    String themeName = getQualifiedNameFromResourceUrl(themeUrl);
    do {
      StyleResourceValue theme = resolver.getStyle(getNameFromQualifiedName(themeName), themeName.startsWith(PREFIX_ANDROID));
      if (theme == null) {
        break;
      }
      Collection<StyleItemResourceValue> themeItems = theme.getDefinedItems();
      for (StyleItemResourceValue item : themeItems) {
        String itemName = getQualifiedItemAttrName(item);
        if (!allItems.containsKey(itemName)) {
          allItems.put(itemName, item);
        }
      }

      themeName = getParentQualifiedName(theme);
    } while (themeName != null);

    return allItems.values();
  }

  @Nullable/*if we can't work out the type, e.g a 'reference' with a '@null' value or enum*/
  public static ResourceType getAttrType(@NotNull StyleItemResourceValue item, @NotNull Configuration configuration) {
    ResourceResolver resolver = configuration.getResourceResolver();
    return getAttrType(item, resolver);
  }

  @Nullable
  public static ResourceType getAttrType(@NotNull StyleItemResourceValue item, @NotNull ResourceResolver resolver) {
    ResourceValue resolvedValue = resolver.resolveResValue(item);
    return resolvedValue == null ? null : resolvedValue.getResourceType();
  }

  /**
   * Gets the {@link FolderConfiguration} of a ResourceValue
   * e.g. if we resolve a drawable using a mdpi configuration, yet that drawable only exists inside xhdpi, this method will return xhdpi.
   *
   * @param configuration the FolderConfiguration that was used for resolving the ResourceValue
   * @return the FolderConfiguration of the ResourceValue
   */
  @Slow
  @NotNull
  public static FolderConfiguration getFolderConfiguration(@NotNull AndroidFacet facet, @NotNull ResourceValue resolvedValue, @NotNull FolderConfiguration configuration) {
    List<? extends Configurable> configurables;
    if (resolvedValue.isFramework()) {
      ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(facet.getModule());
      IAndroidTarget target = configurationManager.getDefaultTarget(); // same as getHighestApiTarget();
      assert target != null;
      ResourceRepository resourceRepository = configurationManager.getResolverCache().getFrameworkResources(configuration, target);
      assert resourceRepository != null;
      configurables =
          resourceRepository.getResources(ResourceNamespace.ANDROID, resolvedValue.getResourceType(), resolvedValue.getName());
    }
    else {
      LocalResourceRepository LocalResourceRepository = StudioResourceRepositoryManager.getAppResources(facet);
      configurables =
          LocalResourceRepository.getResources(ResourceNamespace.TODO(), resolvedValue.getResourceType(), resolvedValue.getName());
    }
    Configurable configurable = configuration.findMatchingConfigurable(configurables);
    assert configurable != null;
    return configurable.getConfiguration();
  }
}
