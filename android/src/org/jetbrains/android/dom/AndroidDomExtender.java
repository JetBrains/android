/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.android.dom.animation.InterpolatorElement;
import org.jetbrains.android.dom.animation.fileDescriptions.InterpolatorDomFileDescription;
import org.jetbrains.android.dom.attrs.*;
import org.jetbrains.android.dom.converters.CompositeConverter;
import org.jetbrains.android.dom.converters.ManifestPlaceholderConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.layout.*;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElement;
import org.jetbrains.android.dom.manifest.UsesSdk;
import org.jetbrains.android.dom.raw.XmlRawResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.Intent;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.LayoutViewClassUtils;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

public class AndroidDomExtender extends DomExtender<AndroidDomElement> {
  private static final Logger LOG = Logger.getInstance(AndroidDomExtender.class);
  private static final String[] LAYOUT_ATTRIBUTES_SUFS = new String[]{"_Layout", "_MarginLayout", "_Cell"};

  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_TAG_NAMES =
    ImmutableSet.of(VIEW_MERGE, TABLE_ROW, VIEW_INCLUDE, REQUEST_FOCUS, TAG_LAYOUT, TAG_DATA, TAG_IMPORT, TAG);

  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_PARENT_TAG_NAMES =
    ImmutableSet
      .of(TABLE_ROW, TABLE_LAYOUT, VIEW_MERGE, GRID_LAYOUT, FQCN_GRID_LAYOUT_V7, CLASS_PERCENT_RELATIVE_LAYOUT, CLASS_PERCENT_FRAME_LAYOUT);

