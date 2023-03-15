/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.dom;

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_ARCH_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_PKG;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_SUPPORT_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ACTION_BAR_NAV_MODE;
import static com.android.SdkConstants.ATTR_COMPOSABLE_NAME;
import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.ATTR_DISCARD;
import static com.android.SdkConstants.ATTR_IGNORE;
import static com.android.SdkConstants.ATTR_ITEM_COUNT;
import static com.android.SdkConstants.ATTR_KEEP;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_LISTFOOTER;
import static com.android.SdkConstants.ATTR_LISTHEADER;
import static com.android.SdkConstants.ATTR_LISTITEM;
import static com.android.SdkConstants.ATTR_MENU;
import static com.android.SdkConstants.ATTR_OPEN_DRAWER;
import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.ATTR_SHOW_AS_ACTION;
import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.ATTR_SHRINK_MODE;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.ATTR_TARGET_API;
import static com.android.SdkConstants.ATTR_VIEW_BINDING_IGNORE;
import static com.android.SdkConstants.ATTR_VIEW_BINDING_TYPE;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.CLASS_COMPOSE_VIEW;
import static com.android.AndroidXConstants.CLASS_DRAWER_LAYOUT;
import static com.android.AndroidXConstants.CLASS_NESTED_SCROLL_VIEW;
import static com.android.SdkConstants.CLASS_PERCENT_FRAME_LAYOUT;
import static com.android.SdkConstants.CLASS_PERCENT_RELATIVE_LAYOUT;
import static com.android.SdkConstants.CLASS_VIEWGROUP;
import static com.android.SdkConstants.FQCN_ADAPTER_VIEW;
import static com.android.AndroidXConstants.FQCN_GRID_LAYOUT_V7;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.AndroidXConstants.RECYCLER_VIEW;
import static com.android.SdkConstants.REQUEST_FOCUS;
import static com.android.SdkConstants.SCROLL_VIEW;
import static com.android.SdkConstants.TABLE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;
import static com.android.SdkConstants.TAG;
import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_IMPORT;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.URI_PREFIX;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.VIEW_GROUP;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.VIEW_TAG;
import static org.jetbrains.android.facet.AndroidClassesForXmlUtilKt.findViewClassByName;
import static org.jetbrains.android.facet.AndroidClassesForXmlUtilKt.findViewValidInXMLByName;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.psi.TagToClassMapper;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.xml.XmlElementDescriptor;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.dom.animation.InterpolatorElement;
import org.jetbrains.android.dom.animation.fileDescriptions.InterpolatorDomFileDescription;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import com.android.tools.dom.attrs.StyleableDefinition;
import org.jetbrains.android.dom.attrs.ToolsAttributeUtil;
import org.jetbrains.android.dom.converters.CompositeConverter;
import org.jetbrains.android.dom.converters.ManifestPlaceholderConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.layout.Data;
import org.jetbrains.android.dom.layout.DataBindingElement;
import org.jetbrains.android.dom.layout.Fragment;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.layout.LayoutViewElementDescriptor;
import org.jetbrains.android.dom.layout.Tag;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElement;
import org.jetbrains.android.dom.manifest.UsesSdk;
import org.jetbrains.android.dom.menu.MenuItem;
import org.jetbrains.android.dom.navigation.NavDestinationElement;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.dom.raw.XmlRawResourceElement;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.Intent;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.TagFromClassDescriptor;
import org.jetbrains.android.resourceManagers.FrameworkResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility functions for enumerating available children attribute types in the context of a given XML tag.
 *
 * Entry point is {@link #processAttributes(AndroidDomElement, AndroidFacet, boolean, AttributeProcessor)},
 * look for a Javadoc there.
 */
public class AttributeProcessingUtil {
  private static final String PREFERENCE_TAG_NAME = "Preference";

  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_TAG_NAMES =
      ImmutableSet.of(VIEW_MERGE, TABLE_ROW, VIEW_INCLUDE, REQUEST_FOCUS, TAG_LAYOUT, TAG_DATA, TAG_IMPORT, TAG);
  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_PARENT_TAG_NAMES = ImmutableSet.of(
      TABLE_ROW, TABLE_LAYOUT, VIEW_MERGE, GRID_LAYOUT, FQCN_GRID_LAYOUT_V7.oldName(), FQCN_GRID_LAYOUT_V7.newName(),
      CLASS_PERCENT_RELATIVE_LAYOUT, CLASS_PERCENT_FRAME_LAYOUT);

  private AttributeProcessingUtil() {
  }

  private static Logger getLog() {
    return Logger.getInstance(AttributeProcessingUtil.class);
  }

  /**
   * Check whether layout tag attribute with given name should be marked as required.
   * Currently, tests for layout_width and layout_height attribute and marks them as required in appropriate context.
   */
  public static boolean isLayoutAttributeRequired(@NotNull XmlName attributeName, @NotNull DomElement element) {
    // Mark layout_width and layout_height required - if the context calls for it
    String localName = attributeName.getLocalName();
    if (!(ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName))) {
      return false;
    }

    if ((element instanceof LayoutViewElement || element instanceof Fragment) && ANDROID_URI.equals(attributeName.getNamespaceKey())) {
      XmlElement xmlElement = element.getXmlElement();
      XmlTag tag = xmlElement instanceof XmlTag ? (XmlTag)xmlElement : null;
      String tagName = tag != null ? tag.getName() : null;

      if (!SIZE_NOT_REQUIRED_TAG_NAMES.contains(tagName) && (tag == null || tag.getAttribute(ATTR_STYLE) == null)) {
        XmlTag parentTag = tag != null ? tag.getParentTag() : null;
        String parentTagName = parentTag != null ? parentTag.getName() : null;

        if (!SIZE_NOT_REQUIRED_PARENT_TAG_NAMES.contains(parentTagName)) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static String getNamespaceUriByResourcePackage(@NotNull AndroidFacet facet, @Nullable String resPackage) {
    if (resPackage == null) {
      if (!facet.getConfiguration().isAppProject() || AndroidModel.isRequired(facet)) {
        return AUTO_URI;
      }
      Manifest manifest = Manifest.getMainManifest(facet);
      if (manifest != null) {
        String aPackage = manifest.getPackage().getValue();
        if (aPackage != null && !aPackage.isEmpty()) {
          return URI_PREFIX + aPackage;
        }
      }
    }
    else if (resPackage.equals(SYSTEM_RESOURCE_PACKAGE)) {
      return ANDROID_URI;
    }
    return null;
  }

  private static void registerStyleableAttributes(@NotNull DomElement element,
                                                  @NotNull StyleableDefinition styleable,
                                                  @Nullable String namespaceUri,
                                                  @NotNull AttributeProcessor callback,
                                                  @NotNull Set<XmlName> skippedAttributes) {
    for (AttributeDefinition attrDef : styleable.getAttributes()) {
      XmlName xmlName = getXmlName(attrDef, namespaceUri);
      if (skippedAttributes.add(xmlName)) {
        registerAttribute(attrDef, xmlName, styleable.getName(), element, callback);
      }
    }
  }

  private static boolean mustBeSoft(@NotNull Converter converter, @NotNull Collection<AttributeFormat> formats) {
    if (converter instanceof CompositeConverter || converter instanceof ResourceReferenceConverter) {
      return false;
    }
    return formats.size() > 1;
  }

  private static void registerAttribute(@NotNull AttributeDefinition attrDef,
                                        @NotNull XmlName xmlName,
                                        @Nullable String parentStyleableName,
                                        @NotNull DomElement element,
                                        @NotNull AttributeProcessor callback) {
    DomExtension extension = callback.processAttribute(xmlName, attrDef, parentStyleableName);
    if (extension == null) {
      return;
    }

    Converter converter = AndroidDomUtil.getSpecificConverter(xmlName, element);
    if (converter == null) {
      if (TOOLS_URI.equals(xmlName.getNamespaceKey())) {
        converter = ToolsAttributeUtil.getConverter(attrDef);
      }
      else {
        converter = AndroidDomUtil.getConverter(attrDef);

        if (converter != null && element.getParentOfType(Manifest.class, true) != null) {
          converter = new ManifestPlaceholderConverter(converter);
        }
      }
    }

    if (converter != null) {
      extension.setConverter(converter, mustBeSoft(converter, attrDef.getFormats()));
    }

    // Check whether attribute is required. If it is, add an annotation to let
    // IntelliJ know about it so it would be, e.g. inserted automatically on
    // tag completion. If attribute is not required, no additional action is needed.
    if (element instanceof LayoutElement && isLayoutAttributeRequired(xmlName, element) ||
        element instanceof ManifestElement && AndroidManifestUtils.isRequiredAttribute(xmlName, element)) {
      extension.addCustomAnnotation(new RequiredImpl());
    }
  }

  private static void registerAttributes(@NotNull AndroidFacet facet,
                                         @NotNull DomElement element,
                                         @NotNull String styleableName,
                                         @Nullable String resPackage,
                                         @NotNull AttributeProcessor callback,
                                         @NotNull Set<XmlName> skipNames) {
    ResourceManager manager = ModuleResourceManagers.getInstance(facet).getResourceManager(resPackage);
    if (manager == null) {
      return;
    }

    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) {
      return;
    }

    String namespace = getNamespaceUriByResourcePackage(facet, resPackage);
    StyleableDefinition styleable = attrDefs.getStyleableByName(styleableName);
    if (styleable != null) {
      registerStyleableAttributes(element, styleable, namespace, callback, skipNames);
    }
    // It's a good idea to add a warning when styleable not found, to make sure that code doesn't
    // try to use attributes that don't exist. However, current AndroidDomExtender code relies on
    // a lot of "heuristics" that fail quite a lot (like adding a bunch of suffixes to short class names)
    // TODO: add a warning when rest of the code of AndroidDomExtender is cleaned up
  }

  private static void registerAttributesForClassAndSuperclasses(@NotNull AndroidFacet facet,
                                                                @NotNull DomElement element,
                                                                @Nullable PsiClass c,
                                                                @NotNull AttributeProcessor callback,
                                                                @NotNull Set<XmlName> skipNames) {
    while (c != null) {
      String styleableName = c.getName();
      if (styleableName != null) {
        registerAttributes(facet, element, styleableName, getResourcePackage(c), callback, skipNames);
      }
      PsiClass additional = getAdditionalAttributesClass(facet, c);
      if (additional != null) {
        String additionalStyleableName = additional.getName();
        if (additionalStyleableName != null) {
          registerAttributes(facet, element, additionalStyleableName, getResourcePackage(additional), callback, skipNames);
        }
      }
      c = getSuperclass(c);
    }
  }

  /**
   * Returns the class that holds attributes used in the specified class c.
   * This is for classes from support libraries without attrs.xml like support lib v4.
   */
  private static @Nullable PsiClass getAdditionalAttributesClass(@NotNull AndroidFacet facet, @NotNull PsiClass c) {
    if (CLASS_NESTED_SCROLL_VIEW.isEquals(StringUtil.notNullize(c.getQualifiedName()))) {
      return findViewValidInXMLByName(facet, SCROLL_VIEW);
    }

    return null;
  }

  @Nullable
  private static String getResourcePackage(@NotNull PsiClass psiClass) {
    // TODO: Replace this with the namespace of the styleableName when that is available.
    String qualifiedName = psiClass.getQualifiedName();
    return qualifiedName != null &&
           qualifiedName.startsWith(ANDROID_PKG_PREFIX) &&
           !qualifiedName.startsWith(ANDROID_SUPPORT_PKG_PREFIX) &&
           !qualifiedName.startsWith(ANDROIDX_PKG_PREFIX) &&
           !qualifiedName.startsWith(ANDROID_ARCH_PKG_PREFIX) ? SYSTEM_RESOURCE_PACKAGE : null;
  }

  @Nullable
  private static PsiClass getSuperclass(@NotNull PsiClass c) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() -> c.isValid() ? c.getSuperClass() : null);
  }

  /**
   * Yield attributes for resources in xml/ folder
   */
  public static void processXmlAttributes(@NotNull AndroidFacet facet,
                                          @NotNull XmlTag tag,
                                          @NotNull XmlResourceElement element,
                                          @NotNull Set<XmlName> skipAttrNames,
                                          @NotNull AttributeProcessor callback) {
    String tagName = tag.getName();
    String styleableName = AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.get(tagName);
    if (styleableName != null) {
      Set<XmlName> newSkipAttrNames = new HashSet<>();
      if (element instanceof Intent) {
        newSkipAttrNames.add(new XmlName("action", ANDROID_URI));
      }

      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, callback, newSkipAttrNames);
    }

    // Handle preferences:
    Map<String, PsiClass> prefClassMap;
    AndroidXmlResourcesUtil.PreferenceSource preferenceSource = AndroidXmlResourcesUtil.PreferenceSource.getPreferencesSource(tag, facet);
    prefClassMap = TagToClassMapper.getInstance(facet.getModule()).getClassMap(preferenceSource.getQualifiedBaseClass());
    PsiClass psiClass = prefClassMap.get(tagName);
    if (psiClass == null) {
      return;
    }

    // Register attributes by preference class:
    registerAttributesForClassAndSuperclasses(facet, element, psiClass, callback, skipAttrNames);

    if (StringUtil.notNullize(psiClass.getQualifiedName()).startsWith("android.preference.")) {
      // Register attributes from the corresponding widget. This was a convention used in framework preferences, but no longer used in
      // AndroidX.
      String widgetClassName = AndroidTextUtils.trimEndOrNullize(tagName, PREFERENCE_TAG_NAME);
      if (widgetClassName != null) {
        PsiClass widgetClass = findViewValidInXMLByName(facet, widgetClassName);
        if (widgetClass != null) {
          registerAttributesForClassAndSuperclasses(facet, element, widgetClass, callback, skipAttrNames);
        }
      }
    }
  }

  /**
   * Returns the expected styleable name for the layout attributes defined by the
   * specified PsiClass of the layout.
   */
  @Nullable
  public static String getLayoutStyleablePrimary(@NotNull PsiClass psiLayoutClass) {
    String viewName = psiLayoutClass.getName();
    if (viewName == null) {
      return null;
    }
    // Not using Map here for lookup by prefix for performance reasons - using switch instead of ImmutableMap makes
    // attribute highlighting 20% faster as measured by AndroidLayoutDomTest#testCustomAttrsPerformance
    switch (viewName) {
      case VIEW_GROUP:
        return "ViewGroup_MarginLayout";
      case TABLE_ROW:
        return "TableRow_Cell";
      default:
        return viewName + "_Layout";
    }
  }

  /**
   * Returns a styleable name that is mistakenly used for the layout attributes defined by the
   * specified PsiClass of the layout.
   */
  @Nullable
  public static String getLayoutStyleableSecondary(@NotNull PsiClass psiLayoutClass) {
    String viewName = psiLayoutClass.getName();
    if (viewName == null) {
      return null;
    }
    return viewName + "_LayoutParams";
  }

  private static void registerAttributesFromSuffixedStyleables(@NotNull AndroidFacet facet,
                                                               @NotNull DomElement element,
                                                               @NotNull PsiClass psiClass,
                                                               @NotNull AttributeProcessor callback,
                                                               @NotNull Set<XmlName> skipAttrNames) {
    String primary = getLayoutStyleablePrimary(psiClass);
    if (primary != null) {
      registerAttributes(facet, element, primary, getResourcePackage(psiClass), callback, skipAttrNames);
    }

    String secondary = getLayoutStyleableSecondary(psiClass);
    if (secondary != null) {
      registerAttributes(facet, element, secondary, null, callback, skipAttrNames);
    }
  }

  private static void registerAttributesFromSuffixedStyleables(
    @NotNull AndroidFacet facet,
    @NotNull DomElement element,
    @NotNull AttributeProcessor callback,
    @NotNull Set<XmlName> skipAttrNames
  ) {
    registerAttributesFromSuffixedStyleablesForNamespace(facet, element, callback, skipAttrNames, ResourceNamespace.ANDROID);
    registerAttributesFromSuffixedStyleablesForNamespace(facet, element, callback, skipAttrNames, ResourceNamespace.RES_AUTO);
  }

  private static void registerAttributesFromSuffixedStyleablesForNamespace(
    @NotNull AndroidFacet facet,
    @NotNull DomElement element,
    @NotNull AttributeProcessor callback,
    @NotNull Set<XmlName> skipAttrNames,
    @NotNull ResourceNamespace resourceNamespace
  ) {
    ResourceRepository repo = StudioResourceRepositoryManager.getInstance(facet).getResourcesForNamespace(resourceNamespace);
    if (repo == null) return;

    //@see AttributeProcessingUtil.getLayoutStyleablePrimary and AttributeProcessingUtil.getLayoutStyleableSecondary
    List<ResourceItem> layoutStyleablesPrimary = repo.getResources(resourceNamespace, ResourceType.STYLEABLE, (item -> {
      String name = item.getName();
      return name.endsWith("_Layout") ||
             name.endsWith("_LayoutParams") ||
             name.equals("ViewGroup_MarginLayout") ||
             name.equals("TableRow_Cell");
    }));
    for (ResourceItem item : layoutStyleablesPrimary) {
      String name = item.getName();
      int indexOfLastUnderscore = name.lastIndexOf('_');
      String viewName = name.substring(0, indexOfLastUnderscore);
      PsiClass psiClass = findViewClassByName(facet, viewName);
      if (psiClass != null) {
        registerAttributes(facet, element, name, getResourcePackage(psiClass), callback, skipAttrNames);
      }
    }
  }

  /**
   * Entry point for XML elements in navigation XMLs.
   */
  public static void processNavAttributes(@NotNull AndroidFacet facet,
                                          @NotNull XmlTag tag,
                                          @NotNull NavDestinationElement element,
                                          @NotNull Set<XmlName> skipAttrNames,
                                          @NotNull AttributeProcessor callback) {
    try {
      NavigationSchema.createIfNecessary(facet.getModule());
    }
    catch (ClassNotFoundException e) {
      // The nav dependency wasn't added yet. Give up.
      return;
    }
    NavigationSchema schema = NavigationSchema.get(facet.getModule());
    for (PsiClass psiClass : schema.getStyleablesForTag(tag.getName())) {
      registerAttributesForClassAndSuperclasses(facet, element, psiClass, callback, skipAttrNames);
    }
  }

  /**
   * Entry point for XML elements in layout XMLs
   */
  public static void processLayoutAttributes(@NotNull AndroidFacet facet,
                                             @NotNull XmlTag tag,
                                             @NotNull LayoutElement element,
                                             @NotNull Set<XmlName> skipAttrNames,
                                             @NotNull AttributeProcessor callback) {

    // Add tools namespace attributes to layout tags, but not those that are databinding-specific ones.
    if (!(element instanceof DataBindingElement)) {
      registerToolsAttribute(ATTR_TARGET_API, callback);
      registerToolsAttribute(ATTR_IGNORE, callback);
      if (tag.getParentTag() == null) {
        registerToolsAttribute(ATTR_CONTEXT, callback);
        registerToolsAttribute(ATTR_MENU, callback);
        registerToolsAttribute(ATTR_ACTION_BAR_NAV_MODE, callback);
        registerToolsAttribute(ATTR_SHOW_IN, callback);
        registerToolsAttribute(ATTR_VIEW_BINDING_IGNORE, callback);
      }

      XmlElementDescriptor descriptor = tag.getDescriptor();
      if (descriptor instanceof LayoutViewElementDescriptor &&
          ((LayoutViewElementDescriptor)descriptor).getClazz() != null) {
        PsiClass viewClass = ((LayoutViewElementDescriptor)descriptor).getClazz();

        registerToolsAttribute(ATTR_VIEW_BINDING_TYPE, callback);

        if (InheritanceUtil.isInheritor(viewClass, FQCN_ADAPTER_VIEW)) {
          registerToolsAttribute(ATTR_LISTITEM, callback);
          registerToolsAttribute(ATTR_LISTHEADER, callback);
          registerToolsAttribute(ATTR_LISTFOOTER, callback);
        }

        if (InheritanceUtil.isInheritor(viewClass, CLASS_DRAWER_LAYOUT.newName()) ||
            InheritanceUtil.isInheritor(viewClass, CLASS_DRAWER_LAYOUT.oldName())) {
          registerToolsAttribute(ATTR_OPEN_DRAWER, callback);
        }

        if (InheritanceUtil.isInheritor(viewClass, RECYCLER_VIEW.newName()) ||
            InheritanceUtil.isInheritor(viewClass, RECYCLER_VIEW.oldName())) {
          registerToolsAttribute(ATTR_ITEM_COUNT, callback);
          registerToolsAttribute(ATTR_LISTITEM, callback);
        }
      }
    }

    if (element instanceof Tag || element instanceof Data) {
      // don't want view attributes inside these tags
      return;
    }

    String tagName = tag.getName();
    switch (tagName) {
      case CLASS_COMPOSE_VIEW:
        registerToolsAttribute(ATTR_COMPOSABLE_NAME, callback);
        break;

      case VIEW_FRAGMENT:
        registerToolsAttribute(ATTR_LAYOUT, callback);
        break;

      case VIEW_TAG:
        // In Android layout XMLs, one can write, e.g.
        //   <view class="LinearLayout" />
        //
        // instead of
        //   <LinearLayout />
        //
        // In this case code adds styleables corresponding to the tag-value of "class" attributes
        //
        // See LayoutInflater#createViewFromTag in Android framework for inflating code

        String name = tag.getAttributeValue("class");
        if (name != null) {
          PsiClass aClass = findViewValidInXMLByName(facet, name);
          if (aClass != null) {
            registerAttributesForClassAndSuperclasses(facet, element, aClass, callback, skipAttrNames);
          }
        }
        break;

      case VIEW_MERGE:
        if (tag.getParentTag() == null) {
          registerToolsAttribute(ATTR_PARENT_TAG, callback);
        }
        registerAttributesForClassAndSuperclasses(facet, element, findViewValidInXMLByName(facet, VIEW_MERGE), callback, skipAttrNames);

        String parentTagName = tag.getAttributeValue(ATTR_PARENT_TAG, TOOLS_URI);
        if (parentTagName != null) {
          registerAttributesForClassAndSuperclasses(facet, element, findViewValidInXMLByName(facet, parentTagName), callback,
                                                    skipAttrNames);
        }
        break;

      default:
        PsiClass c = findViewValidInXMLByName(facet, tagName);
        registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames);
        break;
    }

    if (tagName.equals(VIEW_MERGE)) {
      // A <merge> does not have layout attributes.
      // Instead the children of the merge tag are considered the top elements.
      return;
    }

    XmlTag parentTag = tag.getParentTag();
    PsiClass parentViewClass = null;
    if (parentTag != null) {
      String parentTagName = parentTag.getName();

      if (VIEW_MERGE.equals(parentTagName)) {
        parentTagName = parentTag.getAttributeValue(ATTR_PARENT_TAG, TOOLS_URI);
      }

      if (TAG_LAYOUT.equals(parentTagName)) {
        // Data binding: ensure that the children of the <layout> tag
        // pick up layout params from ViewGroup (layout_width and layout_height)
        parentViewClass = findViewClassByName(facet, CLASS_VIEWGROUP);
      }
      else if (parentTagName != null) {
        parentViewClass = findViewValidInXMLByName(facet, parentTagName);
      }

      if (parentTagName != null) {
        while (parentViewClass != null) {
          registerAttributesFromSuffixedStyleables(facet, element, parentViewClass, callback, skipAttrNames);
          parentViewClass = getSuperclass(parentViewClass);
        }
        return;
      }
    }

    // We don't know what the parent is: include all layout attributes from all layout classes.
    registerAttributesFromSuffixedStyleables(facet, element, callback, skipAttrNames);
  }

  /**
   * Enumerate attributes that are available for the given XML tag, represented by {@link AndroidDomElement},
   * and "return" them via {@link AttributeProcessor}.
   *
   * Primary user is {@link AndroidDomExtender}, which uses it to provide code completion facilities when
   * editing XML files in text editor.
   *
   * Implementation of the method implements {@link Styleable} annotation handling and dispatches on tag type
   * using instanceof checks for adding attributes that don't come from styleable definitions with statically
   * known names.
   *
   * @param processAllExistingAttrsFirst whether already existing attributes should be returned first
   */
  public static void processAttributes(@NotNull AndroidDomElement element,
                                       @NotNull AndroidFacet facet,
                                       boolean processAllExistingAttrsFirst,
                                       @NotNull AttributeProcessor callback) {
    if (DumbService.getInstance(facet.getModule().getProject()).isDumb()) {
      return;
    }
    XmlTag tag = element.getXmlTag();
    assert tag != null;

    Set<XmlName> skippedAttributes =
      processAllExistingAttrsFirst ? registerExistingAttributes(facet, tag, element, callback) : new HashSet<>();

    XmlElementDescriptor descriptor = tag.getDescriptor();
    if (descriptor instanceof TagFromClassDescriptor && ((TagFromClassDescriptor)descriptor).getClazz() == null) {
      // Don't register attributes for unresolved classes.
      return;
    }

    if (element instanceof ManifestElement) {
      processManifestAttributes(tag, element, callback);
    }
    else if (element instanceof LayoutElement) {
      processLayoutAttributes(facet, tag, (LayoutElement)element, skippedAttributes, callback);
    }
    else if (element instanceof XmlResourceElement) {
      processXmlAttributes(facet, tag, (XmlResourceElement)element, skippedAttributes, callback);
    }
    else if (element instanceof XmlRawResourceElement) {
      processRawAttributes(tag, callback);
    }
    else if (element instanceof NavDestinationElement) {
      processNavAttributes(facet, tag, (NavDestinationElement)element, skippedAttributes, callback);
    }

    // If DOM element is annotated with @Styleable annotation, load a styleable definition
    // from Android framework or a library with the name provided in annotation and register all attributes
    // from it for code highlighting and completion.
    Styleable styleableAnnotation = element.getAnnotation(Styleable.class);
    if (styleableAnnotation == null) {
      return;
    }
    boolean isSystem = styleableAnnotation.packageName().equals(ANDROID_PKG);
    AttributeDefinitions definitions;
    if (isSystem) {
      FrameworkResourceManager manager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager();
      if (manager == null) {
        return;
      }

      definitions = manager.getAttributeDefinitions();
      if (definitions == null) {
        return;
      }
    }
    else {
      definitions = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getAttributeDefinitions();
    }

    if (element instanceof MenuItem) {
      processMenuItemAttributes(facet, element, skippedAttributes, callback);
      return;
    }

    for (String styleableName : styleableAnnotation.value()) {
      StyleableDefinition styleable = definitions.getStyleableByName(styleableName);
      if (styleable != null) {
        // TODO(namespaces): if !isSystem and we're namespace-aware we should use the library-specific namespace
        registerStyleableAttributes(element, styleable, isSystem ? ANDROID_URI : AUTO_URI, callback, skippedAttributes);
      }
      else if (isSystem) {
        // DOM element is annotated with @Styleable annotation, but styleable definition with
        // provided name is not there in Android framework. This is a bug, so logging it as a warning.
        getLog().warn(String.format("@Styleable(%s) annotation doesn't point to existing styleable", styleableName));
      }
    }

    // Handle interpolator XML tags: they don't have their own DomElement interfaces, and all use InterpolatorElement at the moment.
    // Thus, they can't use @Styleable annotation and there is a mapping from tag name to styleable name that's used below.

    // This snippet doesn't look much different from lines above for handling @Styleable annotations above,
    // but is used to provide customized warning message
    // TODO: figure it out how to make it DRY without introducing new method with lots of arguments
    if (element instanceof InterpolatorElement) {
      String styleableName = InterpolatorDomFileDescription.getInterpolatorStyleableByTagName(tag.getName());
      if (styleableName != null) {
        StyleableDefinition styleable = definitions.getStyleableByName(styleableName);
        if (styleable == null) {
          getLog().warn(String.format("%s doesn't point to existing styleable for interpolator", styleableName));
        }
        else {
          registerStyleableAttributes(element, styleable, ANDROID_URI, callback, skippedAttributes);
        }
      }
    }
  }

  /**
   * Handle attributes for XML elements in raw/ resource folder
   */
  public static void processRawAttributes(@NotNull XmlTag tag, @NotNull AttributeProcessor callback) {
    // For Resource Shrinking
    if (TAG_RESOURCES.equals(tag.getName())) {
      registerToolsAttribute(ATTR_SHRINK_MODE, callback);
      registerToolsAttribute(ATTR_KEEP, callback);
      registerToolsAttribute(ATTR_DISCARD, callback);
    }
  }

  /**
   * Handle attributes for XML elements from AndroidManifest.xml
   */
  public static void processManifestAttributes(@NotNull XmlTag tag,
                                               @NotNull AndroidDomElement element,
                                               @NotNull AttributeProcessor callback) {
    // Don't register manifest merger attributes for root element
    if (tag.getParentTag() != null) {
      registerToolsAttribute(ToolsAttributeUtil.ATTR_NODE, callback);
      registerToolsAttribute(ToolsAttributeUtil.ATTR_STRICT, callback);
      registerToolsAttribute(ToolsAttributeUtil.ATTR_REMOVE, callback);
      registerToolsAttribute(ToolsAttributeUtil.ATTR_REPLACE, callback);
    }

    if (element instanceof UsesSdk) {
      registerToolsAttribute(ToolsAttributeUtil.ATTR_OVERRIDE_LIBRARY, callback);
    }
  }

  private static void processMenuItemAttributes(@NotNull AndroidFacet facet,
                                                @NotNull DomElement element,
                                                @NotNull Collection<XmlName> skippedAttributes,
                                                @NotNull AttributeProcessor callback) {
    ResourceManager manager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager();
    if (manager == null) {
      return;
    }

    AttributeDefinitions styleables = manager.getAttributeDefinitions();
    if (styleables == null) {
      return;
    }

    StyleableDefinition styleable = styleables.getStyleableByName("MenuItem");
    if (styleable == null) {
      getLog().warn("No StyleableDefinition for MenuItem");
      return;
    }

    for (AttributeDefinition attribute : styleable.getAttributes()) {
      String name = attribute.getName();

      // android:showAsAction was introduced in API Level 11. Use the app: one if the project depends on appcompat.
      // See com.android.tools.lint.checks.AppCompatResourceDetector.
      if (name.equals(ATTR_SHOW_AS_ACTION)) {
        boolean hasAppCompat = DependencyManagementUtil.dependsOn(facet.getModule(), GoogleMavenArtifactId.APP_COMPAT_V7) ||
                               DependencyManagementUtil.dependsOn(facet.getModule(), GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7);
        if (hasAppCompat) {
          // TODO(namespaces): Replace AUTO_URI with the URI of the correct namespace.
          XmlName xmlName = new XmlName(name, AUTO_URI);
          if (skippedAttributes.add(xmlName)) {
            registerAttribute(attribute, xmlName, "MenuItem", element, callback);
          }

          continue;
        }
      }

      XmlName xmlName = new XmlName(name, ANDROID_URI);
      if (skippedAttributes.add(xmlName)) {
        registerAttribute(attribute, xmlName, "MenuItem", element, callback);
      }
    }
  }

  private static void registerToolsAttribute(@NotNull String attributeName, @NotNull AttributeProcessor callback) {
    AttributeDefinition definition = ToolsAttributeUtil.getAttrDefByName(attributeName);
    if (definition != null) {
      XmlName name = new XmlName(attributeName, TOOLS_URI);
      DomExtension domExtension = callback.processAttribute(name, definition, null);
      Converter converter = ToolsAttributeUtil.getConverter(definition);
      if (domExtension != null && converter != null) {
        domExtension.setConverter(converter);
      }
    }
    else {
      getLog().warn("No attribute definition for tools attribute " + attributeName);
    }
  }

  @NotNull
  private static Set<XmlName> registerExistingAttributes(@NotNull AndroidFacet facet,
                                                         @NotNull XmlTag tag,
                                                         @NotNull AndroidDomElement element,
                                                         @NotNull AttributeProcessor callback) {
    Set<XmlName> result = new HashSet<>();
    XmlAttribute[] attrs = tag.getAttributes();

    for (XmlAttribute attr : attrs) {
      String localName = attr.getLocalName();

      if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
        if (!"xmlns".equals(attr.getNamespacePrefix())) {
          AttributeDefinition attrDef = AndroidDomUtil.getAttributeDefinition(facet, attr);

          if (attrDef != null) {
            XmlName xmlName = getXmlName(attrDef, attr.getNamespace());
            result.add(xmlName);
            String namespaceUri = attr.getNamespace();
            registerAttribute(attrDef, xmlName, null, element, callback);
          }
        }
      }
    }
    return result;
  }

  private static XmlName getXmlName(@NotNull AttributeDefinition attrDef, @Nullable String namespaceUri) {
    ResourceReference attrReference = attrDef.getResourceReference();
    String attrNamespaceUri = attrReference.getNamespace().getXmlNamespaceUri();
    return new XmlName(attrReference.getName(), TOOLS_URI.equals(namespaceUri) ? TOOLS_URI : attrNamespaceUri);
  }

  public interface AttributeProcessor {
    @Nullable
    DomExtension processAttribute(@NotNull XmlName xmlName, @NotNull AttributeDefinition attrDef, @Nullable String parentStyleableName);
  }
}
