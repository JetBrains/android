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

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.lint.checks.ApiLookup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;

/**
 * Utility methods for style resolution.
 */
public class ResolutionUtils {

  private static final Logger LOG = Logger.getInstance(ResolutionUtils.class);

  // Utility methods class isn't meant to be constructed, all methods are static.
  private ResolutionUtils() { }

  /**
   * @return ResourceUrl representation of a style from qualifiedName
   * e.g. for "android:Theme" returns "@android:style/Theme" or for "AppTheme" returns "@style/AppTheme"
   */
  @NotNull
  public static String getStyleResourceUrl(@NotNull String qualifiedName) {
    return getResourceUrlFromQualifiedName(qualifiedName, TAG_STYLE);
  }

  public static String getResourceUrlFromQualifiedName(@NotNull String qualifiedName, @NotNull String type) {
    String startChar = TAG_ATTR.equals(type) ? PREFIX_THEME_REF : PREFIX_RESOURCE_REF;
    int colonIndex = qualifiedName.indexOf(':');
    if (colonIndex != -1) {
      // The theme name contains a namespace, change the format to be "@namespace:style/ThemeName"
      String namespace = qualifiedName.substring(0, colonIndex + 1); // Namespace plus + colon
      String themeNameWithoutNamespace = StringUtil.trimStart(qualifiedName, namespace);
      return startChar + namespace + type + "/" + themeNameWithoutNamespace;
    }
    return startChar + type + "/" + qualifiedName;
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
   * Returns the item name, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedItemName(@NotNull ItemResourceValue item) {
    String name = item.getName();
    return item.isFrameworkAttr() ? PREFIX_ANDROID + name : name;
  }

  /**
   * Returns item value, maybe with "android:" qualifier,
   * If item is inside of the framework style, "android:" qualifier will be added
   * For example: For a value "@color/black" which is inside the "Theme.Holo.Light.DarkActionBar" style,
   * will be returned as "@android:color/black"
   */
  @NotNull
  public static String getQualifiedValue(@NotNull ItemResourceValue item) {
    ResourceUrl url = ResourceUrl.parse(item.getRawXmlValue(), item.isFramework());
    return url == null ? item.getRawXmlValue() : url.toString();
  }

  @Nullable
  private static StyleResourceValue getStyleResourceValue(@NotNull ResourceResolver resolver, @NotNull String qualifiedStyleName) {
    assert !qualifiedStyleName.startsWith(ANDROID_STYLE_RESOURCE_PREFIX);
    assert !qualifiedStyleName.startsWith(STYLE_RESOURCE_PREFIX);
    String styleName;
    boolean isFrameworkStyle;

    if (qualifiedStyleName.startsWith(PREFIX_ANDROID)) {
      styleName = qualifiedStyleName.substring(PREFIX_ANDROID.length());
      isFrameworkStyle = true;
    } else {
      styleName = qualifiedStyleName;
      isFrameworkStyle = false;
    }

    return resolver.getStyle(styleName, isFrameworkStyle);
  }

  /**
   * Constructs a {@link ConfiguredThemeEditorStyle} instance for a theme with the given name and source module, using the passed resolver.
   */
  @Nullable
  public static ConfiguredThemeEditorStyle getStyle(@NotNull Configuration configuration, @NotNull ResourceResolver resolver, @NotNull final String qualifiedStyleName, @Nullable Module module) {
    final StyleResourceValue style = getStyleResourceValue(resolver, qualifiedStyleName);
    return style == null ? null : new ConfiguredThemeEditorStyle(configuration, style, module);
  }

  @Nullable
  public static ConfiguredThemeEditorStyle getStyle(@NotNull Configuration configuration, @NotNull final String qualifiedStyleName, @Nullable Module module) {
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;
    return getStyle(configuration, configuration.getResourceResolver(), qualifiedStyleName, module);
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull Configuration configuration, @NotNull ItemResourceValue itemResValue) {
    return getAttributeDefinition(configuration.getModule(), configuration, getQualifiedItemName(itemResValue));
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull Module module, @Nullable Configuration configuration, @NotNull String name) {
    AttributeDefinitions definitions;

    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
      IAndroidTarget target;
      if (configuration == null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        assert facet != null;
        target = facet.getConfigurationManager().getDefaultTarget(); // same as getHighestApiTarget();
      }
      else {
        target = configuration.getRealTarget();
      }
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

    ApiLookup apiLookup = IntellijLintClient.getApiLookup(project);
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
      return apiLookup.getFieldVersion("android/R$attr", name.substring(ANDROID_NS_NAME_PREFIX_LEN));
    } else {
      if (!resUrl.framework) {
        // not an android value
        return -1;
      }
      return apiLookup.getFieldVersion("android/R$" + resUrl.type, AndroidResourceUtil.getFieldNameByResourceName(resUrl.name));
    }
  }

