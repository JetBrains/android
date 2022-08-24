/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.refactoring;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeStyleUsageInfo;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeThemeUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Parses the appcompat.aar!/public.txt and keeps the attributes and
 * styles around to be used during a {@link MigrateToAppCompatProcessor} handling.
 * This class also provides methods to find usages as well as apply changes on
 * styles, themes and attributes.
 */
class AppCompatStyleMigration {

  private final Set<String> myAppCompatAttributes;
  private final Set<String> myAppCompatStyles;

  AppCompatStyleMigration(@NonNull Set<String> appCompatAttributes, @NonNull Set<String> appCompatStyles) {
    myAppCompatAttributes = ImmutableSet.copyOf(appCompatAttributes);
    myAppCompatStyles = ImmutableSet.copyOf(appCompatStyles);
  }

  private boolean isAppCompatAttribute(@NonNull String attribute) {
    return myAppCompatAttributes.contains(attribute);
  }

  private boolean isAppCompatStyle(@NonNull String styleName) {
    return myAppCompatStyles.contains(styleName);
  }

  /**
   * Given an {@code AndroidFacet}, find all the themes used in the AndroidManifest.xml
   * either in the application element or on the activities.
   * Find all declared styles
   *
   * @param project
   * @param facet
   * @return list of UsageInfo's pointing to the attributes that need to be modified
   */
  @NotNull
  public List<ChangeStyleUsageInfo> findStyleElementsToBeModified(@NotNull Project project, @NotNull AndroidFacet facet) {
    ConfigurationManager configManager = ConfigurationManager.getOrCreateInstance(facet.getModule());

    if (configManager.getTarget() == null) {
      return Collections.emptyList();
    }

    ResourceRepository frameworkResources = configManager.getResolverCache()
      .getFrameworkResources(new FolderConfiguration(), configManager.getTarget());

    List<ChangeStyleUsageInfo> result = new ArrayList<>();

    if (frameworkResources == null) {
      return Collections.emptyList();
    }

    addStyleUsagesFromManifest(facet, frameworkResources, result);

    LocalResourceRepository projectResources = ResourceRepositoryManager.getProjectResources(facet);

    addUsagesFromStyles(project, frameworkResources, result, projectResources);

    addUsagesFromLayout(project, result, projectResources);

    return result;
  }

  private void addUsagesFromLayout(Project project,
                                   List<ChangeStyleUsageInfo> result,
                                   LocalResourceRepository projectResources) {

    Set<XmlFile> psiLayoutFiles =
      MigrateToAppCompatUtil.getPsiFilesOfType(project, projectResources, ResourceType.LAYOUT);

    for (XmlFile layoutFile : psiLayoutFiles) {

      layoutFile.accept(new XmlRecursiveElementVisitor() {
        @Override
        public void visitXmlTag(@NotNull XmlTag tag) {
          super.visitXmlTag(tag);

          XmlAttribute attr = tag.getAttribute(ATTR_STYLE);
          themeOrStyle2UsageInfo(attr, result);

          // Other ?android: style attributes
          for (XmlAttribute attribute : tag.getAttributes()) {
            if (ANDROID_URI.equals(attribute.getNamespace())) {
              String attributeValue = attribute.getValue();
              if (attributeValue == null) {
                continue;
              }
              if (attributeValue.startsWith(PREFIX_THEME_REF)
                  && attributeValue.contains(PREFIX_ANDROID)) {
                ResourceUrl url = ResourceUrl.parse(attributeValue);
                if (url != null && url.isFramework() && isAppCompatAttribute(url.name)) {
                  ResourceUrl toChange;
                  if (url.isTheme()) {
                    toChange = ResourceUrl.createThemeReference(null, ResourceType.ATTR, url.name);
                  } else {
                    toChange = ResourceUrl.create(null, ResourceType.ATTR, url.name);
                  }
                  String newResource = toChange.toString();

                  //noinspection ConstantConditions
                  result.add(new ChangeStyleUsageInfo(attribute.getValueElement(), attributeValue, newResource));
                }
              }
              else if (attributeValue.startsWith(PREFIX_RESOURCE_REF)
                       && attributeValue.contains(PREFIX_ANDROID)){
                // style ref
                themeOrStyle2UsageInfo(attribute, result);
              }
            }
          }
        }
      });

    }
  }

  private void themeOrStyle2UsageInfo(XmlAttribute styleAttr, List<ChangeStyleUsageInfo> result) {
    if (styleAttr != null && styleAttr.getValue() != null) {
      String attrValue = styleAttr.getValue();
      ResourceUrl attrRes = ResourceUrl.parse(attrValue);
      if (attrRes == null) {
        return;
      }
      if (attrRes.isFramework()) {
        // example:
        // @android:style/TextAppearance.Material.Widget.Button =>
        // @style/TextAppearance.AppCompat.Widget.Button
        Pair<Boolean, String> converted = replaceStyleNameIfContains(attrRes.name, ".Material");
        if (!converted.first) {
          return;
        }
        if (isAppCompatStyle(converted.second)) {
          ResourceUrl toChange = ResourceUrl.create(null, ResourceType.STYLE, converted.second);
          result.add(new ChangeStyleUsageInfo(styleAttr, styleAttr.getValue(), toChange.toString()));
        }
      }
    }
  }

