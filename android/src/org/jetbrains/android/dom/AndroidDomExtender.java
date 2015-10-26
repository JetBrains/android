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

import com.android.SdkConstants;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
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
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animation.AnimationElement;
import org.jetbrains.android.dom.animator.AndroidAnimatorUtil;
import org.jetbrains.android.dom.animator.AnimatorElement;
import org.jetbrains.android.dom.attrs.*;
import org.jetbrains.android.dom.color.ColorDomElement;
import org.jetbrains.android.dom.color.ColorStateListItem;
import org.jetbrains.android.dom.converters.CompositeConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.drawable.*;
import org.jetbrains.android.dom.layout.*;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElement;
import org.jetbrains.android.dom.menu.MenuElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.transition.*;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.Intent;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SimpleClassMapConstructor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.dom.transition.TransitionDomUtil.*;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDomExtender extends DomExtender<AndroidDomElement> {
  private static final String[] LAYOUT_ATTRIBUTES_SUFS = new String[]{"_Layout", "_MarginLayout", "_Cell"};
  private static final MyAttributeProcessor ourLayoutAttrsProcessor = new MyAttributeProcessor() {
    @Override
    public void process(@NotNull XmlName attrName, @NotNull DomExtension extension, @NotNull DomElement element) {
      // Mark layout_width and layout_height required - if the context calls for it
      String localName = attrName.getLocalName();
      if (!(ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName))) {
        return;
      }
      if ((element instanceof LayoutViewElement || element instanceof Fragment) &&
          SdkConstants.NS_RESOURCES.equals(attrName.getNamespaceKey())) {
        XmlElement xmlElement = element.getXmlElement();
        XmlTag tag = xmlElement instanceof XmlTag ? (XmlTag)xmlElement : null;
        String tagName = tag != null ? tag.getName() : null;
        if (!VIEW_MERGE.equals(tagName) &&
            !TABLE_ROW.equals(tagName) &&
            !VIEW_INCLUDE.equals(tagName) &&
            !REQUEST_FOCUS.equals(tagName) &&
            !TAG_LAYOUT.equals(tagName) &&
            !TAG_DATA.equals(tagName) &&
            !TAG_VARIABLE.equals(tagName) &&
            !TAG_IMPORT.equals(tagName) &&
            !TAG.equals(tagName) &&
            (tag == null || tag.getAttribute(ATTR_STYLE) == null)) {
          XmlTag parentTag = tag != null ? tag.getParentTag() : null;
          String parentTagName = parentTag != null ? parentTag.getName() : null;
          if (!TABLE_ROW.equals(parentTagName) &&
              !TABLE_LAYOUT.equals(parentTagName) &&
              !VIEW_MERGE.equals(parentTagName) &&
              !GRID_LAYOUT.equals(parentTagName) &&
              !FQCN_GRID_LAYOUT_V7.equals(parentTagName)) {
            extension.addCustomAnnotation(new MyRequired());
          }
        }
      }
    }
  };

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

  protected static void registerStyleableAttributes(DomElement element,
                                                    @NotNull StyleableDefinition[] styleables,
                                                    @Nullable String namespace,
                                                    MyCallback callback,
                                                    MyAttributeProcessor processor,
                                                    Set<XmlName> skippedAttrSet) {
    for (StyleableDefinition styleable : styleables) {
      for (AttributeDefinition attrDef : styleable.getAttributes()) {
        String attrName = attrDef.getName();
        if (!skippedAttrSet.contains(new XmlName(attrName, namespace))) {
          skippedAttrSet.add(new XmlName(attrName, namespace));
          registerAttribute(attrDef, styleable.getName(), namespace, callback, processor, element);
        }
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
                                        @Nullable MyAttributeProcessor processor,
                                        @NotNull DomElement element) {
    String name = attrDef.getName();
    if (!SdkConstants.NS_RESOURCES.equals(namespaceKey) && name.startsWith(SdkConstants.PREFIX_ANDROID)) {
      // A styleable-definition in the app namespace (user specified or from a library) can include
      // a reference to a platform attribute. In such a case, register it under the android namespace
      // as opposed to the app namespace. See https://code.google.com/p/android/issues/detail?id=171162
      name = name.substring(SdkConstants.PREFIX_ANDROID.length());
      namespaceKey = SdkConstants.NS_RESOURCES;
    }
    XmlName xmlName = new XmlName(name, namespaceKey);
    final DomExtension extension = callback.processAttribute(xmlName, attrDef, parentStyleableName);

    if (extension == null) {
      return;
    }
    Converter converter = AndroidDomUtil.getSpecificConverter(xmlName, element);
    if (converter == null) {
      if (SdkConstants.TOOLS_URI.equals(namespaceKey)) {
        converter = ToolsAttributeUtil.getConverter(attrDef);
      } else {
        converter = AndroidDomUtil.getConverter(attrDef);
      }
    }

    if (converter != null) {
      extension.setConverter(converter, mustBeSoft(converter, attrDef.getFormats()));
    }
    if (processor != null) {
      processor.process(xmlName, extension, element);
    }
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String[] styleableNames,
                                           MyCallback callback,
                                           MyAttributeProcessor processor,
                                           Set<XmlName> skipNames) {
    registerAttributes(facet, element, styleableNames, null, callback, processor, skipNames);
    registerAttributes(facet, element, styleableNames, SYSTEM_RESOURCE_PACKAGE, callback, processor, skipNames);
  }

  private static StyleableDefinition[] getStyleables(@NotNull AttributeDefinitions definitions, @NotNull String[] names) {
    List<StyleableDefinition> styleables = new ArrayList<StyleableDefinition>();
    for (String name : names) {
      StyleableDefinition styleable = definitions.getStyleableByName(name);
      if (styleable != null) {
        styleables.add(styleable);
      }
    }
    return styleables.toArray(new StyleableDefinition[styleables.size()]);
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String styleableName,
                                           @Nullable String resPackage,
                                           MyCallback callback,
                                           Set<XmlName> skipNames) {
    registerAttributes(facet, element, new String[]{styleableName}, resPackage, callback, null, skipNames);
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String[] styleableNames,
                                           @Nullable String resPackage,
                                           MyCallback callback,
                                           MyAttributeProcessor processor,
                                           Set<XmlName> skipNames) {
    ResourceManager manager = facet.getResourceManager(resPackage);
    if (manager == null) return;
    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) return;
    StyleableDefinition[] styleables = getStyleables(attrDefs, styleableNames);
    registerAttributes(facet, element, styleables, resPackage, callback, processor, skipNames);
  }

  private static void registerAttributes(AndroidFacet facet,
                                         DomElement element,
                                         StyleableDefinition[] styleables, String resPackage,
                                         MyCallback callback,
                                         MyAttributeProcessor processor,
                                         Set<XmlName> skipNames) {
    String namespace = getNamespaceKeyByResourcePackage(facet, resPackage);
    registerStyleableAttributes(element, styleables, namespace, callback, processor, skipNames);
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
                                                                  MyAttributeProcessor processor,
                                                                  Set<XmlName> skipNames) {
    while (c != null) {
      String styleableName = c.getName();
      if (styleableName != null) {
        registerAttributes(facet, element, new String[]{styleableName}, callback, processor, skipNames);
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
      String[] attrsToSkip = element instanceof Intent ? new String[]{"action"} : ArrayUtil.EMPTY_STRING_ARRAY;

      final Set<XmlName> newSkipAttrNames = new HashSet<XmlName>();

      for (String attrName : attrsToSkip) {
        newSkipAttrNames.add(new XmlName(attrName, SdkConstants.NS_RESOURCES));
      }

      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, callback, newSkipAttrNames);
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
    registerAttributesForClassAndSuperclasses(facet, element, c, callback, null, skipAttrNames);

    //register attributes by widget
    String suffix = "Preference";
    if (prefClassName.endsWith(suffix)) {
      String widgetClassName = prefClassName.substring(0, prefClassName.length() - suffix.length());
      PsiClass widgetClass = SimpleClassMapConstructor.findClassByTagName(facet, widgetClassName, AndroidUtils.VIEW_CLASS_NAME);
      registerAttributesForClassAndSuperclasses(facet, element, widgetClass, callback, null, skipAttrNames);
    }

    if (c != null && isPreference(prefClassMap, c)) {
      registerClassNameSubtags(tag, prefClassMap, PreferenceElement.class, registeredSubtags, callback);
    }
  }

  @NotNull
  public static Map<String, PsiClass> getPreferencesClassMap(@NotNull AndroidFacet facet) {
    return facet.getClassMap(AndroidXmlResourcesUtil.PREFERENCE_CLASS_NAME, SimpleClassMapConstructor.getInstance());
  }

  public static void registerExtensionsForAnimation(final AndroidFacet facet,
                                                    String tagName,
                                                    AnimationElement element,
                                                    MyCallback callback,
                                                    Set<String> registeredSubtags,
                                                    Set<XmlName> skipAttrNames) {
    if (tagName.equals("set")) {
      for (String subtagName : AndroidAnimationUtils.getPossibleChildren(facet)) {
        registerSubtags(subtagName, AnimationElement.class, callback, registeredSubtags);
      }
    }
    final String styleableName = AndroidAnimationUtils.getStyleableNameByTagName(tagName);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(facet.getModule().getProject());
    final PsiClass c = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      @Nullable
      public PsiClass compute() {
        return facade.findClass(AndroidAnimationUtils.ANIMATION_PACKAGE + '.' + styleableName,
                                facet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
      }
    });
    if (c != null) {
      registerAttributesForClassAndSuperclasses(facet, element, c, callback, null, skipAttrNames);
    }
    else {
      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
      String layoutAnim = "LayoutAnimation";
      if (styleableName.endsWith(layoutAnim) && !styleableName.equals(layoutAnim)) {
        registerAttributes(facet, element, layoutAnim, SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
      }
      if (styleableName.endsWith("Animation")) {
        registerAttributes(facet, element, "Animation", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
      }
    }
  }

  public static void registerExtensionsForAnimator(final AndroidFacet facet,
                                                   String tagName,
                                                   AnimatorElement element,
                                                   MyCallback callback,
                                                   Set<String> registeredSubtags,
                                                   Set<XmlName> skipAttrNames) {
    if (tagName.equals("set")) {
      for (String subtagName : AndroidAnimatorUtil.getPossibleChildren()) {
        registerSubtags(subtagName, AnimatorElement.class, callback, registeredSubtags);
      }
    }
    registerAttributes(facet, element, "Animator", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    final String styleableName = AndroidAnimatorUtil.getStyleableNameByTagName(tagName);

    if (styleableName != null) {
      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
  }

  public static Map<String, PsiClass> getViewClassMap(@NotNull AndroidFacet facet) {
    if (DumbService.isDumb(facet.getModule().getProject())) {
      return Collections.emptyMap();
    }
    return facet.getClassMap(AndroidUtils.VIEW_CLASS_NAME, SimpleClassMapConstructor.getInstance());
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
                                               MyAttributeProcessor processor,
                                               Set<XmlName> skipAttrNames) {
    XmlTag parentTag = tag.getParentTag();
    Map<String, PsiClass> map = getViewClassMap(facet);
    if (parentTag != null) {
      final String parentTagName = parentTag.getName();

      if (!VIEW_MERGE.equals(parentTagName)) {
        PsiClass c = map.get(parentTagName);
        while (c != null) {
          registerLayoutAttributes(facet, element, c, callback, processor, skipAttrNames);
          c = getSuperclass(c);
        }
        return;
      }
    }
    for (String className : map.keySet()) {
      PsiClass c = map.get(className);
      registerLayoutAttributes(facet, element, c, callback, processor, skipAttrNames);
    }
  }

  private static void registerLayoutAttributes(AndroidFacet facet,
                                               DomElement element,
                                               PsiClass c,
                                               MyCallback callback,
                                               MyAttributeProcessor processor,
                                               Set<XmlName> skipAttrNames) {
    String styleableName = c.getName();
    if (styleableName != null) {
      for (String suf : LAYOUT_ATTRIBUTES_SUFS) {
        registerAttributes(facet, element, new String[]{styleableName + suf}, callback, processor, skipAttrNames);
      }
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
        registerLayoutAttributes(facet, element, c, callback, ourLayoutAttrsProcessor, skipAttrNames);
      }
      return;
    }
    else if (element instanceof Fragment) {
      registerAttributes(facet, element, new String[]{"Fragment"}, callback, ourLayoutAttrsProcessor, skipAttrNames);
    } else if (element instanceof Tag) {
      registerAttributes(facet, element, "ViewTag", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
      return;
    }
    else {
      String tagName = tag.getName();
      if (!tagName.equals("view")) {
        PsiClass c = map.get(tagName);
        registerAttributesForClassAndSuperclasses(facet, element, c, callback, ourLayoutAttrsProcessor, skipAttrNames);
      }
      else {
        String[] styleableNames = getClassNames(map.values());
        registerAttributes(facet, element, styleableNames, callback, ourLayoutAttrsProcessor, skipAttrNames);
      }
    }
    registerLayoutAttributes(facet, element, tag, callback, ourLayoutAttrsProcessor, skipAttrNames);
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
                                                   Set<XmlName> skippedNames,
                                                   boolean processStaticallyDefinedElements) {
    String styleableName = AndroidManifestUtils.getStyleableNameForElement(element);

    if (styleableName == null) {
      styleableName = AndroidManifestUtils.getStyleableNameByTagName(tagName);
    }
    final Set<XmlName> newSkippedNames = new HashSet<XmlName>(skippedNames);

    if (!processStaticallyDefinedElements) {
      for (String attrName : AndroidManifestUtils.getStaticallyDefinedAttrs(element)) {
        newSkippedNames.add(new XmlName(attrName, SdkConstants.NS_RESOURCES));
      }
    }
    SystemResourceManager manager = facet.getSystemResourceManager();
    if (manager == null) return;
    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) return;
    StyleableDefinition styleable = attrDefs.getStyleableByName(styleableName);
    if (styleable == null) return;

    registerStyleableAttributes(element, new StyleableDefinition[]{styleable}, SdkConstants.NS_RESOURCES, callback,
                                new MyAttributeProcessor() {
                                  @Override
                                  public void process(@NotNull XmlName attrName,
                                                      @NotNull DomExtension extension,
                                                      @NotNull DomElement element) {
                                    if (AndroidManifestUtils.isRequiredAttribute(attrName, element)) {
                                      extension.addCustomAnnotation(new MyRequired());
                                    }
                                  }
                                }, newSkippedNames);

    Set<String> subtagSet;

    if (!processStaticallyDefinedElements) {
      subtagSet = new HashSet<String>();
      Collections.addAll(subtagSet, AndroidManifestUtils.getStaticallyDefinedSubtags(element));
    }
    else {
      subtagSet = Collections.emptySet();
    }
    for (StyleableDefinition child : styleable.getChildren()) {
      String childTagName = AndroidManifestUtils.getTagNameByStyleableName(child.getName());
      if (childTagName != null && !subtagSet.contains(childTagName)) {
        registerSubtags(childTagName, ManifestElement.class, callback, registeredSubtags);
      }
    }
  }

  public static void processAttrsAndSubtags(@NotNull AndroidDomElement element,
                                            @NotNull MyCallback callback,
                                            @NotNull AndroidFacet facet,
                                            boolean processAllExistingAttrsFirst,
                                            boolean processStaticallyDefinedElements) {
    try {
      XmlTag tag = element.getXmlTag();

      final Set<XmlName> skippedAttributes = processAllExistingAttrsFirst
                                             ? registerExistingAttributes(facet, tag, callback, element)
                                             : new HashSet<XmlName>();
      String tagName = tag.getName();
      Set<String> registeredSubtags = new HashSet<String>();
      if (element instanceof ManifestElement) {
        registerExtensionsForManifest(facet, tagName, (ManifestElement)element, callback, registeredSubtags,
                                      skippedAttributes, processStaticallyDefinedElements);
      }
      else if (element instanceof LayoutElement) {
        registerExtensionsForLayout(facet, tag, (LayoutElement)element, callback, registeredSubtags, skippedAttributes);
      }
      else if (element instanceof AnimationElement) {
        registerExtensionsForAnimation(facet, tagName, (AnimationElement)element, callback, registeredSubtags, skippedAttributes);
      }
      else if (element instanceof AnimatorElement) {
        registerExtensionsForAnimator(facet, tagName, (AnimatorElement)element, callback, registeredSubtags, skippedAttributes);
      }
      else if (element instanceof MenuElement) {
        String styleableName = StringUtil.capitalize(tagName);
        if (!styleableName.equals("Menu")) {
          styleableName = "Menu" + styleableName;
        }
        registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, callback, skippedAttributes);
      }
      else if (element instanceof XmlResourceElement) {
        registerExtensionsForXmlResources(facet, tag, (XmlResourceElement)element, callback, registeredSubtags, skippedAttributes);
      }
      else if (element instanceof DrawableDomElement || element instanceof ColorDomElement) {
        registerExtensionsForDrawable(facet, tagName, element, callback, skippedAttributes);
      }
      else if (element instanceof TransitionDomElement) {
        registerExtensionsForTransition(facet, tagName, (TransitionDomElement)element, callback, registeredSubtags, skippedAttributes);
      }
      if (!processStaticallyDefinedElements) {
        Collections.addAll(registeredSubtags, AndroidDomUtil.getStaticallyDefinedSubtags(element));
      }
    }
    catch (MyStopException ignored) {
    }
  }

  private static void registerExtensionsForDrawable(AndroidFacet facet,
                                                    String tagName,
                                                    AndroidDomElement element,
                                                    MyCallback callback,
                                                    Set<XmlName> skipAttrNames) {
    final String specialStyleableName = AndroidDrawableDomUtil.SPECIAL_STYLEABLE_NAMES.get(tagName);
    if (specialStyleableName != null) {
      registerAttributes(facet, element, specialStyleableName, SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }

    if (element instanceof DrawableStateListItem || element instanceof ColorStateListItem) {
      registerAttributes(facet, element, "DrawableStates", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);

      final AttributeDefinitions attrDefs = getAttrDefs(facet);
      if (attrDefs != null) {
        registerAttributes(facet, element, attrDefs.getStateStyleables(), SYSTEM_RESOURCE_PACKAGE, callback, null, skipAttrNames);
      }
    }
    else if (element instanceof LayerListItem) {
      registerAttributes(facet, element, "LayerDrawableItem", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    else if (element instanceof LevelListItem) {
      registerAttributes(facet, element, "LevelListDrawableItem", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    else if (element instanceof AnimationListItem) {
      registerAttributes(facet, element, "AnimationDrawableItem", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    else if (element instanceof AnimatedStateListTransition) {
      registerAttributes(facet, element, "AnimatedStateListDrawableTransition", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    else if (element instanceof VectorPath) {
      registerAttributes(facet, element, "VectorDrawablePath", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    else if (element instanceof VectorGroup) {
      registerAttributes(facet, element, "VectorDrawableGroup", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    else if (element instanceof VectorClipPath) {
      registerAttributes(facet, element, "VectorDrawableClipPath", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    else if (element instanceof AnimatedVectorTarget) {
      registerAttributes(facet, element, "AnimatedVectorDrawableTarget", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
  }

  public static void registerExtensionsForTransition(final AndroidFacet facet,
                                                   String tagName,
                                                   TransitionDomElement element,
                                                   MyCallback callback,
                                                   Set<String> registeredSubTags,
                                                   Set<XmlName> skipAttrNames) {
    if (tagName.equals(FADE_TAG)) {
      registerTransition(facet, element, callback, registeredSubTags, skipAttrNames, "Fade");
    } else if (tagName.equals(TRANSITION_SET_TAG)) {
      registerTransition(facet, element, callback, registeredSubTags, skipAttrNames, "TransitionSet");

      registerSubtags(TRANSITION_SET_TAG, TransitionSet.class, callback, registeredSubTags);

      // See TransitionInflater#createTransitionFromXml:
      registerSubtags(FADE_TAG, Fade.class, callback, registeredSubTags);
      registerSubtags(CHANGE_BOUNDS_TAG, ChangeBounds.class, callback, registeredSubTags);
      registerSubtags(SLIDE_TAG, Slide.class, callback, registeredSubTags);
      registerSubtags(EXPLODE_TAG, Explode.class, callback, registeredSubTags);
      registerSubtags(CHANGE_IMAGE_TRANSFORM_TAG, ChangeImageTransform.class, callback, registeredSubTags);
      registerSubtags(CHANGE_TRANSFORM_TAG, ChangeTransform.class, callback, registeredSubTags);
      registerSubtags(CHANGE_CLIP_BOUNDS_TAG, ChangeClipBounds.class, callback, registeredSubTags);
      registerSubtags(AUTO_TRANSITION_TAG, AutoTransition.class, callback, registeredSubTags);
      registerSubtags(RECOLOR_TAG, Recolor.class, callback, registeredSubTags);
      registerSubtags(CHANGE_SCROLL_TAG, ChangeScroll.class, callback, registeredSubTags);
      registerSubtags(ARC_MOTION_TAG, ArcMotion.class, callback, registeredSubTags);
      registerSubtags(PATH_MOTION_TAG, PathMotion.class, callback, registeredSubTags);
      registerSubtags(PATTERN_PATH_MOTION_TAG, PatternPathMotion.class, callback, registeredSubTags);
      registerSubtags(TRANSITION_TAG, TransitionSetTransition.class, callback, registeredSubTags);

    } else if (tagName.equals(TRANSITION_MANAGER_TAG)) {
      registerSubtags(TRANSITION_TAG, TransitionTag.class, callback, registeredSubTags);
    } else if (tagName.equals(TRANSITION_TAG)) {
      registerAttributes(facet, element, "TransitionManager", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    } else if (tagName.equals(TARGETS_TAG)) {
      registerSubtags(TARGET_TAG, Target.class, callback, registeredSubTags);
    } else if (tagName.equals(TARGET_TAG)) {
      registerAttributes(facet, element, "TransitionTarget", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    } else if (tagName.equals(SLIDE_TAG)) {
      registerTransition(facet, element, callback, registeredSubTags, skipAttrNames, "Slide");
    } else if (tagName.equals(CHANGE_TRANSFORM_TAG)) {
      registerTransition(facet, element, callback, registeredSubTags, skipAttrNames, "ChangeTransform");
    } else if (tagName.equals(ARC_MOTION_TAG)) {
      registerTransition(facet, element, callback, registeredSubTags, skipAttrNames, "ArcMotion");
    } else if (tagName.equals(PATTERN_PATH_MOTION_TAG)) {
      registerTransition(facet, element, callback, registeredSubTags, skipAttrNames, "PatternPathMotion");
    } else if (tagName.equals(AUTO_TRANSITION_TAG)
               || tagName.equals(CHANGE_BOUNDS_TAG)
               || tagName.equals(EXPLODE_TAG)
               || tagName.equals(CHANGE_IMAGE_TRANSFORM_TAG)
               || tagName.equals(CHANGE_CLIP_BOUNDS_TAG)
               || tagName.equals(RECOLOR_TAG)
               || tagName.equals(CHANGE_SCROLL_TAG)
               || tagName.equals(PATH_MOTION_TAG)) {
      registerTransition(facet, element, callback, registeredSubTags, skipAttrNames, null);
    }
  }

  public static void registerTransition(AndroidFacet facet,
                                        TransitionDomElement element,
                                        MyCallback callback,
                                        Set<String> registeredSubTags,
                                        Set<XmlName> skipAttrNames,
                                        @Nullable String specific) {
    registerAttributes(facet, element, "Transition", SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    if (specific != null) {
      registerAttributes(facet, element, specific, SYSTEM_RESOURCE_PACKAGE, callback, skipAttrNames);
    }
    registerSubtags(TARGETS_TAG, Targets.class, callback, registeredSubTags);
  }

  @Nullable
  private static AttributeDefinitions getAttrDefs(AndroidFacet facet) {
    final SystemResourceManager manager = facet.getSystemResourceManager();
    return manager != null ? manager.getAttributeDefinitions() : null;
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
  private static Set<XmlName> registerExistingAttributes(AndroidFacet facet,
                                                         XmlTag tag,
                                                         MyCallback callback,
                                                         AndroidDomElement element) {
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
            registerAttribute(attrDef, null, namespace.length() > 0 ? namespace : null, callback, null, element);
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

  private interface MyAttributeProcessor {
    void process(@NotNull XmlName attrName, @NotNull DomExtension extension, @NotNull DomElement element);
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

  private static class MyStopException extends RuntimeException {
  }

  public static class MyCallback {

    protected void stop() {
      throw new MyStopException();
    }

    @Nullable
    DomExtension processAttribute(@NotNull XmlName xmlName, @NotNull AttributeDefinition attrDef, @Nullable String parentStyleableName) {
      return null;
    }

    void processSubtag(@NotNull XmlName xmlName, @NotNull Type type) {
    }
  }
}
