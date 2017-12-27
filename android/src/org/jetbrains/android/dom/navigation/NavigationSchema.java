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
package org.jetbrains.android.dom.navigation;

import com.android.SdkConstants;
import com.google.common.collect.*;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.ClassMaps;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides information on OOTB and user-specified navigation tags and attributes.
 */
public class NavigationSchema implements Disposable {
  public static final String TAG_ACTION = "action";
  public static final String ATTR_DESTINATION = "destination";

  public static final String TAG_INCLUDE = "include";

  public static final String NAVIGATOR_CLASS_NAME = "android.support.navigation.app.nav.Navigator";
  public static final String NAV_GRAPH_NAVIGATOR_CLASS_NAME = "android.support.navigation.app.nav.NavGraphNavigator";
  public static final String FRAGMENT_NAVIGATOR_CLASS_NAME = "android.support.navigation.app.nav.FragmentNavigator";
  public static final String ACTIVITY_NAVIGATOR_CLASS_NAME = "android.support.navigation.app.nav.ActivityNavigator";

  public static final Map<String, DestinationType> NAV_CLASS_TO_TYPE = ImmutableMap.of(
    NAV_GRAPH_NAVIGATOR_CLASS_NAME, DestinationType.NAVIGATION,
    FRAGMENT_NAVIGATOR_CLASS_NAME, DestinationType.FRAGMENT,
    ACTIVITY_NAVIGATOR_CLASS_NAME, DestinationType.ACTIVITY);
  public static final String ANNOTATION_NAV_TAG_NAME = "android.support.navigation.app.nav.Navigator.Name";
  public static final String ATTR_NAV_TYPE = "navType";
  public static final String ATTR_START_DESTINATION = "startDestination";

  // TODO: get these from the xml metadata once supported by platform
  public static final Map<String, DestinationType> DESTINATION_SUPERCLASS_TO_TYPE = ImmutableMap.of(
    SdkConstants.CLASS_ACTIVITY, DestinationType.ACTIVITY,
    SdkConstants.CLASS_FRAGMENT, DestinationType.FRAGMENT,
    SdkConstants.CLASS_V4_FRAGMENT, DestinationType.FRAGMENT);

  private static final Map<AndroidFacet, NavigationSchema> ourSchemas = new HashMap<>();

  private Map<String, DestinationType> myTagToDestinationType;
  private Map<DestinationType, String> myTypeToRootTag;

  private Map<DestinationType, Map<String, PsiClass>> myNavTagToClass;

  private final ClassMaps myClassMaps;

  public enum DestinationType {
    NAVIGATION,
    FRAGMENT,
    ACTIVITY,
    OTHER
  }

  @NotNull
  public static NavigationSchema getOrCreateSchema(@NotNull AndroidFacet facet) {
    NavigationSchema result = ourSchemas.get(facet);
    if (result == null) {
      result = new NavigationSchema(facet);
      ourSchemas.put(facet, result);
      Disposer.register(facet, result);
    }
    return result;
  }

  private NavigationSchema(@NotNull AndroidFacet facet) {
    myClassMaps = ClassMaps.getInstance(facet);
  }

  @Override
  public void dispose() {
    ourSchemas.remove(myClassMaps.getFacet());
  }

  @Nullable
  public PsiClass getDestinationClassByTag(@NotNull String tagName) {
    for (Map<String, PsiClass> typeMap : getTypeTagClassMap().values()) {
      PsiClass c = typeMap.get(tagName);
      if (c != null) {
        return c;
      }
    }
    return null;
  }

  @NotNull
  private Map<DestinationType, Map<String, PsiClass>> getTypeTagClassMap() {
    initIfNeeded();
    return myNavTagToClass;
  }

  @NotNull
  public Map<String, PsiClass> getDestinationClassByTagMap(@NotNull DestinationType type) {
    initIfNeeded();
    return myNavTagToClass.get(type);
  }

  @NotNull
  private Map<String, PsiClass> getClassMap(@NotNull String className) {
    Map<String, PsiClass> result = myClassMaps.getClassMap(className);
    if (result.isEmpty()) {
      // TODO: handle the not-synced-yet case
      throw new RuntimeException();
    }
    return result;
  }

