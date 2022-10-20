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

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.TAG_DEEP_LINK;
import static com.android.SdkConstants.TAG_INCLUDE;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.ACTIVITY;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.FRAGMENT;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.NAVIGATION;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.OTHER;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NavigationSchema is structured around two mappings:
 *  - Tag name to destination class
 *  - Destination type to destination class
 *
 * Both are in theory many-to-many mappings, though in practice they may
 * often be one-to-one (since the type to class map may only contain the
 * roots of the destination class hierarchies).
 *
 * The class provides two main areas of functionality:
 *  - mapping between tag name, destination type, and destination class
 *  - providing information on the xml schema of navigation files, including
 *    valid root tags and valid children for a given tag.
 *
 * NB: this is only for destinations referenced by Navigators directly, not
 * all destinations in the project (e.g. subclasses of Fragment will never be
 * returned by methods in this class if they aren't referenced by Navigators
 * directly).
 *
 * It also provides a mapping from tag name to styleable, which is needed to
 * find attribute definitions during setup.
 */
public class NavigationSchema implements Disposable {

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

  /**
   * Annotation on the Navigator subclass that specifies the corresponding tag to be used in the navigation xml.
   * Navigators are required to specify this or extend a Navigator that does.
   */
  private static final String ANNOTATION_NAV_TAG_NAME = "androidx.navigation.Navigator.Name";

  /**
   * Optional annotation on the NavDestination subclass that specifies the class of the destination (e.g. Fragment or Activity).
   * This is necessary for some aspects of the nav editor to work fully, but is not required by the framework.
   */
  private static final String ANNOTATION_NAV_CLASS_TYPE = "androidx.navigation.NavDestination.ClassType";

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

  /**
   * We use this as a placeholder for the type navigated over for subgraph destinations, since they don't have one.
   */
  public static final String NAV_GRAPH_DESTINATION = "androidx.navigation.NavGraph";

  /**
   * Map from DestinationType to the tag used by the root OOTB navigator for that tag.
   *
   * Implementation note: If a custom Navigator subclass uses e.g. the fragment tag and doesn't extend from the default FragmentNavigator
   * there's no way for us to know which the default Navigator is without this static mapping.
   * TODO: it would be nice to have this specified by the platform somehow that didn't require hardcoding of class names in Studio.
   */
  @NotNull
  private ImmutableMap<DestinationType, String> myTypeToRootTag = ImmutableMap.of();
  public static final String ROOT_ACTIVITY_NAVIGATOR = "androidx.navigation.ActivityNavigator";
  public static final String ROOT_FRAGMENT_NAVIGATOR = "androidx.navigation.fragment.FragmentNavigator";
  public static final String ROOT_NAV_GRAPH_NAVIGATOR = "androidx.navigation.NavGraphNavigator";

  /**
   * Type of destination, mostly for controlling behavior in the nav editor UI and determining valid subtags in the xml.
   */
  public enum DestinationType {
    NAVIGATION,
    FRAGMENT,
    ACTIVITY,
    OTHER
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Caches
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Reference to a PsiClass that should be stable across psi reparses, but knows how to get a new version of itself if needed.
   */
  private class TypeRef {
    private String myClassName;
    private SmartPsiElementPointer<PsiClass> myPointer;

    TypeRef(@NotNull PsiClass c) {
      myClassName = c.getQualifiedName();
      myPointer = SmartPointerManager.getInstance(c.getProject()).createSmartPsiElementPointer(c);
    }

    TypeRef(@NotNull String className) {
      this.myClassName = className;
    }

    /**
     * Don't use this, it's just for NULL_TYPE
     */
    TypeRef() {}

    /**
     * Gets the PsiClass this ref represents. If the smartpointer is obsolete (or if this TypeRef wasn't created with a PsiClass instance)
     * it will look it up by name.
     */
    @Nullable
    PsiClass dereference() {
      PsiClass result = myPointer == null ? null : myPointer.getElement();
      if (result == null) {
        NavigationSchema.this.myTypeCache.remove(myClassName);
        result = NavigationSchema.this.getClass(myClassName);
        if (result != null) {
          myPointer = SmartPointerManager.getInstance(result.getProject()).createSmartPsiElementPointer(result);
        }
      }
      return result;
    }

    /**
     * TypeRefs are hashed and compared by type name only.
     */
    @Override
    public int hashCode() {
      return myClassName == null ? 0 : myClassName.hashCode();
    }

    /**
     * TypeRefs are hashed and compared by type name only.
     */
    @Override
    public boolean equals(Object obj) {
      return obj instanceof TypeRef && Objects.equals(((TypeRef)obj).myClassName, myClassName);
    }
  }