  private void addUsagesFromStyles(@NotNull Project project,
                                   ResourceRepository frameworkResources,
                                   List<ChangeStyleUsageInfo> result,
                                   LocalResourceRepository projectResources) {
    Set<XmlFile> xmlFiles = MigrateToAppCompatUtil.getPsiFilesOfType(project, projectResources, ResourceType.STYLE);

    for (XmlFile xmlFile : xmlFiles) {

      XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag == null) {
        continue;
      }
      XmlTag[] styleTags = rootTag.findSubTags(ATTR_STYLE);

      for (XmlTag styleTag : styleTags) {

        XmlAttribute parent = styleTag.getAttribute(ATTR_PARENT);
        // <style name="AppTheme" parent="android:Theme.Material...">
        if (parent != null && parent.getValue() != null && parent.getValueElement() != null) {
          String parentValue = parent.getValue();
          String parentStyle = StringUtil.trimStart(parentValue, PREFIX_ANDROID);
          if (parentValue.startsWith(PREFIX_ANDROID)
              && frameworkResources.hasResources(ResourceNamespace.ANDROID, ResourceType.STYLE, parentStyle)) {
            String changeToStyle = toAppCompatThemeOrStyleName(parentStyle);
            // Ensure that the final resulting name is present in the AppCompat styles
            if (isAppCompatStyle(changeToStyle)) {
              result.add(new ChangeThemeUsageInfo(parent.getValueElement(),
                                                  // remember to pass in the actual value here since it is
                                                  // validated when changing the theme.
                                                  parent.getValue(), changeToStyle));
            }
          }
        }

        XmlTag[] items = styleTag.findSubTags(TAG_ITEM);

        for (XmlTag xmlItemTag : items) {
          // example: <item name="android:windowNoTitle">?android:selectableItemBackground</item>
          // Process attribute name such as name="android:windowNoTitle"
          String itemNameAttr = xmlItemTag.getAttributeValue(ATTR_NAME);
          String itemNameNoAndroidPrefix = itemNameAttr == null ? null : StringUtil.trimStart(itemNameAttr, PREFIX_ANDROID);
          if (itemNameAttr != null
              && itemNameAttr.startsWith(PREFIX_ANDROID)
              && frameworkResources.hasResources(ResourceNamespace.ANDROID, ResourceType.ATTR, itemNameNoAndroidPrefix)
              && isAppCompatAttribute(itemNameNoAndroidPrefix)) {

            //noinspection ConstantConditions
            result.add(new ChangeStyleUsageInfo(xmlItemTag.getAttribute(ATTR_NAME),
                                                itemNameAttr,
                                                itemNameNoAndroidPrefix));
          }

          // Process item body such as ?android:selectableItemBackground
          XmlTagValue tagValue = xmlItemTag.getValue();
          String tagValueText = tagValue.getText();
          if (hasResourceItem(frameworkResources, tagValueText)) {
            ResourceUrl attrUrl = ResourceUrl.parse(tagValueText);

            if (attrUrl != null && attrUrl.isFramework() && isAppCompatAttribute(attrUrl.name)
                && xmlItemTag.getValue().getTextElements().length == 1) {
              ResourceUrl changeToStyleAttr;
              if (attrUrl.isTheme()) {
                changeToStyleAttr = ResourceUrl.createThemeReference(null, ResourceType.ATTR, attrUrl.name);
              } else {
                changeToStyleAttr = ResourceUrl.create(null, ResourceType.ATTR, attrUrl.name);
              }

              String changedStyle = changeToStyleAttr.toString();
              result.add(new ChangeStyleUsageInfo(xmlItemTag.getValue().getTextElements()[0],
                                                  tagValueText, changedStyle));
            }
          }
        }
      }
    }
  }

  private void addStyleUsagesFromManifest(@NotNull AndroidFacet facet,
                                          ResourceRepository frameworkResources,
                                          List<ChangeStyleUsageInfo> result) {
    // Find all the themes used by activities or the application attribute
    // from AndroidManifest.xml

    Manifest manifest = Manifest.getMainManifest(facet);
    if (manifest != null) {
      XmlTag applicationTag = manifest.getApplication().getXmlTag();
      if (applicationTag != null) {
        XmlAttribute theme = applicationTag.getAttribute(ATTR_THEME, ANDROID_URI);
        convertThemeAttr2UsageInfo(theme, frameworkResources, result);
      }
      List<Activity> activities = manifest.getApplication().getActivities();
      for (Activity activity : activities) {
        XmlAttribute activityTheme = activity.getXmlTag().getAttribute(ATTR_THEME, ANDROID_URI);
        convertThemeAttr2UsageInfo(activityTheme, frameworkResources, result);
      }
    }
  }

  private static String toAppCompatThemeOrStyleName(@NonNull String themeName) {
    Pair<Boolean, String> result;
    if ((result = replaceStyleNameIfContains(themeName, ".Material")).first ||
        (result = replaceStyleNameIfContains(themeName, ".Holo")).first) {
      return result.second;
    }
    return themeName;
  }

  /**
   * Replace the style/theme with '.AppCompat' if the theme/style ends with '.Material' or '.Holo'
   * otherwise replace it with '.AppCompat.' if the theme/style contains '.Material' or '.Holo.'
   */
  private static Pair<Boolean, String> replaceStyleNameIfContains(@NotNull String originalStyleName,
                                                                  @NotNull String findWithoutTrailingDot) {

    if (originalStyleName.endsWith(findWithoutTrailingDot)) {
      return Pair.create(true, StringUtil.replace(originalStyleName, findWithoutTrailingDot, ".AppCompat"));
    } else if (originalStyleName.contains(findWithoutTrailingDot + ".")) {
      return Pair.create(true, StringUtil.replace(originalStyleName, findWithoutTrailingDot + ".", ".AppCompat."));
    }
    return Pair.create(false, null);
  }

  private void convertThemeAttr2UsageInfo(XmlAttribute theme,
                                          ResourceRepository frameworkResources,
                                          List<ChangeStyleUsageInfo> result) {
    if (theme != null && theme.getValueElement() != null && theme.getValue() != null
        && hasResourceItem(frameworkResources, theme.getValue())) {
      String themeValue = theme.getValue();
      ResourceUrl themeUrl = ResourceUrl.parse(themeValue);
      if (themeUrl != null) {
        String appCompatThemeName = toAppCompatThemeOrStyleName(themeUrl.name);
        if (isAppCompatStyle(appCompatThemeName)) {
          ResourceUrl changeToStyleAttr = ResourceUrl.create(null, ResourceType.STYLE, appCompatThemeName);
          result.add(new ChangeStyleUsageInfo(theme.getValueElement(), themeValue, changeToStyleAttr.toString()));
        }
      }
    }
  }

  /**
   * Returns true if this resource repository contains a resource of the given URL.
   *
   * @param frameworkResources the repository containing framework resources
   * @param url the resource URL
   * @return true if the resource is present in the repository
   */
  private static boolean hasResourceItem(@NotNull ResourceRepository frameworkResources, @NotNull String url) {
    // Handle theme references
    if (url.startsWith(PREFIX_THEME_REF)) {
      String remainder = url.substring(PREFIX_THEME_REF.length());
      if (url.startsWith(ATTR_REF_PREFIX)) {
        url = PREFIX_RESOURCE_REF + url.substring(PREFIX_THEME_REF.length());
        return hasResourceItem(frameworkResources, url);
      }
      int colon = url.indexOf(':');
      if (colon >= 0) {
        // Convert from ?android:progressBarStyleBig to ?android:attr/progressBarStyleBig
        if (remainder.indexOf('/', colon) == -1) {
          remainder = remainder.substring(0, colon) + RESOURCE_CLZ_ATTR + '/'
                      + remainder.substring(colon);
        }
        url = PREFIX_RESOURCE_REF + remainder;
        return hasResourceItem(frameworkResources, url);
      } else {
        int slash = url.indexOf('/');
        if (slash < 0) {
          url = PREFIX_RESOURCE_REF + RESOURCE_CLZ_ATTR + '/' + remainder;
          return hasResourceItem(frameworkResources, url);
        }
      }
    }

    if (!url.startsWith(PREFIX_RESOURCE_REF)) {
      return false;
    }

    assert url.startsWith("@") || url.startsWith("?") : url;

    int typeEnd = url.indexOf('/', 1);
    if (typeEnd >= 0) {
      int nameBegin = typeEnd + 1;

      // Skip @ and @+
      int typeBegin = url.startsWith("@+") ? 2 : 1; //$NON-NLS-1$

      int colon = url.lastIndexOf(':', typeEnd);
      ResourceNamespace namespace = ResourceNamespace.RES_AUTO;
      if (colon >= 0) {
        if (colon - typeBegin == ANDROID_NS_NAME.length()
            && url.startsWith(ANDROID_NS_NAME, typeBegin)) {
          namespace = ResourceNamespace.ANDROID;
        } else {
          // TODO: namespaces
          namespace = ResourceNamespace.TODO();
        }
        typeBegin = colon + 1;
      }

      String typeName = url.substring(typeBegin, typeEnd);
      ResourceType type = ResourceType.fromXmlValue(typeName);
      if (type != null) {
        String name = url.substring(nameBegin);
        return frameworkResources.hasResources(namespace, type, name);
      }
    }

    return false;
  }
}