  private void initIfNeeded() {
    if (myNavTagToClass == null) {
      Map<DestinationType, Map<String, PsiClass>> result = new HashMap<>();
      Map<String, DestinationType> tagToType = new HashMap<>();

      Map<DestinationType, Map<String, PsiClass>> classNameMaps = new HashMap<>();
      for (Map.Entry<String, DestinationType> types : NAV_CLASS_TO_TYPE.entrySet()) {
        classNameMaps.put(types.getValue(), getClassMap(types.getKey()));
        result.put(types.getValue(), new HashMap<>());
      }
      result.put(DestinationType.OTHER, new HashMap<>());

      Map<String, PsiClass> map = getClassMap(NAVIGATOR_CLASS_NAME);
      for (String className : map.keySet()) {
        PsiClass c = map.get(className);
        for (PsiAnnotation annotation : AnnotationUtil.getAllAnnotations(c, false, null)) {
          if (ANNOTATION_NAV_TAG_NAME.equals(annotation.getQualifiedName())) {
            String value = AnnotationUtil.getStringAttributeValue(annotation, "value");
            if (value != null) {
              DestinationType resolvedType = null;
              for (DestinationType type : classNameMaps.keySet()) {
                Map<String, PsiClass> classNameMap = classNameMaps.get(type);
                if (classNameMap.containsKey(className)) {
                  resolvedType = type;
                  break;
                }
              }
              if (resolvedType == null) {
                resolvedType = DestinationType.OTHER;
              }
              result.get(resolvedType).put(value, c);
              tagToType.put(value, resolvedType);
            }
          }
        }
      }
      myNavTagToClass = result;
      myTagToDestinationType = tagToType;

      Map<DestinationType, String> typeToTag = new HashMap<>();
      Map<DestinationType, PsiClass> typeToBestClass = new HashMap<>();
      for (String tag : myTagToDestinationType.keySet()) {
        DestinationType type = myTagToDestinationType.get(tag);
        PsiClass psiClass = myNavTagToClass.get(type).get(tag);
        if (!typeToBestClass.containsKey(type) || typeToBestClass.get(type).isInheritor(psiClass, true)) {
          typeToBestClass.put(type, psiClass);
          typeToTag.put(type, tag);
        }
      }
      myTypeToRootTag = typeToTag;
    }
  }

  // TODO: it seems like the framework should do this somehow
  @NotNull
  public Multimap<Class<? extends AndroidDomElement>, String> getDestinationSubtags(@NotNull String tagName) {
    initIfNeeded();
    Multimap<Class<? extends AndroidDomElement>, String> result = HashMultimap.create();
    if (myTagToDestinationType.get(tagName) == DestinationType.NAVIGATION) {
      for (Map<String, PsiClass> typeMap : getTypeTagClassMap().values()) {
        for (String subTag : typeMap.keySet()) {
          result.put(NavDestinationElement.class, subTag);
        }
      }
    }
    result.put(NavActionElement.class, TAG_ACTION);
    // TODO: other tags
    return result;
  }

  @Nullable
  public DestinationType getDestinationType(@NotNull String tag) {
    initIfNeeded();
    return myTagToDestinationType.get(tag);
  }

  @Nullable
  public String getTag(@NotNull DestinationType type) {
    initIfNeeded();
    return myTypeToRootTag.get(type);
  }

  @Nullable
  public String getTagForComponentSuperclass(@NotNull String superclassName) {
    initIfNeeded();
    DestinationType type = DESTINATION_SUPERCLASS_TO_TYPE.get(superclassName);
    if (type != null) {
      return getTag(type);
    }
    return null;
  }

  @Contract("null -> null")
  @Nullable
  public String findTagForComponent(@Nullable PsiClass layoutClass) {
    while (layoutClass != null) {
      String qName = layoutClass.getQualifiedName();
      if (qName != null) {
        String tag = getTagForComponentSuperclass(qName);
        if (tag != null) {
          return tag;
        }
      }
      layoutClass = layoutClass.getSuperClass();
    }
    return null;
  }
}