  /**
   * Marker TypeRef for the null value case (e.g. for maps that don't support null values).
   */
  private final TypeRef NULL_TYPE = new TypeRef();

  /**
   * Cache of class names to (in effect) PsiClasses for our frequently-used types.
   */
  private final Map<String, TypeRef> myTypeCache = new HashMap<>();

  /**
   * Get (from our cache if possible) the PsiClass for the given className.
   */
  @Nullable
  private PsiClass getClass(@Nullable String className) {
    if (className == null) {
      return null;
    }
    TypeRef ref = myTypeCache.get(className);
    if (ref != null) {
      PsiClass c = ref.dereference();
      if (c != null) {
        return c;
      }
    }

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(myModule.getProject());
    GlobalSearchScope scope = ProjectSystemUtil.getModuleSystem(myModule).getResolveScope(ScopeType.MAIN);
    PsiClass result = javaPsiFacade.findClass(className, scope);
    if (result != null) {
      myTypeCache.put(className, new TypeRef(result));
    }
    else {
      myTypeCache.remove(className);
    }
    return result;
  }

  private class NavigatorKeyInfo {
    long myModificationCount;
    @NotNull TypeRef myNavigatorTypeRef;
    @Nullable String myTagName;
    @Nullable TypeRef myDestinationClassRef;

    private NavigatorKeyInfo(@NotNull PsiClass navigator, @Nullable String tag, @Nullable PsiClass destinationClass) {
      myModificationCount = getModificationCount(navigator);
      myNavigatorTypeRef = new TypeRef(navigator);
      myTagName = tag;
      myDestinationClassRef = destinationClass == null ? NULL_TYPE : new TypeRef(destinationClass);
    }

    boolean checkConsistent(@NotNull NavigationSchema schema) {
      PsiClass otherClass = myNavigatorTypeRef.dereference();
      if (otherClass == null) {
        return false;
      }

      if (getModificationCount(otherClass) == myModificationCount) {
        return true;
      }

      String tag = getTagAnnotationValue(otherClass);
      if (!Objects.equals(myTagName, tag)) {
        return false;
      }
      PsiClass rootNavigator = schema.getClass(NAVIGATOR_CLASS_NAME);
      if (rootNavigator == null) {
        // shouldn't happen
        return false;
      }
      PsiClass otherDestination = null;
      PsiClass otherOrParent = otherClass;
      while (otherDestination == null && otherOrParent != null && otherOrParent.isInheritor(rootNavigator, true)) {
        otherDestination = getDestinationClassAnnotationValue(otherOrParent, rootNavigator);
        otherOrParent = otherOrParent.getSuperClass();
      }
      PsiClass destinationClass = myDestinationClassRef == null ? null : myDestinationClassRef.dereference();

      String destinationName = (destinationClass == null) ? null : destinationClass.getQualifiedName();
      String otherName = (otherDestination == null) ? null : otherDestination.getQualifiedName();

      return Objects.equals(destinationName, otherName);
    }

    private long getModificationCount(@NotNull PsiClass psiClass) {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(psiClass.getProject());
      if (manager == null) {
        return 0;
      }

      PsiFile file = psiClass.getContainingFile();
      if (file == null) {
        return 0;
      }

      Document document = manager.getDocument(file);
      return document == null ? 0 : document.getModificationStamp();
    }
  }

  /**
   * List of class key information. Only used for cache invalidation.
   */
  @NotNull
  private ImmutableList<NavigatorKeyInfo> myNavigatorCacheKeys = ImmutableList.of();

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Instance Data
  /////////////////////////////////////////////////////////////////////////////

  private final Module myModule;

  /**
   * Map from DestinationType (Fragment, activity, navigation, other) to destination class (android.app.Activity etc.).
   * This includes all destination classes that are directly referenced by Navigators in ClassType annotations on their NavDestination
   * subclasses. in the project or libraries (including the basic ones from the navigation library).
   */
  @NotNull
  private ImmutableMultimap<DestinationType, TypeRef> myTypeToDestinationClass = ImmutableMultimap.of();

  /**
   * Map from tag name to destination class. This should include all tags and destination classes
   */
  @NotNull
  private ImmutableMultimap<String, TypeRef> myTagToDestinationClass = ImmutableMultimap.of();

  /**
   * Map from tag name to styleable class (that is, class corresponding to an xml tag with attributes defined in attrs.xml).
   */
  @NotNull
  private ImmutableMultimap<String, TypeRef> myTagToStyleables = ImmutableMultimap.of();

