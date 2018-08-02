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
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.psi.TagToClassMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Query;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.android.SdkConstants.TAG_DEEP_LINK;
import static com.android.SdkConstants.TAG_INCLUDE;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.*;

/**
 * Provides information on OOTB and user-specified navigation tags and attributes.
 * <p>
 * TODO: support updates (e.g. custom navigator created)
 */
public class NavigationSchema implements Disposable {

  public static boolean enableNavigationEditor() {
    return StudioFlags.ENABLE_NAV_EDITOR.get();
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Constants
  /////////////////////////////////////////////////////////////////////////////

  public static final String TAG_ACTION = "action";
  public static final String TAG_ARGUMENT = "argument";
  public static final String ATTR_DESTINATION = "destination";

  // TODO: move these to a UI-specific place
  public static final String INCLUDE_GRAPH_LABEL = "Include Graph";
  public static final String ACTION_LABEL = "Action";

  private static final String NAVIGATOR_CLASS_NAME = "androidx.navigation.Navigator";

  // TODO: it would be nice if this mapping were somehow supplied by the platform
  private static final Map<String, DestinationType> NAV_CLASS_TO_TYPE = ImmutableMap.of(
    "androidx.navigation.NavGraph", NAVIGATION,
    "androidx.navigation.fragment.FragmentNavigator.Destination", FRAGMENT,
    "androidx.navigation.ActivityNavigator.Destination", ACTIVITY);

  private static final String ANNOTATION_NAV_TAG_NAME = "androidx.navigation.Navigator.Name";

  public static final String ATTR_NAV_TYPE = "navType";
  public static final String ATTR_POP_UP_TO = "popUpTo";
  public static final String ATTR_POP_UP_TO_INCLUSIVE = "popUpToInclusive";
  public static final String ATTR_SINGLE_TOP = "launchSingleTop";
  public static final String ATTR_ENTER_ANIM = "enterAnim";
  public static final String ATTR_EXIT_ANIM = "exitAnim";
  public static final String ATTR_POP_ENTER_ANIM = "popEnterAnim";
  public static final String ATTR_POP_EXIT_ANIM = "popExitAnim";
  public static final String ATTR_ACTION = "action";
  public static final String ATTR_DATA = "data";
  public static final String ATTR_DATA_PATTERN = "dataPattern";
  public static final String ATTR_DEFAULT_VALUE = "defaultValue";

  // TODO: it would be nice to have this generated dynamically, but there's no way to know what the default navigator for a type should be.
  private Map<DestinationType, String> myTypeToRootTag = ImmutableMap.of(
    FRAGMENT, "fragment",
    ACTIVITY, "activity",
    NAVIGATION, "navigation"
  );

  public enum DestinationType {
    NAVIGATION,
    FRAGMENT,
    ACTIVITY,
    OTHER
  }

  //endregion

  /////////////////////////////////////////////////////////////////////////////
  //region Instance Data
  /////////////////////////////////////////////////////////////////////////////

  private final AndroidFacet myFacet;

  // TODO: it would be nice if this mapping were somehow supplied by the platform
  public static final Map<String, DestinationType> DESTINATION_SUPERCLASS_TO_TYPE = ImmutableMap.of(
    SdkConstants.CLASS_ACTIVITY, ACTIVITY,
    SdkConstants.CLASS_FRAGMENT, FRAGMENT,
    SdkConstants.CLASS_V4_FRAGMENT.oldName(), FRAGMENT,
    SdkConstants.CLASS_V4_FRAGMENT.newName(), FRAGMENT);

  private static final Multimap<String, String> NAVIGATION_TO_DESTINATION_SUPERCLASS = HashMultimap.create();

  private Map<String, DestinationType> myTagToDestinationType;
  private Multimap<String, PsiClass> myTagToNavigatorClass;

  static {
    // TODO: Remove this hardcoded initialization once the platform supplies this information
    for (Map.Entry<String, DestinationType> entry : DESTINATION_SUPERCLASS_TO_TYPE.entrySet()) {
      String navigatorClass;
      if (entry.getValue() == FRAGMENT) {
        navigatorClass = "androidx.navigation.fragment.FragmentNavigator";
      }
      else if (entry.getValue() == ACTIVITY) {
        navigatorClass = "androidx.navigation.ActivityNavigator";
      }
      else {
        continue;
      }

      NAVIGATION_TO_DESTINATION_SUPERCLASS.put(navigatorClass, entry.getKey());
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Initialization
  /////////////////////////////////////////////////////////////////////////////

  private static final Map<AndroidFacet, NavigationSchema> ourSchemas = new HashMap<>();

  /**
   * Gets the {@code NavigationSchema} for the given {@code facet}. {@link #createIfNecessary(AndroidFacet)} <b>must</b> be called before
   * this method is called.
   */
  @NotNull
  public static synchronized NavigationSchema get(@NotNull AndroidFacet facet) {
    NavigationSchema result = ourSchemas.get(facet);
    Preconditions.checkNotNull(result, "NavigationSchema must be created first!");
    return result;
  }

  /**
   * Creates a {@code NavigationSchema} for the given facet. The navigation library must already be included in the project, or this will
   * throw {@code ClassNotFoundException}.
   */
  public static synchronized void createIfNecessary(@NotNull AndroidFacet facet) throws ClassNotFoundException {
    NavigationSchema result = ourSchemas.get(facet);
    if (result == null) {
      result = new NavigationSchema(facet);
      result.init();
      Disposer.register(facet, result);
      ourSchemas.put(facet, result);
    }
  }


  private NavigationSchema(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().invokeLater(() -> ourSchemas.remove(myFacet));
  }

  private void init() throws ClassNotFoundException {
    Project project = myFacet.getModule().getProject();
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    PsiClass navigatorRoot = javaPsiFacade.findClass(NAVIGATOR_CLASS_NAME, GlobalSearchScope.allScope(project));
    if (navigatorRoot == null) {
      Logger.getInstance(getClass()).warn("Navigator class not found.");
      throw new ClassNotFoundException(NAVIGATOR_CLASS_NAME);
    }
    PsiTypeParameter destinationTypeParam = navigatorRoot.getTypeParameters()[0];

    Map<String, DestinationType> tagToType = new HashMap<>();
    Multimap<String, PsiClass> tagToClass = HashMultimap.create();

    for (PsiClass navClass : getClassMap(NAVIGATOR_CLASS_NAME).values()) {
      if (navClass.equals(navigatorRoot)) {
        // Don't keep the root navigator
        continue;
      }

      DestinationType type = getType(navClass, navigatorRoot, destinationTypeParam);
      String tag = getTagAttributeValue(navClass);

      if (tag == null) {
        continue;
      }

      if (tagToType.getOrDefault(tag, type) != type) {
        Logger.getInstance(NavigationSchema.class).warn("Multiple destination types for tag " + navClass);
      }
      tagToType.put(tag, type);
      tagToClass.put(tag, navClass);
    }
    // Doesn't come from library, so have to add manually.
    tagToType.put(TAG_INCLUDE, NAVIGATION);

    myTagToNavigatorClass = tagToClass;
    myTagToDestinationType = tagToType;
  }

  @NotNull
  private Map<String, PsiClass> getClassMap(@NotNull String className) {
    Map<String, PsiClass> result = TagToClassMapper.getInstance(myFacet.getModule()).getClassMap(className);
    if (result.isEmpty()) {
      // TODO: handle the not-synced-yet case
      throw new RuntimeException(className + " not found");
    }
    return result;
  }

  @NotNull
  private static DestinationType getType(@NotNull PsiClass subNav, @NotNull PsiClass navigatorRoot, PsiTypeParameter destinationTypeParam) {
    PsiType resolved =
      TypeConversionUtil.getSuperClassSubstitutor(navigatorRoot, PsiTypesUtil.getClassType(subNav)).substitute(destinationTypeParam);

    if (resolved == null) {
      // Shouldn't happen unless there's code errors in the project
      return OTHER;
    }
    return NAV_CLASS_TO_TYPE.getOrDefault(resolved.getCanonicalText(), OTHER);
  }

  @Nullable
  private static String getTagAttributeValue(@NotNull PsiClass subNav) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(subNav, ANNOTATION_NAV_TAG_NAME);
    return annotation == null ? null : AnnotationUtil.getStringAttributeValue(annotation, "value");
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region XML Tag Schema
  /////////////////////////////////////////////////////////////////////////////

  /**
   * For the given {@code tagName}, gets a map from {@link AndroidDomElement} class to all subtags names of that type.
   * <p>
   * Implementation note: We define the hierarchy this way instead of via the normal mechanism
   * (https://www.jetbrains.org/intellij/sdk/docs/reference_guide/frameworks_and_external_apis/xml_dom_api.html)
   * since we need to support custom tags that aren't known at compile time.
   * TODO: investigate whether this can be done using the normal mechanism and a DomExtender (specifically for the root tag).
   */
  @NotNull
  public Multimap<Class<? extends AndroidDomElement>, String> getDestinationSubtags(@NotNull String tagName) {
    if (tagName.equals(TAG_ACTION)) {
      return ImmutableSetMultimap.of(NavArgumentElement.class, TAG_ARGUMENT);
    }
    DestinationType type = getDestinationType(tagName);
    if (type == null || isIncludeTag(tagName)) {
      return ImmutableListMultimap.of();
    }
    Multimap<Class<? extends AndroidDomElement>, String> result = HashMultimap.create();
    if (type == NAVIGATION) {
      myTagToDestinationType
        .forEach((key, value) -> result.put(value == NAVIGATION ? NavGraphElement.class : ConcreteDestinationElement.class, key));
    }
    if (type != ACTIVITY) {
      result.put(NavActionElement.class, TAG_ACTION);
    }
    result.put(DeeplinkElement.class, TAG_DEEP_LINK);
    result.put(NavArgumentElement.class, TAG_ARGUMENT);
    return result;
  }

  @Nullable
  public String getDefaultTag(@NotNull DestinationType type) {
    return myTypeToRootTag.get(type);
  }

  @NotNull
  public static List<String> getPossibleRootsMaybeWithoutSchema(@NotNull AndroidFacet facet) {
    Application application = ApplicationManager.getApplication();
    AtomicReference<List<String>> result = new AtomicReference<>();

    application.invokeAndWait(() -> application.runReadAction(() -> {
      try {
        createIfNecessary(facet);
        result.set(get(facet).getPossibleRoots());
      }
      catch (ClassNotFoundException e) {
        // Navigation wasn't initialized yet, fall back to default
        result.set(ImmutableList.of(NavigationDomFileDescription.DEFAULT_ROOT_TAG));
      }
    }));
    return result.get();
  }

  @NotNull
  public List<String> getPossibleRoots() {
    return myTagToDestinationType.keySet().stream()
                                 .filter(tag -> myTagToDestinationType.get(tag) == NAVIGATION)
                                 .collect(Collectors.toList());
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Schema Mappings
  /////////////////////////////////////////////////////////////////////////////

  @Nullable
  private String getTagForComponentSuperclass(@NotNull String superclassName) {
    DestinationType type = DESTINATION_SUPERCLASS_TO_TYPE.get(superclassName);
    if (type != null) {
      return getDefaultTag(type);
    }
    return null;
  }

  @Nullable
  public DestinationType getDestinationType(@NotNull String tag) {
    return myTagToDestinationType.get(tag);
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

  @NotNull
  public Collection<PsiClass> getDestinationClassesByTag(@NotNull String tagName) {
    return myTagToNavigatorClass.get(tagName);
  }

  public Map<String, DestinationType> getTagTypeMap() {
    return Collections.unmodifiableMap(myTagToDestinationType);
  }

  /**
   * Gets a map of tags to destination classes
   */
  public Multimap<String, PsiClass> getAllDestinationClasses() {
    Multimap<String, PsiClass> result = HashMultimap.create();

    for (String tag : myTagToDestinationType.keySet()) {
      List<PsiClass> classes = getDestinationClasses(tag);
      result.putAll(tag, classes);
    }

    return result;
  }

  /**
   * Gets a list of PsiClasses for destination classes and their subclasses for all navigators
   * that use the specified tag name
   */
  public List<PsiClass> getDestinationClasses(String tag) {
    Module module = myFacet.getModule();
    Project project = module.getProject();
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    List<PsiClass> result = new ArrayList<>();

    for (PsiClass navigatorClass : myTagToNavigatorClass.get(tag)) {
      for (String destinationSuperClass : NAVIGATION_TO_DESTINATION_SUPERCLASS.get(navigatorClass.getQualifiedName())) {
        if (destinationSuperClass == null) {
          continue;
        }

        PsiClass psiSuperClass = javaPsiFacade.findClass(destinationSuperClass, GlobalSearchScope.allScope(project));
        if (psiSuperClass == null) {
          continue;
        }

        Query<PsiClass> query = ClassInheritorsSearch.search(psiSuperClass, GlobalSearchScope.moduleWithDependenciesScope(module), true);
        if (query == null) {
          continue;
        }

        for (PsiClass psiClass : query) {
          if (isNavHostFragment(psiClass)) {
            continue;
          }

          result.add(psiClass);
        }
      }
    }

    return result;
  }

  private static boolean isNavHostFragment(PsiClass psiClass) {
    for (PsiClass superClass : psiClass.getSupers()) {
      if (superClass.getQualifiedName().equals(SdkConstants.FQCN_NAV_HOST_FRAGMENT)) {
        return true;
      }
    }

    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region UI
  /////////////////////////////////////////////////////////////////////////////
  // TODO: move this section to another class, since this class shouldn't deal with UI stuff

  @NotNull
  public String getTagLabel(@NotNull String tag) {
    return getTagLabel(tag, false);
  }

  @NotNull
  public String getTagLabel(@NotNull String tag, boolean isRoot) {
    String text = null;
    if (isIncludeTag(tag)) {
      text = INCLUDE_GRAPH_LABEL;
    }
    else if (TAG_ACTION.equals(tag)) {
      text = ACTION_LABEL;
    }
    else {
      NavigationSchema.DestinationType type = getDestinationType(tag);
      if (type == NAVIGATION) {
        text = isRoot ? "Root Graph" : "Nested Graph";
      }
      else if (type == FRAGMENT) {
        text = "Fragment";
      }
      else if (type == ACTIVITY) {
        text = "Activity";
      }

      if (type == OTHER) {
        text = tag;
      }
      else if (type != null && !tag.equals(getDefaultTag(type))) {
        // If it's a custom tag, show it
        text += " (" + tag + ")";
      }
    }

    assert text != null;
    return text;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Helpers
  /////////////////////////////////////////////////////////////////////////////

  public Boolean isFragmentTag(@NotNull String tag) {
    return getDestinationType(tag) == FRAGMENT;
  }

  public Boolean isActivityTag(@NotNull String tag) {
    return getDestinationType(tag) == ACTIVITY;
  }

  public Boolean isNavigationTag(@NotNull String tag) {
    return getDestinationType(tag) == NAVIGATION;
  }

  public Boolean isOtherTag(@NotNull String tag) {
    return getDestinationType(tag) == OTHER;
  }

  public Boolean isIncludeTag(@NotNull String tag) {
    return tag.equals(TAG_INCLUDE);
  }

  //endregion
}