  public static boolean isLayoutAttributeRequired(@NotNull XmlName attributeName, @NotNull DomElement element) {
    // Mark layout_width and layout_height required - if the context calls for it
    String localName = attributeName.getLocalName();
    if (!(ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName))) {
      return false;
    }
    if ((element instanceof LayoutViewElement || element instanceof Fragment) && NS_RESOURCES.equals(attributeName.getNamespaceKey())) {
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

  @Override
  public boolean supportsStubs() {
    return false;
  }

  @Nullable
  public static String getNamespaceKeyByResourcePackage(@NotNull AndroidFacet facet, @Nullable String resPackage) {
    if (resPackage == null) {
      if (facet.getProperties().LIBRARY_PROJECT || facet.requiresAndroidModel()) {
        return AUTO_URI;
      }
      Manifest manifest = facet.getManifest();
      if (manifest != null) {
        String aPackage = manifest.getPackage().getValue();
        if (aPackage != null && aPackage.length() > 0) {
          return URI_PREFIX + aPackage;
        }
      }
    }
    else if (resPackage.equals(SYSTEM_RESOURCE_PACKAGE)) {
      return ANDROID_URI;
    }
    return null;
  }

  private static void registerStyleableAttributes(DomElement element,
                                                  @NotNull StyleableDefinition styleable,
                                                  @Nullable String namespace,
                                                  MyCallback callback,
                                                  Set<XmlName> skippedAttributes) {
    for (AttributeDefinition attrDef : styleable.getAttributes()) {
      String attrName = attrDef.getName();
      final XmlName xmlName = new XmlName(attrName, namespace);
      if (!skippedAttributes.contains(xmlName)) {
        skippedAttributes.add(xmlName);
        registerAttribute(attrDef, styleable.getName(), namespace, callback, element);
      }
    }
  }

  private static boolean mustBeSoft(@NotNull Converter converter, Collection<AttributeFormat> formats) {
    if (converter instanceof CompositeConverter || converter instanceof ResourceReferenceConverter) {
      return false;
    }
    return formats.size() > 1;
  }

  private static void registerAttribute(@NotNull AttributeDefinition attrDef,
                                        @Nullable String parentStyleableName,
                                        String namespaceKey,
                                        MyCallback callback,
                                        @NotNull DomElement element) {
    String name = attrDef.getName();
    if (!NS_RESOURCES.equals(namespaceKey) && name.startsWith(PREFIX_ANDROID)) {
      // A styleable-definition in the app namespace (user specified or from a library) can include
      // a reference to a platform attribute. In such a case, register it under the android namespace
      // as opposed to the app namespace. See https://code.google.com/p/android/issues/detail?id=171162
      name = name.substring(PREFIX_ANDROID.length());
      namespaceKey = NS_RESOURCES;
    }
    XmlName xmlName = new XmlName(name, namespaceKey);
    final DomExtension extension = callback.processAttribute(xmlName, attrDef, parentStyleableName);

    if (extension == null) {
      return;
    }
    Converter converter = AndroidDomUtil.getSpecificConverter(xmlName, element);
    if (converter == null) {
      if (TOOLS_URI.equals(namespaceKey)) {
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
      extension.addCustomAnnotation(new MyRequired());
    }
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String[] styleableNames,
                                           MyCallback callback,
                                           Set<XmlName> skipNames) {
    registerAttributes(facet, element, styleableNames, null, callback, skipNames);
    registerAttributes(facet, element, styleableNames, SYSTEM_RESOURCE_PACKAGE, callback, skipNames);
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String[] styleableNames,
                                           @Nullable String resPackage,
                                           MyCallback callback,
                                           Set<XmlName> skipNames) {
    ResourceManager manager = facet.getResourceManager(resPackage);
    if (manager == null) {
      return;
    }

    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) {
      return;
    }

    String namespace = getNamespaceKeyByResourcePackage(facet, resPackage);
    for (String name : styleableNames) {
      StyleableDefinition styleable = attrDefs.getStyleableByName(name);
      if (styleable != null) {
        registerStyleableAttributes(element, styleable, namespace, callback, skipNames);
      }
      // It's a good idea to add a warning when styleable not found, to make sure that code doesn't
      // try to use attributes that don't exist. However, current AndroidDomExtender code relies on
      // a lot of "heuristics" that fail quite a lot (like adding a bunch of suffixes to short class names)
      // TODO: add a warning when rest of the code of AndroidDomExtender is cleaned up
    }
  }

  @NotNull
  private static Class getValueClass(@Nullable AttributeFormat format) {
    if (format == null) return String.class;
    switch (format) {
      case Boolean:
        return boolean.class;
      case Reference:
      case Dimension:
      case Color:
        return ResourceValue.class;
      default:
        return String.class;
    }
  }

  protected static void registerAttributesForClassAndSuperclasses(AndroidFacet facet,
                                                                  DomElement element,
                                                                  PsiClass c,
                                                                  MyCallback callback,
                                                                  Set<XmlName> skipNames) {
    while (c != null) {
      String styleableName = c.getName();
      if (styleableName != null) {
        registerAttributes(facet, element, new String[]{styleableName}, callback, skipNames);
      }
      c = getSuperclass(c);
    }
  }

  @Nullable
  protected static PsiClass getSuperclass(@NotNull final PsiClass c) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      @Nullable
      public PsiClass compute() {
        return c.isValid() ? c.getSuperClass() : null;
      }
    });
  }

  private static boolean isPreference(@NotNull Map<String, PsiClass> preferenceClassMap, @NotNull PsiClass c) {
    PsiClass preferenceClass = preferenceClassMap.get("Preference");
    return preferenceClass != null && (preferenceClass == c || c.isInheritor(preferenceClass, true));
  }

  public static void registerExtensionsForXmlResources(AndroidFacet facet,
                                                       XmlTag tag,
                                                       XmlResourceElement element,
                                                       MyCallback callback,
                                                       Set<String> registeredSubtags,
                                                       Set<XmlName> skipAttrNames) {
    final String tagName = tag.getName();
    String styleableName = AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.get(tagName);
    if (styleableName != null) {
      final Set<XmlName> newSkipAttrNames = new HashSet<XmlName>();
      if (element instanceof Intent) {
        newSkipAttrNames.add(new XmlName("action", NS_RESOURCES));
      }

      registerAttributes(facet, element, new String[]{styleableName}, SYSTEM_RESOURCE_PACKAGE, callback, newSkipAttrNames);
    }

    if (tagName.equals("searchable")) {
      registerSubtags("actionkey", XmlResourceElement.class, callback, registeredSubtags);
    }

    // for keyboard api
    if (tagName.equals("Keyboard")) {
      registerSubtags("Row", XmlResourceElement.class, callback, registeredSubtags);
    }
    else if (tagName.equals("Row")) {
      registerSubtags("Key", XmlResourceElement.class, callback, registeredSubtags);
    }

    // for device-admin api
    if (tagName.equals("device-admin")) {
      registerSubtags("uses-policies", XmlResourceElement.class, callback, registeredSubtags);
    }
    else if (tagName.equals("uses-policies")) {
      registerSubtags("limit-password", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("watch-login", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("reset-password", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("force-lock", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("wipe-data", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("set-global-proxy", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("expire-password", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("encrypted-storage", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("disable-camera", XmlResourceElement.class, callback, registeredSubtags);
      registerSubtags("disable-keyguard-features", XmlResourceElement.class, callback, registeredSubtags);
    }

    // DevicePolicyManager API
    if (tagName.equals("preference-headers")) {
      registerSubtags("header", PreferenceElement.class, callback, registeredSubtags);
    }

    // for preferences
    Map<String, PsiClass> prefClassMap = getPreferencesClassMap(facet);
    String prefClassName = element.getXmlTag().getName();
    PsiClass c = prefClassMap.get(prefClassName);

    // register attributes by preference class
    registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames);

    //register attributes by widget
    String suffix = "Preference";
    if (prefClassName.endsWith(suffix)) {
      String widgetClassName = prefClassName.substring(0, prefClassName.length() - suffix.length());
      PsiClass widgetClass = LayoutViewClassUtils.findClassByTagName(facet, widgetClassName, AndroidUtils.VIEW_CLASS_NAME);
      registerAttributesForClassAndSuperclasses(facet, element, widgetClass, callback, skipAttrNames);
    }

    if (c != null && isPreference(prefClassMap, c)) {
      registerClassNameSubtags(tag, prefClassMap, PreferenceElement.class, registeredSubtags, callback);
    }
  }

  @NotNull
  public static Map<String, PsiClass> getPreferencesClassMap(@NotNull AndroidFacet facet) {
    return facet.getClassMap(CLASS_PREFERENCE);
  }

  public static Map<String, PsiClass> getViewClassMap(@NotNull AndroidFacet facet) {
    if (DumbService.isDumb(facet.getModule().getProject())) {
      return Collections.emptyMap();
    }
    return facet.getClassMap(AndroidUtils.VIEW_CLASS_NAME);
  }

  private static String[] getClassNames(@NotNull Collection<PsiClass> classes) {
    List<String> names = new ArrayList<String>();
    for (PsiClass aClass : classes) {
      names.add(aClass.getName());
    }
    return ArrayUtil.toStringArray(names);
  }

  private static void registerLayoutAttributes(AndroidFacet facet,
                                               DomElement element,
                                               XmlTag tag,
                                               MyCallback callback,
                                               Set<XmlName> skipAttrNames) {
    XmlTag parentTag = tag.getParentTag();
    Map<String, PsiClass> map = getViewClassMap(facet);
    if (parentTag != null) {
      final String parentTagName = parentTag.getName();

      if (!VIEW_MERGE.equals(parentTagName)) {
        PsiClass c = map.get(parentTagName);
        while (c != null) {
          registerLayoutAttributes(facet, element, c, callback, skipAttrNames);
          c = getSuperclass(c);
        }
        return;
      }
    }
    for (PsiClass c : map.values()) {
      registerLayoutAttributes(facet, element, c, callback, skipAttrNames);
    }
  }

  private static void registerLayoutAttributes(AndroidFacet facet,
                                               DomElement element,
                                               PsiClass c,
                                               MyCallback callback,
                                               Set<XmlName> skipAttrNames) {
    String styleableName = c.getName();
    if (styleableName != null) {
      String[] styleableNames = new String[LAYOUT_ATTRIBUTES_SUFS.length];
      for (int i = 0; i < LAYOUT_ATTRIBUTES_SUFS.length; i++) {
        styleableNames[i] = styleableName + LAYOUT_ATTRIBUTES_SUFS[i];
      }
      registerAttributes(facet, element, styleableNames, callback, skipAttrNames);
    }
  }

  public static void registerExtensionsForLayout(AndroidFacet facet,
                                                 XmlTag tag,
                                                 LayoutElement element,
                                                 MyCallback callback,
                                                 Set<String> registeredSubtags,
                                                 Set<XmlName> skipAttrNames) {
    Map<String, PsiClass> map = getViewClassMap(facet);
    if (element instanceof Include) {
      for (String className : map.keySet()) {
        PsiClass c = map.get(className);
        registerLayoutAttributes(facet, element, c, callback, skipAttrNames);
      }
      for (PsiClass c : map.values()) {
        registerLayoutAttributes(facet, element, c, callback, skipAttrNames);
      }
      return;
    }
    else if (element instanceof Fragment) {
      registerAttributes(facet, element, new String[]{"Fragment"}, callback, skipAttrNames);
    }
    else if (element instanceof Tag) {
      registerAttributes(facet, element, new String[]{"ViewTag"}, SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
      return;
    }
    else if (element instanceof Data) {
      return;  // don't want view tags inside data.
    }
    else {
      String tagName = tag.getName();
      if (!tagName.equals("view")) {
        PsiClass c = map.get(tagName);
        registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames);
      }
      else {
        String[] styleableNames = getClassNames(map.values());
        registerAttributes(facet, element, styleableNames, callback, skipAttrNames);
      }
    }
    registerLayoutAttributes(facet, element, tag, callback, skipAttrNames);
    registerClassNameSubtags(tag, map, LayoutViewElement.class, registeredSubtags, callback);
  }

  private static void registerClassNameSubtags(XmlTag tag,
                                               Map<String, PsiClass> classMap,
                                               Type type,
                                               Set<String> registeredSubtags,
                                               MyCallback callback) {
    final Set<String> allAllowedTags = new HashSet<String>();
    final Map<String, String> class2Name = new HashMap<String, String>();

    for (String tagName : classMap.keySet()) {
      final PsiClass aClass = classMap.get(tagName);
      if (!AndroidUtils.isAbstract(aClass)) {
        allAllowedTags.add(tagName);
        final String qName = aClass.getQualifiedName();
        final String prevTagName = class2Name.get(qName);

        if (prevTagName == null || tagName.indexOf('.') == -1) {
          class2Name.put(qName, tagName);
        }
      }
    }
    registerSubtags(callback, tag, allAllowedTags, class2Name.values(), registeredSubtags, type);
  }

  private static void registerSubtags(MyCallback callback,
                                      XmlTag tag,
                                      final Set<String> allowedTags,
                                      Collection<String> tagsToComplete,
                                      Set<String> registeredSubtags,
                                      Type type) {
    for (String tagName : tagsToComplete) {
      registerSubtags(tagName, type, callback, registeredSubtags);
    }
    registerExistingSubtags(tag, callback, new Processor<String>() {
      @Override
      public boolean process(String s) {
        return allowedTags.contains(s);
      }
    }, type);
  }

  public static void registerExtensionsForManifest(AndroidFacet facet,
                                                   String tagName,
                                                   ManifestElement element,
                                                   MyCallback callback,
                                                   Set<String> registeredSubtags,
                                                   Set<XmlName> skippedAttributes,
                                                   boolean processStaticallyDefinedElements) {
    String styleableName = AndroidManifestUtils.getStyleableNameForElement(element);

    if (styleableName == null) {
      styleableName = AndroidManifestUtils.getStyleableNameByTagName(tagName);
    }
    final Set<XmlName> newSkippedNames = new HashSet<XmlName>(skippedAttributes);

    if (!processStaticallyDefinedElements) {
      for (String attrName : AndroidManifestUtils.getStaticallyDefinedAttrs(element)) {
        newSkippedNames.add(new XmlName(attrName, NS_RESOURCES));
      }
    }
    SystemResourceManager manager = facet.getSystemResourceManager();
    if (manager == null) return;
    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) return;
    StyleableDefinition styleable = attrDefs.getStyleableByName(styleableName);
    if (styleable == null) return;

    registerStyleableAttributes(element, styleable, NS_RESOURCES, callback, newSkippedNames);

    Set<String> skippedTagNames;
    if (!processStaticallyDefinedElements) {
      skippedTagNames = new HashSet<String>();
      Collections.addAll(skippedTagNames, AndroidManifestUtils.getStaticallyDefinedSubtags(element));
    }
    else {
      skippedTagNames = Collections.emptySet();
    }
    for (StyleableDefinition child : styleable.getChildren()) {
      String childTagName = AndroidManifestUtils.getTagNameByStyleableName(child.getName());
      if (childTagName != null && !skippedTagNames.contains(childTagName)) {
        registerSubtags(childTagName, ManifestElement.class, callback, registeredSubtags);
      }
    }
  }

  public static void processAttrsAndSubtags(@NotNull AndroidDomElement element,
                                            @NotNull MyCallback callback,
                                            @NotNull AndroidFacet facet,
                                            boolean processAllExistingAttrsFirst,
                                            boolean processStaticallyDefinedElements) {
    XmlTag tag = element.getXmlTag();

    final Set<XmlName> skippedAttributes =
      processAllExistingAttrsFirst ? registerExistingAttributes(facet, tag, callback, element) : new HashSet<XmlName>();
    String tagName = tag.getName();
    Set<String> registeredSubtags = new HashSet<String>();
    if (element instanceof ManifestElement) {
      registerExtensionsForManifest(facet, tagName, (ManifestElement)element, callback, registeredSubtags, skippedAttributes,
                                    processStaticallyDefinedElements);
      if (tag.getParentTag() != null) {
        // Don't register attributes for root element
        registerToolsAttribute(ToolsAttributeUtil.ATTR_NODE, callback);
        registerToolsAttribute(ToolsAttributeUtil.ATTR_STRICT, callback);
        registerToolsAttribute(ToolsAttributeUtil.ATTR_REMOVE, callback);
        registerToolsAttribute(ToolsAttributeUtil.ATTR_REPLACE, callback);
      }
      if (element instanceof UsesSdk) {
        registerToolsAttribute(ToolsAttributeUtil.ATTR_OVERRIDE_LIBRARY, callback);
      }
    }
    else if (element instanceof LayoutElement) {
      registerExtensionsForLayout(facet, tag, (LayoutElement)element, callback, registeredSubtags, skippedAttributes);

      // Add tools namespace attributes to layout tags, but not those that are databinding-specific ones
      if (!(element instanceof DataBindingElement)) {
        registerToolsAttribute(ATTR_TARGET_API, callback);
        if (tag.getParentTag() == null) {
          registerToolsAttribute(ATTR_CONTEXT, callback);
          registerToolsAttribute(ATTR_MENU, callback);
          registerToolsAttribute(ATTR_ACTION_BAR_NAV_MODE, callback);
          registerToolsAttribute(ATTR_SHOW_IN, callback);
        }

        final Map<String, PsiClass> classMap = getViewClassMap(facet);

        // AdapterView resides in android.widget package and thus is acquired from class map by short name
        final PsiClass adapterView = classMap.get(ADAPTER_VIEW);
        final PsiClass psiClass = classMap.get(tag.getName());
        if (adapterView != null && psiClass != null && psiClass.isInheritor(adapterView, true)) {
          registerToolsAttribute(ATTR_LISTITEM, callback);
          registerToolsAttribute(ATTR_LISTHEADER, callback);
          registerToolsAttribute(ATTR_LISTFOOTER, callback);
        }

        final PsiClass drawerLayout = classMap.get(CLASS_DRAWER_LAYOUT);
        if (drawerLayout != null && psiClass != null &&
            (psiClass.isEquivalentTo(drawerLayout) || psiClass.isInheritor(drawerLayout, true))) {
          registerToolsAttribute(ATTR_OPEN_DRAWER, callback);
        }
      }
    }
    else if (element instanceof XmlResourceElement) {
      registerExtensionsForXmlResources(facet, tag, (XmlResourceElement)element, callback, registeredSubtags, skippedAttributes);
    }
    // For Resource Shrinking
    else if (element instanceof XmlRawResourceElement) {
      if (TAG_RESOURCES.equals(tagName)) {
        registerToolsAttribute(ATTR_SHRINK_MODE, callback);
        registerToolsAttribute(ATTR_KEEP, callback);
        registerToolsAttribute(ATTR_DISCARD, callback);
      }
    }

    // If DOM element is annotated with @Styleable annotation, load a styleable definition
    // from Android framework with the name provided in annotation and register all attributes
    // from it for code highlighting and completion.
    final Styleable styleableAnnotation = element.getAnnotation(Styleable.class);
    if (styleableAnnotation == null) {
      return;
    }

    final SystemResourceManager manager = facet.getSystemResourceManager();
    if (manager == null) {
      return;
    }

    final AttributeDefinitions definitions = manager.getAttributeDefinitions();
    if (definitions == null) {
      return;
    }

    for (String styleableName : styleableAnnotation.value()) {
      final StyleableDefinition styleable = definitions.getStyleableByName(styleableName);
      if (styleable == null) {
        // DOM element is annotated with @Styleable annotation, but styleable definition with
        // provided name is not there in Android framework. This is a bug, so logging it as a warning.
        LOG.warn(String.format("@Styleable(%s) annotation doesn't point to existing styleable", styleableName));
      }
      else {
        registerStyleableAttributes(element, styleable, ANDROID_URI, callback, skippedAttributes);
      }
    }

    // This snippet doesn't look much different from lines above for handling @Styleable annotations above,
    // but is used to provide customized warning message
    // TODO: figure it out how to make it DRY without introducing new method with lots of arguments
    if (element instanceof InterpolatorElement) {
      final String styleableName = InterpolatorDomFileDescription.getInterpolatorStyleableByTagName(tag.getName());
      if (styleableName != null) {
        final StyleableDefinition styleable = definitions.getStyleableByName(styleableName);
        if (styleable == null) {
          LOG.warn(String.format("%s doesn't point to existing styleable for interpolator", styleableName));
        }
        else {
          registerStyleableAttributes(element, styleable, ANDROID_URI, callback, skippedAttributes);
        }
      }
    }
  }

  private static void registerToolsAttribute(@NotNull String attributeName, @NotNull MyCallback callback) {
    final AttributeDefinition definition = ToolsAttributeUtil.getAttrDefByName(attributeName);
    if (definition != null) {
      final XmlName name = new XmlName(attributeName, TOOLS_URI);
      final DomExtension domExtension = callback.processAttribute(name, definition, null);
      final ResolvingConverter converter = ToolsAttributeUtil.getConverter(definition);
      if (domExtension != null && converter != null) {
        domExtension.setConverter(converter);
      }
    }
    else {
      LOG.warn("No attribute definition for tools attribute " + attributeName);
    }
  }

  private static void registerSubtags(@NotNull String name, Type type, MyCallback callback, Set<String> registeredTags) {
    callback.processSubtag(new XmlName(name), type);
    registeredTags.add(name);
  }

  private static void registerExistingSubtags(@NotNull XmlTag tag,
                                              @NotNull MyCallback callback,
                                              @Nullable Processor<String> filter,
                                              @NotNull Type type) {
    XmlTag[] subtags = tag.getSubTags();
    for (XmlTag subtag : subtags) {
      String localName = subtag.getLocalName();
      if (filter == null || filter.process(localName)) {
        if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
          callback.processSubtag(new XmlName(localName), type);
        }
      }
    }
  }

  @NotNull
  private static Set<XmlName> registerExistingAttributes(AndroidFacet facet, XmlTag tag, MyCallback callback, AndroidDomElement element) {
    final Set<XmlName> result = new HashSet<XmlName>();
    XmlAttribute[] attrs = tag.getAttributes();

    for (XmlAttribute attr : attrs) {
      String localName = attr.getLocalName();

      if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
        if (!"xmlns".equals(attr.getNamespacePrefix())) {
          AttributeDefinition attrDef = AndroidDomUtil.getAttributeDefinition(facet, attr);

          if (attrDef != null) {
            String namespace = attr.getNamespace();
            result.add(new XmlName(attr.getLocalName(), attr.getNamespace()));
            registerAttribute(attrDef, null, namespace.length() > 0 ? namespace : null, callback, element);
          }
        }
      }
    }
    return result;
  }

  @Override
  public void registerExtensions(@NotNull AndroidDomElement element, @NotNull final DomExtensionsRegistrar registrar) {
    final AndroidFacet facet = AndroidFacet.getInstance(element);

    if (facet == null) {
      return;
    }
    processAttrsAndSubtags(element, new MyCallback() {
      @Nullable
      @Override
      public DomExtension processAttribute(@NotNull XmlName xmlName,
                                           @NotNull AttributeDefinition attrDef,
                                           @Nullable String parentStyleableName) {
        Set<AttributeFormat> formats = attrDef.getFormats();
        Class valueClass = formats.size() == 1 ? getValueClass(formats.iterator().next()) : String.class;
        registrar.registerAttributeChildExtension(xmlName, GenericAttributeValue.class);
        return registrar.registerGenericAttributeValueChildExtension(xmlName, valueClass);
      }

      @Override
      public void processSubtag(@NotNull XmlName xmlName, @NotNull Type type) {
        registrar.registerCollectionChildrenExtension(xmlName, type);
      }
    }, facet, true, false);
  }

  @SuppressWarnings("ClassExplicitlyAnnotation")
  private static class MyRequired implements Required {
    @Override
    public boolean value() {
      return true;
    }

    @Override
    public boolean nonEmpty() {
      return true;
    }

    @Override
    public boolean identifier() {
      return false;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return Required.class;
    }
  }

  public static abstract class MyCallback {
    @Nullable
    public abstract DomExtension processAttribute(@NotNull XmlName xmlName,
                                                  @NotNull AttributeDefinition attrDef,
                                                  @Nullable String parentStyleableName);

    void processSubtag(@NotNull XmlName xmlName, @NotNull Type type) {
    }
  }
}