  @Override
  public int hashCode() {
    return Objects.hash(myModule, myTagToDestinationClass, myTagToDestinationClass, myTagToStyleables);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof NavigationSchema)) {
      return false;
    }
    NavigationSchema otherSchema = (NavigationSchema)obj;
    return Objects.equals(myModule, otherSchema.myModule) &&
           Objects.equals(myTypeToDestinationClass, otherSchema.myTypeToDestinationClass) &&
           Objects.equals(myTagToDestinationClass, otherSchema.myTagToDestinationClass) &&
           Objects.equals(myTagToStyleables, otherSchema.myTagToStyleables);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Initialization
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Cache of NavigationSchemas that have been created, per Facet.
   */
  private static final Map<Module, NavigationSchema> ourSchemas = new HashMap<>();

  /**
   * Gets the {@code NavigationSchema} for the given {@code module}. {@link #createIfNecessary(Module)} <b>must</b> be called before
   * this method is called.
   *
   * The returned object should be considered transient and not kept around, since the schema for a Module may be replaced at any time.
   */
  @NotNull
  public static synchronized NavigationSchema get(@NotNull Module module) {
    NavigationSchema result = ourSchemas.get(module);

    // If there is no schema available it may indicate that the module has already been disposed.
    // Return an empty schema that can be accessed but contains no data.
    return result == null ? new NavigationSchema(module) : result;
  }

  /**
   * Creates a {@code NavigationSchema} for the given module. The navigation library must already be included in the project, or this will
   * throw {@code ClassNotFoundException}.
   */
  public static synchronized void createIfNecessary(@NotNull Module module) throws ClassNotFoundException {
    NavigationSchema result = ourSchemas.get(module);
    if (result == null) {
      result = new NavigationSchema(module);
      result.init();
      ourSchemas.put(module, result);
      try {
        Disposer.register(module, result);
      }
      catch (IncorrectOperationException ignore) {
        result.dispose();
      }
    }
  }

  @VisibleForTesting
  public NavigationSchema(@NotNull Module module) {
    myModule = module;
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().invokeLater(() -> ourSchemas.remove(myModule, this));
  }

  /**
   * Initialize the core data of this NavigationSchema (myTypeToDestinationClass and myTagToDestinationClass).
   *
   * @throws ClassNotFoundException if the Navigator root class isn't found.
   *
   * TODO: re-initialize when libraries or Navigator subclasses are added or removed.
   */
  private void init() throws ClassNotFoundException {
    // Get the root Navigator class
    Project project = myModule.getProject();
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope =  ProjectSystemUtil.getModuleSystem(myModule).getResolveScope(ScopeType.MAIN);
    PsiClass navigatorRoot = javaPsiFacade.findClass(NAVIGATOR_CLASS_NAME, scope);
    if (navigatorRoot == null) {
      Logger.getInstance(getClass()).warn("Navigator class not found.");
      throw new ClassNotFoundException(NAVIGATOR_CLASS_NAME);
    }

    PsiClass activity = javaPsiFacade.findClass(SdkConstants.CLASS_ACTIVITY, scope);
    if (activity == null) {
      Logger.getInstance(getClass()).warn("Activity class not found.");
      throw new ClassNotFoundException(SdkConstants.CLASS_ACTIVITY);
    }

    Map<PsiClass, String> navigatorToTag = new HashMap<>();
    Map<PsiClass, PsiClass> navigatorToDestinationClass = new HashMap<>();

    // This one currently has to be added manually no matter what, since there's no destination type for subnavs per se
    navigatorToDestinationClass.put(getClass(ROOT_NAV_GRAPH_NAVIGATOR), getClass(NAV_GRAPH_DESTINATION));

    Set<PsiClass> nonCustomDestinations = new HashSet<>();
    Set<String> nonCustomTags = new HashSet<>();

    // Now we iterate over all the navigators and collect the destinations and tags.
    for (PsiClass navClass : ClassInheritorsSearch.search(navigatorRoot, scope, true)) {
      if (navClass.equals(navigatorRoot)) {
        // Don't keep the root navigator
        continue;
      }
      String qName = navClass.getQualifiedName();
      if (qName != null) {
        if (qName.startsWith(ANDROIDX_PKG_PREFIX)) {
          if (!navClass.hasModifier(JvmModifier.PUBLIC)) {
            continue;
          }
        }
        else {
          // Metrics
          myCustomNavigatorCount++;
        }
      }
      collectDestinationsForNavigator(navigatorRoot, navClass, navigatorToDestinationClass);
      collectTagsForNavigator(navClass, navigatorToTag);
      if (qName != null && qName.startsWith(ANDROIDX_PKG_PREFIX)) {
        nonCustomDestinations.add(navigatorToDestinationClass.get(navClass));
        nonCustomTags.add(navigatorToTag.get(navClass));
      }
    }
    myTagToStyleables = buildTagToStyleables(navigatorToTag);
    // Match up the tags and destinations
    myTagToDestinationClass = buildTagToDestinationMap(navigatorToTag, navigatorToDestinationClass);
    // Build the type map, mostly based on hardcoded correspondences between type and destination class.
    myTypeToDestinationClass = buildDestinationTypeToDestinationMap();
    // Metrics
    myCustomDestinationCount = new HashSet<>(myTagToDestinationClass.values()).size() - nonCustomDestinations.size();
    myCustomTagCount = myTagToDestinationClass.keySet().size() - nonCustomTags.size();

    myTypeToRootTag = buildTypeToDefaultTag(navigatorToTag);
    myNavigatorCacheKeys = buildCacheKeys(navigatorToTag, navigatorToDestinationClass);
  }

