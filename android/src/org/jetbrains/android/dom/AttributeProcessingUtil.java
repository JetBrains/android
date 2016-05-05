/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.AndroidTextUtils;
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
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtension;
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
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.Intent;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.LayoutViewClassUtils;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * Utility functions for enumerating available children attribute types in the context of a given XML tag.
 *
 * Entry point is {@link #processAttributes(AndroidDomElement, AndroidFacet, boolean, AttributeProcessor)},
 * look for a Javadoc there.
 */
public class AttributeProcessingUtil {

  private static final String PREFERENCE_TAG_NAME = "Preference";
  private static final String[] LAYOUT_ATTRIBUTES_SUFS = new String[]{"_Layout", "_MarginLayout", "_Cell"};
  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_TAG_NAMES =
    ImmutableSet.of(VIEW_MERGE, TABLE_ROW, VIEW_INCLUDE, REQUEST_FOCUS, TAG_LAYOUT, TAG_DATA, TAG_IMPORT, TAG);
  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_PARENT_TAG_NAMES =
    ImmutableSet
      .of(TABLE_ROW, TABLE_LAYOUT, VIEW_MERGE, GRID_LAYOUT, FQCN_GRID_LAYOUT_V7, CLASS_PERCENT_RELATIVE_LAYOUT, CLASS_PERCENT_FRAME_LAYOUT);

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

  private static void registerStyleableAttributes(@NotNull DomElement element,
                                                  @NotNull StyleableDefinition styleable,
                                                  @Nullable String namespace,
                                                  @NotNull AttributeProcessor callback,
                                                  @NotNull Set<XmlName> skippedAttributes) {
    for (AttributeDefinition attrDef : styleable.getAttributes()) {
      String attrName = attrDef.getName();
      final XmlName xmlName = new XmlName(attrName, namespace);
      if (!skippedAttributes.contains(xmlName)) {
        skippedAttributes.add(xmlName);
        registerAttribute(attrDef, styleable.getName(), namespace, element, callback);
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
                                        @Nullable String parentStyleableName,
                                        @Nullable String namespaceKey,
                                        @NotNull DomElement element,
                                        @NotNull AttributeProcessor callback) {
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
      extension.addCustomAnnotation(new RequiredImpl());
    }
  }

  private static void registerAttributes(AndroidFacet facet,
                                         DomElement element,
                                         @NotNull String[] styleableNames,
                                         AttributeProcessor callback,
                                         Set<XmlName> skipNames) {
    registerAttributes(facet, element, styleableNames, null, callback, skipNames);
    registerAttributes(facet, element, styleableNames, SYSTEM_RESOURCE_PACKAGE, callback, skipNames);
  }

  private static void registerAttributes(AndroidFacet facet,
                                         DomElement element,
                                         @NotNull String[] styleableNames,
                                         @Nullable String resPackage,
                                         AttributeProcessor callback,
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

  private static void registerAttributesForClassAndSuperclasses(AndroidFacet facet,
                                                                DomElement element,
                                                                PsiClass c,
                                                                AttributeProcessor callback,
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
  private static PsiClass getSuperclass(@NotNull final PsiClass c) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      @Nullable
      public PsiClass compute() {
        return c.isValid() ? c.getSuperClass() : null;
      }
    });
  }

  /**
   * Yield attributes for resources in xml/ folder
   */
  public static void processXmlAttributes(@NotNull AndroidFacet facet,
                                          @NotNull XmlTag tag,
                                          @NotNull XmlResourceElement element,
                                          @NotNull Set<XmlName> skipAttrNames,
                                          @NotNull AttributeProcessor callback) {
    final String tagName = tag.getName();
    String styleableName = AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.get(tagName);
    if (styleableName != null) {
      final Set<XmlName> newSkipAttrNames = new HashSet<>();
      if (element instanceof Intent) {
        newSkipAttrNames.add(new XmlName("action", NS_RESOURCES));
      }

      registerAttributes(facet, element, new String[]{styleableName}, SYSTEM_RESOURCE_PACKAGE, callback, newSkipAttrNames);
    }

    // for preferences
    Map<String, PsiClass> prefClassMap = getPreferencesClassMap(facet);
    String prefClassName = element.getXmlTag().getName();
    PsiClass c = prefClassMap.get(prefClassName);

    // register attributes by preference class
    registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames);

    // register attributes by widget
    String widgetClassName = AndroidTextUtils.trimEndOrNullize(prefClassName, PREFERENCE_TAG_NAME);
    if (widgetClassName != null) {
      PsiClass widgetClass = LayoutViewClassUtils.findClassByTagName(facet, widgetClassName, AndroidUtils.VIEW_CLASS_NAME);
      registerAttributesForClassAndSuperclasses(facet, element, widgetClass, callback, skipAttrNames);
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

  /**
   * Put all names of classes in passed collection to an array
   */
  private static String[] getClassNames(@NotNull Collection<PsiClass> classes) {
    final String[] result = new String[classes.size()];

    int i = 0;
    for (PsiClass aClass : classes) {
      result[i++] = aClass.getName();
    }

    return result;
  }

  /**
   * This method tries to find styleable definitions for a given class by trying a bunch of suffixes
   * ({@link #LAYOUT_ATTRIBUTES_SUFS}), yielding attributes from them through a callback.
   *
   * FIXME: this is a heuristic from old code and doesn't make sense - two of three suffixes appear only once.
   */
  private static void registerAttributesFromSuffixedStyleables(@NotNull AndroidFacet facet,
                                                               @NotNull DomElement element,
                                                               @NotNull PsiClass psiClass,
                                                               @NotNull AttributeProcessor callback,
                                                               @NotNull Set<XmlName> skipAttrNames) {
    String styleableName = psiClass.getName();
    if (styleableName != null) {
      String[] styleableNames = new String[LAYOUT_ATTRIBUTES_SUFS.length];
      for (int i = 0; i < LAYOUT_ATTRIBUTES_SUFS.length; i++) {
        styleableNames[i] = styleableName + LAYOUT_ATTRIBUTES_SUFS[i];
      }
      registerAttributes(facet, element, styleableNames, callback, skipAttrNames);
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
    Map<String, PsiClass> map = getViewClassMap(facet);

    // Add tools namespace attributes to layout tags, but not those that are databinding-specific ones
    if (!(element instanceof DataBindingElement)) {
      registerToolsAttribute(ATTR_TARGET_API, callback);
      if (tag.getParentTag() == null) {
        registerToolsAttribute(ATTR_CONTEXT, callback);
        registerToolsAttribute(ATTR_MENU, callback);
        registerToolsAttribute(ATTR_ACTION_BAR_NAV_MODE, callback);
        registerToolsAttribute(ATTR_SHOW_IN, callback);
      }

      // AdapterView resides in android.widget package and thus is acquired from class map by short name
      final PsiClass adapterView = map.get(ADAPTER_VIEW);
      final PsiClass psiClass = map.get(tag.getName());
      if (adapterView != null && psiClass != null && psiClass.isInheritor(adapterView, true)) {
        registerToolsAttribute(ATTR_LISTITEM, callback);
        registerToolsAttribute(ATTR_LISTHEADER, callback);
        registerToolsAttribute(ATTR_LISTFOOTER, callback);
      }

      final PsiClass drawerLayout = map.get(CLASS_DRAWER_LAYOUT);
      if (drawerLayout != null && psiClass != null &&
          (psiClass.isEquivalentTo(drawerLayout) || psiClass.isInheritor(drawerLayout, true))) {
        registerToolsAttribute(ATTR_OPEN_DRAWER, callback);
      }
    }

    if (element instanceof Tag || element instanceof Include || element instanceof Data) {
      // don't want view attributes inside these tags
      return;
    }

    String tagName = tag.getName();
    if (tagName.equals("view")) {
      // In Android layout XMLs, one can write, e.g.
      //   <view class="LinearLayout" />
      //
      // instead of
      //   <LinearLayout />
      //
      // In this case code here treats <view> tag as a special case, in which it adds all the attributes
      // from all available styleables that have the same simple names as found descendants of View class.
      //
      // See LayoutInflater#createViewFromTag in Android framework for inflating code

      String[] styleableNames = getClassNames(map.values());
      registerAttributes(facet, element, styleableNames, callback, skipAttrNames);
    }
    else {
      PsiClass c = map.get(tagName);
      registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames);
    }

    XmlTag parentTag = tag.getParentTag();

    if (parentTag != null) {
      final String parentTagName = parentTag.getName();

      if (!VIEW_MERGE.equals(parentTagName)) {
        PsiClass c = map.get(parentTagName);
        while (c != null) {
          registerAttributesFromSuffixedStyleables(facet, element, c, callback, skipAttrNames);
          c = getSuperclass(c);
        }
        return;
      }
    }
    for (PsiClass c : map.values()) {
      registerAttributesFromSuffixedStyleables(facet, element, c, callback, skipAttrNames);
    }
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
    XmlTag tag = element.getXmlTag();

    final Set<XmlName> skippedAttributes =
      processAllExistingAttrsFirst ? registerExistingAttributes(facet, tag, element, callback) : new HashSet<>();

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
        getLog().warn(String.format("@Styleable(%s) annotation doesn't point to existing styleable", styleableName));
      }
      else {
        registerStyleableAttributes(element, styleable, ANDROID_URI, callback, skippedAttributes);
      }
    }

    // Handle interpolator XML tags: they don't have their own DomElement interfaces, and all use InterpolatorElement at the moment.
    // Thus, they can't use @Styleable annotation and there is a mapping from tag name to styleable name that's used below.

    // This snippet doesn't look much different from lines above for handling @Styleable annotations above,
    // but is used to provide customized warning message
    // TODO: figure it out how to make it DRY without introducing new method with lots of arguments
    if (element instanceof InterpolatorElement) {
      final String styleableName = InterpolatorDomFileDescription.getInterpolatorStyleableByTagName(tag.getName());
      if (styleableName != null) {
        final StyleableDefinition styleable = definitions.getStyleableByName(styleableName);
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

  private static void registerToolsAttribute(@NotNull String attributeName, @NotNull AttributeProcessor callback) {
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
      getLog().warn("No attribute definition for tools attribute " + attributeName);
    }
  }

  @NotNull
  private static Set<XmlName> registerExistingAttributes(@NotNull AndroidFacet facet,
                                                         @NotNull XmlTag tag,
                                                         @NotNull AndroidDomElement element,
                                                         @NotNull AttributeProcessor callback) {
    final Set<XmlName> result = new HashSet<>();
    XmlAttribute[] attrs = tag.getAttributes();

    for (XmlAttribute attr : attrs) {
      String localName = attr.getLocalName();

      if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
        if (!"xmlns".equals(attr.getNamespacePrefix())) {
          AttributeDefinition attrDef = AndroidDomUtil.getAttributeDefinition(facet, attr);

          if (attrDef != null) {
            String namespace = attr.getNamespace();
            result.add(new XmlName(attr.getLocalName(), attr.getNamespace()));
            registerAttribute(attrDef, null, namespace.length() > 0 ? namespace : null, element, callback);
          }
        }
      }
    }
    return result;
  }

  public interface AttributeProcessor {
    @Nullable
    DomExtension processAttribute(@NotNull XmlName xmlName,
                                  @NotNull AttributeDefinition attrDef,
                                  @Nullable String parentStyleableName);
  }
}