  @Nullable("if this style doesn't have parent")
  public static String getParentQualifiedName(@NotNull StyleResourceValue style) {
    String parentName = ResourceResolver.getParentName(style);
    if (parentName == null) {
      return null;
    }
    if (parentName.startsWith(PREFIX_RESOURCE_REF)) {
      parentName = getQualifiedNameFromResourceUrl(parentName);
    }
    if (style.isFramework() && !parentName.startsWith(PREFIX_ANDROID)) {
      parentName = PREFIX_ANDROID + parentName;
    }
    return parentName;
  }

  @NotNull
  public static Collection<ItemResourceValue> getThemeAttributes(@NotNull ResourceResolver resolver, final @NotNull String themeUrl) {
    Map<String, ItemResourceValue> allItems = new HashMap<>();
    String themeName = getQualifiedNameFromResourceUrl(themeUrl);
    do {
      StyleResourceValue theme = resolver.getStyle(getNameFromQualifiedName(themeName), themeName.startsWith(PREFIX_ANDROID));
      if (theme == null) {
        break;
      }
      Collection<ItemResourceValue> themeItems = theme.getValues();
      for (ItemResourceValue item : themeItems) {
        String itemName = getQualifiedItemName(item);
        if (!allItems.containsKey(itemName)) {
          allItems.put(itemName, item);
        }
      }

      themeName = getParentQualifiedName(theme);
    } while (themeName != null);

    return allItems.values();
  }

  @Nullable("if we can't work out the type, e.g a 'reference' with a '@null' value or enum")
  public static ResourceType getAttrType(@NotNull ItemResourceValue item, @NotNull Configuration configuration) {
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;
    ResourceValue resolvedValue = resolver.resolveResValue(item);
    ResourceType attrType = resolvedValue.getResourceType();
    if (attrType != null) {
      return attrType;
    }
    else {
      AttributeDefinition def = getAttributeDefinition(configuration, item);
      if (def != null) {
        for (AttributeFormat attrFormat : def.getFormats()) {
          attrType = AndroidDomUtil.getResourceType(attrFormat);
          if (attrType != null) {
            return attrType;
          }
        }
      }
    }
    // sometimes we won't find the type of the attr, this means it's either a reference that points to @null, or a enum
    return null;
  }

  /**
   * Gets the {@link FolderConfiguration} of a ResourceValue
   * e.g. if we resolve a drawable using a mdpi configuration, yet that drawable only exists inside xhdpi, this method will return xhdpi
   * @param configuration the FolderConfiguration that was used for resolving the ResourceValue
   * @return the FolderConfiguration of the ResourceValue
   */
  @NotNull
  public static FolderConfiguration getFolderConfiguration(@NotNull AndroidFacet facet, @NotNull ResourceValue resolvedValue, @NotNull FolderConfiguration configuration) {
    List<? extends Configurable> configurables;
    if (resolvedValue.isFramework()) {
      ConfigurationManager configurationManager = facet.getConfigurationManager();
      IAndroidTarget target = configurationManager.getDefaultTarget(); // same as getHighestApiTarget();
      assert target != null;
      ResourceRepository resourceRepository = configurationManager.getResolverCache().getFrameworkResources(configuration, target);
      assert resourceRepository != null;
      ResourceItem resourceItem = resourceRepository.getResourceItem(resolvedValue.getResourceType(), resolvedValue.getName());
      configurables = resourceItem.getSourceFileList();
    }
    else {
      AppResourceRepository appResourceRepository = facet.getAppResources(true);
      configurables = appResourceRepository.getResourceItem(resolvedValue.getResourceType(), resolvedValue.getName());
    }
    Configurable configurable = configuration.findMatchingConfigurable(configurables);
    assert configurable != null;
    return configurable.getConfiguration();
  }
}