  private ImmutableList<NavigatorKeyInfo> buildCacheKeys(Map<PsiClass, String> tagMap, Map<PsiClass, PsiClass> destinationTypeMap) {
    ImmutableList.Builder<NavigatorKeyInfo> result = new ImmutableList.Builder<>();
    for (PsiClass navigator : Sets.union(tagMap.keySet(), destinationTypeMap.keySet())) {
      NavigatorKeyInfo key = new NavigatorKeyInfo(navigator, tagMap.get(navigator), destinationTypeMap.get(navigator));
      result.add(key);
    }
    return result.build();
  }

  /**
   * Builds a map from tag to Styleables (in this case, the Navigators that correspond to those tags).
   */
  private ImmutableMultimap<String, TypeRef> buildTagToStyleables(Map<PsiClass, String> navigatorToTag) {
    ImmutableMultimap.Builder<String, TypeRef> builder = ImmutableMultimap.builder();
    navigatorToTag.forEach((navigator, tag) -> builder.put(tag, new TypeRef(navigator)));
    return builder.build();
  }

  /**
   * Builds the map of DestinationType to destination class for all the destination classes referenced in Navigators, simply by walking
   * up the class hierarchy for destination classes until one of the root types (android.app.Activity, or the androidx or non-androidx
   * support Fragment) is reached, or not.
   */
  @NotNull
  private ImmutableMultimap<DestinationType, TypeRef> buildDestinationTypeToDestinationMap() {
    // Build a map of qualified class names to destination types.
    // Use the qualified name instead of the class since we might get different instances
    // for the same PsiClass, possibly if a library module has a dependency on the same class
    Map<String, DestinationType> destinationClassToType = new HashMap<>();

    destinationClassToType.put(SdkConstants.CLASS_ACTIVITY, ACTIVITY);
    destinationClassToType.put(AndroidXConstants.CLASS_V4_FRAGMENT.oldName(), FRAGMENT);
    destinationClassToType.put(AndroidXConstants.CLASS_V4_FRAGMENT.newName(), FRAGMENT);
    destinationClassToType.put(NAV_GRAPH_DESTINATION, NAVIGATION);

    for (TypeRef destinationClassRef : myTagToDestinationClass.values()) {
      if (destinationClassRef == NULL_TYPE) {
        continue;
      }
      PsiClass destinationClass = destinationClassRef.dereference();
      if (destinationClass == null) {
        // At this point this shouldn't happen, since the reference was just created above.
        continue;
      }
      List<PsiClass> toUpdate = new ArrayList<>();
      DestinationType result = OTHER;
      while (destinationClass != null) {
        toUpdate.add(destinationClass);
        DestinationType t = destinationClassToType.get(destinationClass.getQualifiedName());
        if (t != null) {
          result = t;
          break;
        }
        destinationClass = destinationClass.getSuperClass();
      }
      for (PsiClass d : toUpdate) {
        destinationClassToType.put(d.getQualifiedName(), result);
      }
    }
    ImmutableMultimap.Builder<DestinationType, TypeRef> typeToDestinationBuilder = ImmutableMultimap.builder();
    destinationClassToType.forEach((destination, type) -> typeToDestinationBuilder.put(type, new TypeRef(destination)));
    typeToDestinationBuilder.put(OTHER, NULL_TYPE);
    return typeToDestinationBuilder.build();
  }

