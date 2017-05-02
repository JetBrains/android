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

import com.android.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.ClassMaps;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.facet.AndroidFacet;
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
  public static final String TAG_NAVIGATION = "navigation";
  @VisibleForTesting  // Normally the introspective methods below should be used
  public static final String TAG_FRAGMENT = "fragment";
  public static final String DEFAULT_ROOT_TAG = TAG_NAVIGATION;

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

  private Map<String, DestinationType> myTagToDestinationType;

  private static final Map<AndroidFacet, NavigationSchema> ourSchemas = new HashMap<>();

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

}