  /**
   * Join the navigator-to-tag map and the navigator-to-destination map. All tags will be in the result map, but only destinations with
   * associated tags will be included. Tags with no associated destination (that don't extend OOTB Navigators or don't have a type specified
   * via the (optional) NavDestination.ClassType annotation) will be mapped to NULL_TYPE.
   */
  @NotNull
  private ImmutableMultimap<String, TypeRef> buildTagToDestinationMap(@NotNull Map<PsiClass, String> navigatorToTag,
                                                                      @NotNull Map<PsiClass, PsiClass> navigatorToDestinationClass) {
    ImmutableMultimap.Builder<String, TypeRef> tagToDestBuilder = new ImmutableMultimap.Builder<>();
    for (Map.Entry<PsiClass, PsiClass> navToDestEntry : navigatorToDestinationClass.entrySet()) {
      String tag = navigatorToTag.get(navToDestEntry.getKey());
      if (tag != null) {
        PsiClass destClass = navToDestEntry.getValue();
        tagToDestBuilder.put(tag, destClass == null ? NULL_TYPE : new TypeRef(destClass));
      }
    }
    return tagToDestBuilder.build();
  }

  /**
   * A Navigator has a tag defined as an annotation on the Navigator class itself or one of its superclasses. This method updates
   * navigatorToTagCollector with the mapping for the given navClass, as well as any of its superclasses that do not currently have a
   * mapping defined.
   */
  private static void collectTagsForNavigator(@NotNull PsiClass navClass, @NotNull Map<PsiClass, String> navigatorToTagCollector) {
    PsiClass tempNavigator = navClass;
    List<PsiClass> childrenToUpdate = new ArrayList<>();

    String result;
    do {
      childrenToUpdate.add(tempNavigator);
      result = navigatorToTagCollector.get(tempNavigator);
      if (result != null) {
        break;
      }
      result = getTagAnnotationValue(tempNavigator);
      if (result != null) {
        break;
      }
      tempNavigator = tempNavigator.getSuperClass();
    } while (tempNavigator != null && !NAVIGATOR_CLASS_NAME.equals(tempNavigator.getQualifiedName()));

    if (result != null) {
      for (PsiClass navigator : childrenToUpdate) {
        navigatorToTagCollector.put(navigator, result);
      }
    }
  }

  /**
   * A Navigator can have a specific destination class defined by an annotation on its NavDestination type parameter, which can be inherited
   * from the parent NavDestination type if not defined.
   * This method updates navigatorToDestinationClassCollector with the mapping for the given navClass.
   */
  private static void collectDestinationsForNavigator(@NotNull PsiClass navigatorRoot,
                                                      @NotNull PsiClass navClass,
                                                      @NotNull Map<PsiClass, PsiClass> navigatorToDestinationClassCollector) {
    PsiClass resultDestination = navigatorToDestinationClassCollector.get(navClass);
    if (resultDestination == null) {
      resultDestination = getDestinationClassAnnotationValue(navClass, navigatorRoot);
    }

    // TODO: remove once the base classes are properly annotated
    if (resultDestination == null) {
      PsiClass parentClass = navClass.getSuperClass();
      while (parentClass != null && resultDestination == null) {
        resultDestination = navigatorToDestinationClassCollector.get(parentClass);
        parentClass = parentClass.getSuperClass();
      }
    }
    // end TODO

    navigatorToDestinationClassCollector.put(navClass, resultDestination);

  }

  /**
   * Finds the class referenced by the ClassType annotation on the navigator's type parameter (inherited from the type parameter's
   * superclass if necessary).
   */
  @Nullable
  private static PsiClass getDestinationClassAnnotationValue(@NotNull PsiClass subNav,
                                                             @NotNull PsiClass navigatorRoot) {
    PsiTypeParameter destinationTypeParam = navigatorRoot.getTypeParameters()[0];
    PsiType resolved =
      TypeConversionUtil.getSuperClassSubstitutor(navigatorRoot, PsiTypesUtil.getClassType(subNav)).substitute(destinationTypeParam);
    if (resolved == null) {
      return null;
    }
    List<PsiType> resolvedWithSupers = new ArrayList<>(Arrays.asList(resolved.getSuperTypes()));
    resolvedWithSupers.add(0, resolved);

    for (PsiType t : resolvedWithSupers) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(PsiTypesUtil.getPsiClass(t), ANNOTATION_NAV_CLASS_TYPE);
      if (annotation != null) {
        PsiAnnotationMemberValue expression = annotation.findAttributeValue("value");
        if (!(expression instanceof PsiClassObjectAccessExpression)) {
          return null;
        }
        return PsiTypesUtil.getPsiClass(((PsiClassObjectAccessExpression)expression).getOperand().getType());
      }
    }
    return null;
  }

  /**
   * Gets the tag name from the Name annotation on the given Navigator subclass.
   */
  @Nullable
  private static String getTagAnnotationValue(@NotNull PsiClass subNav) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(subNav, ANNOTATION_NAV_TAG_NAME);
    return annotation == null ? null : AnnotationUtil.getStringAttributeValue(annotation, "value");
  }

  /**
   * Builds a map from DestinationType to the default tag to be used for that type.
   */
  @NotNull
  private ImmutableMap<DestinationType, String> buildTypeToDefaultTag(Map<PsiClass, String> navigatorToTag) {
    ImmutableMap.Builder<DestinationType, String> builder = ImmutableMap.builder();

    addDefaultTag(FRAGMENT, ROOT_FRAGMENT_NAVIGATOR, navigatorToTag, builder);
    addDefaultTag(ACTIVITY, ROOT_ACTIVITY_NAVIGATOR, navigatorToTag, builder);
    addDefaultTag(NAVIGATION, ROOT_NAV_GRAPH_NAVIGATOR, navigatorToTag, builder);

    return builder.build();
  }

  /**
   * Adds the default tag to the map, if the tag exists.
   */
  private void addDefaultTag(DestinationType destinationType,
                             String className,
                             Map<PsiClass, String> navigatorToTag,
                             ImmutableMap.Builder<DestinationType, String> builder) {
    PsiClass psiClass = getClass(className);
    if (psiClass == null) {
      return;
    }

    String tag = navigatorToTag.get(psiClass);
    if (tag != null) {
      builder.put(destinationType, tag);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Cache Invalidation
  /////////////////////////////////////////////////////////////////////////////

  private static final Multimap<Module, Runnable> ourListeners = ArrayListMultimap.create();
  private static final Object ourListenerLock = new Object();

  private CompletableFuture<NavigationSchema> myRebuildTask;
  private final Object myTaskLock = new Object();

  @Nullable
  public CompletableFuture<NavigationSchema> getRebuildTask() {
    synchronized (myTaskLock) {
      return myRebuildTask;
    }
  }

  public boolean quickValidate() {
    synchronized (myTaskLock) {
      if (myRebuildTask != null) {
        return false;
      }

      return myNavigatorCacheKeys.stream().allMatch(value -> value.checkConsistent(this));
    }
  }

  @NotNull
  public CompletableFuture<NavigationSchema> rebuildSchema() {
    if (myModule.isDisposed()) {
      return CompletableFuture.completedFuture(new NavigationSchema(myModule));
    }

    CompletableFuture<NavigationSchema> task;
    synchronized (myTaskLock) {
      if (myRebuildTask != null) {
        return myRebuildTask;
      }

      myRebuildTask = new CompletableFuture<>();
      task = myRebuildTask;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      NavigationSchema newVersion = new NavigationSchema(myModule);
      DumbService.getInstance(myModule.getProject()).runReadActionInSmartMode(() -> {
        try {
          newVersion.init();
        }
        catch (Throwable t) {
          synchronized (myTaskLock) {
            myRebuildTask.completeExceptionally(t);
            myRebuildTask = null;
          }
        }
      });
      if (myRebuildTask == null) {
        // there was an error during init
        return;
      }

      if (equals(newVersion)) {
        synchronized (myTaskLock) {
          myRebuildTask.complete(this);
          myRebuildTask = null;
        }
        return;
      }
      ourSchemas.put(myModule, newVersion);

      boolean registered = false;
      try {
        Disposer.register(myModule, newVersion);
        registered = true;
      }
      catch (IncorrectOperationException ignore) {
        newVersion.dispose();
      }
      synchronized (myTaskLock) {
        myRebuildTask.complete(registered ? newVersion : new NavigationSchema(myModule));
      }

      List<Runnable> listeners;
      synchronized (ourListenerLock) {
        listeners = new ArrayList<>(ourListeners.get(myModule));
      }
      listeners.forEach(Runnable::run);
      Disposer.dispose(this);
    });
    return task;
  }

  /**
   * Add a listener that will be run on a worker thread when schema rebuild is complete.
   * The listener will also automatically be propagated to the new NavigationSchema object.
   */
  public static void addSchemaRebuildListener(@NotNull Module module, @NotNull Runnable listener) {
    synchronized (ourListenerLock) {
      ourListeners.put(module, listener);
    }
  }

  public static void removeSchemaRebuildListener(@NotNull Module module, Runnable listener) {
    synchronized (ourListenerLock) {
      ourListeners.remove(module, listener);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region XML Tag Schema
  /////////////////////////////////////////////////////////////////////////////

  /**
   * For the given {@code tagName}, gets a map from {@link AndroidDomElement} class to all subtags names of that type.
   * Note that if multiple custom navigators define the same tag for different destination types this method will return the union
   * of the valid subtags for all destination types.
   * For example, if we have a custom navigator with destinations of type Activity and one with destinations of type Fragment and both have
   * tag "foo" we'll return the subtags for both fragments and activities here.
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
    Multimap<Class<? extends AndroidDomElement>, String> result = HashMultimap.create();
    for (DestinationType type : getDestinationTypesForTag(tagName)) {
      if (type == null || isIncludeTag(tagName)) {
        continue;
      }
      if (type == NAVIGATION) {
        myTypeToDestinationClass.forEach((childType, childDestinationClass) -> {
          for (String tag : myTagToDestinationClass.inverse().get(childDestinationClass)) {
            if (childType == NAVIGATION) {
              result.put(NavGraphElement.class, tag);
            } else if (childType == FRAGMENT) {
              result.put(FragmentDestinationElement.class, tag);
            } else if (childType == ACTIVITY) {
              result.put(ActivityDestinationElement.class, tag);
            } else {
              result.put(ConcreteDestinationElement.class, tag);
            }
          }
        });
        result.put(NavGraphElement.class, TAG_INCLUDE);
      }
      if (type != ACTIVITY) {
        result.put(NavActionElement.class, TAG_ACTION);
      }
      result.put(DeeplinkElement.class, TAG_DEEP_LINK);
      result.put(NavArgumentElement.class, TAG_ARGUMENT);
    }
    return result;
  }

  /**
   * Gets the tags that can be used as the root tag of a navigation file based on the NavigationSchema for the given facet, and returning
   * a sensible default if the schema isn't available.
   */
  @NotNull
  public static List<String> getPossibleRootsMaybeWithoutSchema(@NotNull Module module) {
    Application application = ApplicationManager.getApplication();
    AtomicReference<List<String>> result = new AtomicReference<>();

    application.invokeAndWait(() -> application.runReadAction(() -> {
      try {
        createIfNecessary(module);
        result.set(get(module).getPossibleRoots());
      }
      catch (ClassNotFoundException e) {
        // Navigation wasn't initialized yet, fall back to default
        result.set(ImmutableList.of(NavigationDomFileDescription.DEFAULT_ROOT_TAG));
      }
    }));
    return result.get();
  }

  /**
   * Gets the tags that can be used as the root of a navigation graph--the tags that map to a destination of type NAVIGATION.
   */
  @NotNull
  public List<String> getPossibleRoots() {
    return myTypeToDestinationClass.get(NAVIGATION).stream()
                                   .flatMap(c -> myTagToDestinationClass.inverse().get(c).stream())
                                   .collect(Collectors.toList());
  }

  @NotNull
  public Collection<PsiClass> getStyleablesForTag(@NotNull String tag) {
    return ContainerUtil.map(myTagToStyleables.get(tag), TypeRef::dereference);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Schema Mappings
  /////////////////////////////////////////////////////////////////////////////

  // This section should contain methods to map between only tag, destination class, and destination type.

  /**
   * Gets all the tags known in this NavigationSchema.
   */
  @NotNull
  public Collection<String> getAllTags() {
    return myTagToDestinationClass.keySet();
  }

  /**
   * Gets the possible DestinationTypes for a given tag.
   * In normal situations there should only be one possible DestinationType for a tag, but it's possible to define multiple Navigators
   * with the same tag and different destination classes which have different types.
   */
  @NotNull
  public Collection<DestinationType> getDestinationTypesForTag(@NotNull String tag) {
    if (tag.equals(TAG_INCLUDE)) {
      return ImmutableList.of(NAVIGATION);
    }
    Set<DestinationType> result = EnumSet.noneOf(DestinationType.class);
    for (TypeRef destinationClassRef : myTagToDestinationClass.get(tag)) {
      if (destinationClassRef == NULL_TYPE) {
        result.add(OTHER);
      }
      else {
        result.addAll(myTypeToDestinationClass.inverse().get(destinationClassRef));
      }
    }
    return result;
  }

  /**
   * Gets the DestinationType for the given destination class. This is determined by the superclass hierarchy of the destination class.
   */
  @Nullable
  public DestinationType getDestinationTypeForDestinationClassName(@NotNull String className) {
    ImmutableCollection<DestinationType> types = myTypeToDestinationClass.inverse().get(new TypeRef(className));
    return types.isEmpty() ? null : types.iterator().next();
  }


  /**
   * Gets the possible tags for a given destination class. There can be multiple if multiple Navigators target the same destination class.
   *
   * TODO: determine if tags specified anywhere in the supertype hierarchy should be included.
   */
  @Contract("null -> null")
  @Nullable
  public Collection<String> getTagsForDestinationClass(@Nullable PsiClass layoutClass) {
    while (layoutClass != null) {
      Collection<String> result = myTagToDestinationClass.inverse().get(new TypeRef(layoutClass));
      if (!result.isEmpty()) {
        return result;
      }
      layoutClass = layoutClass.getSuperClass();
    }
    return null;
  }

  /**
   * Gets the possible destination classes for a given tag. There can be multiple if multiple Navigators use the same tag name.
   */
  @NotNull
  public Collection<PsiClass> getDestinationClassesForTag(@NotNull String tagName) {
    return myTagToDestinationClass.get(tagName).stream()
                                               .map(TypeRef::dereference)
                                               .filter(c -> c != null)
                                               .collect(Collectors.toSet());
  }

  /**
   * Gets the set of classes from this project that can be used as destinations for the given tag
   * This includes destinations for the given tag that are defined in this project, and subclasses
   * of those destinations found in this project and its dependencies.
   * Classes derived from NavHostFragment will not be included.
   */
  @NotNull
  public List<PsiClass> getProjectClassesForTag(@NotNull String tagName, @NotNull SearchScope scope) {
    Collection<PsiClass> destinationClasses = getDestinationClassesForTag(tagName);
    List<PsiClass> projectClasses = new ArrayList<>();

    for (PsiClass destinationClass : destinationClasses) {
      if (NavClassHelperKt.isInProject(destinationClass)) {
        projectClasses.add(destinationClass);
      }

      Query<PsiClass> query = ClassInheritorsSearch.search(destinationClass, scope, true, true, false);
      for (PsiClass inherited : query) {
        if (!NavClassHelperKt.extendsNavHostFragment(inherited, myModule)) {
          projectClasses.add(inherited);
        }
      }
    }

    return projectClasses;
  }

  @NotNull
  public List<PsiClass> getProjectClassesForTag(@NotNull String tagName) {
    return getProjectClassesForTag(tagName, GlobalSearchScope.moduleWithDependenciesScope(myModule));
  }

  /**
   * Gets the tag to use by default for the given DestinationType. Right now this is just a static mapping to the OOTB tags
   */
  @Nullable
  public String getDefaultTag(@NotNull DestinationType type) {
    return myTypeToRootTag.get(type);
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
      Collection<NavigationSchema.DestinationType> types = getDestinationTypesForTag(tag);
      if (types.size() > 1) {
        text = "Ambiguous Type";
      }
      else if (types.contains(NAVIGATION)) {
        text = isRoot ? "Root Graph" : "Nested Graph";
      }
      else if (types.contains(FRAGMENT)) {
        text = "Fragment";
      }
      else if (types.contains(ACTIVITY)) {
        text = "Activity";
      }
      else if (types.contains(OTHER)) {
        text = tag;
      }

      if (types.stream().map(this::getDefaultTag).noneMatch(t -> t == null || t.equals(tag))) {
        // If it's a custom tag, show it. Don't show "other" type tags, since we already show that as the main text.
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
    return getDestinationTypesForTag(tag).contains(FRAGMENT);
  }

  public Boolean isActivityTag(@NotNull String tag) {
    return getDestinationTypesForTag(tag).contains(ACTIVITY);
  }

  public Boolean isNavigationTag(@NotNull String tag) {
    return getDestinationTypesForTag(tag).contains(NAVIGATION);
  }

  public Boolean isOtherTag(@NotNull String tag) {
    return getDestinationTypesForTag(tag).contains(OTHER);
  }

  public Boolean isIncludeTag(@NotNull String tag) {
    return tag.equals(TAG_INCLUDE);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Metrics
  /////////////////////////////////////////////////////////////////////////////

  private int myCustomNavigatorCount;
  private int myCustomTagCount;
  private int myCustomDestinationCount;

  public int getCustomNavigatorCount() {
    return myCustomNavigatorCount;
  }

  public int getCustomTagCount() {
    return myCustomTagCount;
  }

  public int getCustomDestinationCount() {
    return myCustomDestinationCount;
  }

  //endregion
}
