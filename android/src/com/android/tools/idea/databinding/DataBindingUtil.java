/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.databinding;


import com.android.SdkConstants;
import com.android.ide.common.res2.DataBindingResourceType;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.rendering.DataBindingInfo;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.PsiDataBindingResourceItem;
import com.google.common.collect.Maps;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class that handles the interaction between Data Binding and the IDE.
 * <p/>
 * This class handles adding class finders and short names caches for DataBinding related code
 * completion etc.
 */
public class DataBindingUtil {
  public static final String BR = "BR";
  private static int HAS_DATA_BINDING_UNKNOWN = 0;
  private static int HAS_DATA_BINDING_YES = 1;
  private static int HAS_DATA_BINDING_NO = 2;
  private static final Key<Integer> KEY_HAS_DATA_BINDING = Key.create("has-data-binding");
  private static final Key<List<PsiElementFinder>> KEY_CLASS_FINDERS = Key.create("class-finders");
  private static final Key<List<PsiShortNamesCache>> KEY_SHORT_NAME_CACHES = Key.create("short-name-caches");
  private static final Key<LightBrClass> KEY_BR_CLASS = Key.create("br-class");

  /**
   * Initializes necessary callbacks and information for the provided Facet.
   * Called by the AndroidFacet on initialization.
   */
  public static void initFor(final AndroidFacet facet) {
    facet.putUserData(KEY_HAS_DATA_BINDING, HAS_DATA_BINDING_UNKNOWN);
  }

  /**
   * Package private class used by BR class finder and BR short names cache to create a BR file on demand.
   *
   * @param facet The facet for which the BR file is necessary.
   * @return The LightBRClass that belongs to the given AndroidFacet
   */
  static LightBrClass getOrCreateBrClassFor(AndroidFacet facet) {
    LightBrClass existing = facet.getUserData(KEY_BR_CLASS);
    if (existing == null) {
      synchronized (facet) {
        existing = facet.getUserData(KEY_BR_CLASS);
        if (existing == null) {
          existing = new LightBrClass(PsiManager.getInstance(facet.getModule().getProject()), facet);
          facet.putUserData(KEY_BR_CLASS, existing);
        }
      }
    }
    return existing;
  }

  private static void handleGradleSyncResult(Project project, AndroidFacet facet) {
    if (project == null) {
      return;
    }
    synchronized (facet) {
      int hadDataBinding = safeGetHasDataBindingStatus(facet);
      int hasDataBinding = resolveHasDataBinding(facet);
      if (hadDataBinding == hasDataBinding) {
        return;
      }
      if (hasDataBinding == HAS_DATA_BINDING_YES) {
        enableDataBinding(project, facet);
      }
      else if (hasDataBinding == HAS_DATA_BINDING_NO) {
        disableDataBinding(project, facet);
      }
      facet.putUserData(KEY_HAS_DATA_BINDING, hasDataBinding);
    }
  }

  private static void disableDataBinding(Project project, AndroidFacet facet) {
    List<PsiShortNamesCache> shortNamesCaches = facet.getUserData(KEY_SHORT_NAME_CACHES);
    if (shortNamesCaches != null) {
      ExtensionPoint<PsiShortNamesCache> shortNamesExtPoint = Extensions.getArea(project).getExtensionPoint(PsiShortNamesCache.EP_NAME);
      for (PsiShortNamesCache cache : shortNamesCaches) {
        shortNamesExtPoint.unregisterExtension(cache);
      }
      facet.putUserData(KEY_SHORT_NAME_CACHES, null);
    }
    List<PsiElementFinder> elementFinders = facet.getUserData(KEY_CLASS_FINDERS);
    if (elementFinders != null) {
      ExtensionPoint<PsiElementFinder> extensionPoint = Extensions.getArea(project).getExtensionPoint(PsiElementFinder.EP_NAME);
      for (PsiElementFinder finder : elementFinders) {
        extensionPoint.unregisterExtension(finder);
      }
      facet.putUserData(KEY_CLASS_FINDERS, null);
    }
  }

  private static void enableDataBinding(Project project, AndroidFacet facet) {
    ExtensionPoint<PsiElementFinder> extensionPoint = Extensions.getArea(project).getExtensionPoint(PsiElementFinder.EP_NAME);
    DataBindingClassFinder dataBindingClassFinder = new DataBindingClassFinder(facet);
    extensionPoint.registerExtension(dataBindingClassFinder);
    BrClassFinder brClassFinder = new BrClassFinder(facet);
    extensionPoint.registerExtension(brClassFinder);
    facet.putUserData(KEY_CLASS_FINDERS, Arrays.asList(dataBindingClassFinder, brClassFinder));

    ExtensionPoint<PsiShortNamesCache> shortNamesExtPoint = Extensions.getArea(project).getExtensionPoint(PsiShortNamesCache.EP_NAME);
    PsiShortNamesCache bindingShortNamesCache = new DataBindingShortNamesCache(facet);
    shortNamesExtPoint.registerExtension(bindingShortNamesCache);
    BrShortNamesCache brShortNamesCache = new BrShortNamesCache(facet);
    shortNamesExtPoint.registerExtension(brShortNamesCache);
    facet.putUserData(KEY_SHORT_NAME_CACHES, Arrays.asList(bindingShortNamesCache, brShortNamesCache));
  }

  private static int resolveHasDataBinding(AndroidFacet facet) {
    if (!facet.isGradleProject()) {
      return HAS_DATA_BINDING_NO;
    }
    if (facet.getIdeaAndroidProject() == null) {
      return HAS_DATA_BINDING_NO;
    }
    // TODO check plugin instead
    boolean hasDependency = GradleUtil.dependsOn(facet.getIdeaAndroidProject(), SdkConstants.DATA_BINDING_LIB_ARTIFACT);
    return hasDependency ? HAS_DATA_BINDING_YES : HAS_DATA_BINDING_NO;
  }

  private static int safeGetHasDataBindingStatus(AndroidFacet facet) {
    Integer value = facet.getUserData(KEY_HAS_DATA_BINDING);
    if (value == null) {
      return HAS_DATA_BINDING_UNKNOWN;
    }
    return value;
  }

  static PsiClass getOrCreatePsiClass(AndroidFacet facet, DataBindingInfo info) {
    if (info.getPsiClass() == null) {
      synchronized (info) {
        info.setPsiClass(new LightBindingClass(facet, PsiManager.getInstance(info.getProject()), info));
      }
    }
    return info.getPsiClass();
  }

  /**
   * Utility method that implements Data Binding's logic to convert a file name to a Java Class name
   *
   * @param name The name of the file
   * @return The class name that will represent the given file
   */
  public static String convertToJavaClassName(String name) {
    int dotIndex = name.indexOf('.');
    if (dotIndex >= 0) {
      name = name.substring(0, dotIndex);
    }

    String[] split = name.split("[_-]");
    StringBuilder out = new StringBuilder();
    for (String section : split) {
      out.append(StringUtil.capitalize(section));
    }
    return out.toString();
  }

  /**
   * Returns whether the given facet has data binding enabled or not.
   *
   * @param facet The facet to check.
   * @return True if the given facet depends on data binding library, false otherwise.
   */
  public static boolean isDataBindingEnabled(AndroidFacet facet) {
    return safeGetHasDataBindingStatus(facet) == HAS_DATA_BINDING_YES;
  }

  /**
   * Returns whether the given facet has data binding enabled or we don't yet know the value.
   *
   * @param facet The facet to check.
   * @return True if the given facet depends on data binding or it is not synced yet, false otherwise.
   */
  public static boolean isDataBindingEnabledOrUnknown(AndroidFacet facet) {
    return safeGetHasDataBindingStatus(facet) != HAS_DATA_BINDING_NO;
  }

  /**
   * Utility method to convert a variable name into java field name.
   *
   * @param name The variable name.
   * @return The java field name for the given variable name.
   */
  public static String convertToJavaFieldName(String name) {
    int dotIndex = name.indexOf('.');
    if (dotIndex >= 0) {
      name = name.substring(0, dotIndex);
    }

    String[] split = name.split("[_-]");
    StringBuilder out = new StringBuilder();
    boolean first = true;
    for (String section : split) {
      if (first) {
        first = false;
        out.append(section);
      }
      else {
        out.append(StringUtil.capitalize(section));
      }
    }
    return out.toString();
  }

  /**
   * Returns the qualified name for the BR file for the given Facet.
   *
   * @param facet The {@linkplain AndroidFacet} to check.
   * @return The qualified name for the BR class of the given Android Facet.
   */
  public static String getBrQualifiedName(AndroidFacet facet) {
    return getGeneratedPackageName(facet) + "." + BR;
  }

  /**
   * Returns the package name that will be use to generate R file or BR file.
   *
   * @param facet The {@linkplain AndroidFacet} to check.
   * @return The package name that can be used to generate R and BR classes.
   */
  public static String getGeneratedPackageName(AndroidFacet facet) {
    return ManifestInfo.get(facet.getModule(), false).getPackage();
  }

  /**
   * Called by the {@linkplain AndroidFacet} to refresh its data binding status.
   *
   * @param facet the {@linkplain AndroidFacet} whose IdeaProject is just set.
   */
  public static void onIdeaProjectSet(AndroidFacet facet) {
    handleGradleSyncResult(facet.getModule().getProject(), facet);
  }

  /**
   * The light class that represents the generated data binding code for a layout file.
   */
  static class LightBindingClass extends AndroidLightClassBase {
    private static final String BASE_CLASS = "android.databinding.ViewDataBinding";
    static final int STATIC_METHOD_COUNT = 3;
    private DataBindingInfo myInfo;
    private CachedValue<PsiMethod[]> myPsiMethodsCache;
    private CachedValue<PsiField[]> myPsiFieldsCache;
    private PsiReferenceList myExtendsList;
    private PsiClassType[] myExtendsListTypes;

    protected LightBindingClass(AndroidFacet facet, @NotNull PsiManager psiManager, DataBindingInfo info) {
      super(psiManager);
      myInfo = info;
      myPsiMethodsCache =
        CachedValuesManager.getManager(info.getProject()).createCachedValue(new ResourceCacheValueProvider<PsiMethod[]>(facet) {
          @Override
          PsiMethod[] doCompute() {
            List<PsiDataBindingResourceItem> variables = myInfo.getItems(DataBindingResourceType.VARIABLE);
            if (variables == null) {
              return PsiMethod.EMPTY_ARRAY;
            }
            PsiMethod[] methods = new PsiMethod[variables.size() * 2 + STATIC_METHOD_COUNT];
            PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
            for (int i = 0; i < variables.size(); i++) {
              createVariableMethods(factory, variables.get(i), methods, i * 2);
            }
            createStaticMethods(factory, methods, variables.size() * 2);
            return methods;
          }


          @Override
          PsiMethod[] defaultValue() {
            return PsiMethod.EMPTY_ARRAY;
          }
        });

      myPsiFieldsCache =
        CachedValuesManager.getManager(info.getProject()).createCachedValue(new ResourceCacheValueProvider<PsiField[]>(facet) {
          @Override
          PsiField[] doCompute() {
            List<DataBindingInfo.ViewWithId> viewsWithIds = myInfo.getViewsWithIds();
            PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
            PsiField[] result = new PsiField[viewsWithIds.size()];
            int i = 0;
            for (DataBindingInfo.ViewWithId viewWithId : viewsWithIds) {
              result[i++] = createPsiField(factory, viewWithId);
            }
            return result;
          }

          @Override
          PsiField[] defaultValue() {
            return PsiField.EMPTY_ARRAY;
          }
        });
    }

    @Override
    public String toString() {
      return myInfo.getClassName();
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return myInfo.getQualifiedName();
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
      return null;
    }

    @NotNull
    @Override
    public PsiField[] getFields() {
      return myPsiFieldsCache.getValue();
    }

    @NotNull
    @Override
    public PsiField[] getAllFields() {
      return getFields();
    }

    @NotNull
    @Override
    public PsiMethod[] getMethods() {
      return myPsiMethodsCache.getValue();
    }

    @Override
    public PsiClass getSuperClass() {
      return JavaPsiFacade.getInstance(myInfo.getProject())
        .findClass(BASE_CLASS, myInfo.getModule().getModuleWithDependenciesAndLibrariesScope(false));
    }

    @Override
    public PsiReferenceList getExtendsList() {
      if (myExtendsList == null) {
        PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
        PsiJavaCodeReferenceElement referenceElementByType = factory.createReferenceElementByType(getExtendsListTypes()[0]);
        myExtendsList = factory.createReferenceList(new PsiJavaCodeReferenceElement[]{referenceElementByType});
      }
      return myExtendsList;
    }

    @NotNull
    @Override
    public PsiClassType[] getSuperTypes() {
      return getExtendsListTypes();
    }

    @NotNull
    @Override
    public PsiClassType[] getExtendsListTypes() {
      if (myExtendsListTypes == null) {
        myExtendsListTypes = new PsiClassType[]{
          PsiType.getTypeByName(BASE_CLASS, myInfo.getProject(), myInfo.getModule().getModuleWithDependenciesAndLibrariesScope(false))};
      }
      return myExtendsListTypes;
    }


    @NotNull
    @Override
    public PsiMethod[] getAllMethods() {
      return getMethods();
    }

    @NotNull
    @Override
    public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
      for (PsiMethod method : getMethods()) {
        if (name.equals(method.getName())) {
          return new PsiMethod[]{method};
        }
      }
      return PsiMethod.EMPTY_ARRAY;
    }

    private void createVariableMethods(PsiElementFactory factory, PsiDataBindingResourceItem item, PsiMethod[] outPsiMethods, int index) {
      PsiMethod setter = factory.createMethod("set" + StringUtil.capitalize(item.getName()), PsiType.VOID);
      PsiClassType type = PsiType
        .getTypeByName(item.getExtra("type"), myInfo.getProject(), myInfo.getModule().getModuleWithDependenciesAndLibrariesScope(true));
      PsiParameter param = factory.createParameter(item.getName(), type);
      setter.getParameterList().add(param);
      PsiUtil.setModifierProperty(setter, PsiModifier.PUBLIC, true);
      PsiManager psiManager = PsiManager.getInstance(myInfo.getProject());
      final Language javaLang = Language.findLanguageByID("JAVA");
      outPsiMethods[index] = new LightDataBindingMethod(item.getXmlTag(), psiManager, setter, this, javaLang);

      PsiMethod getter = factory.createMethod("get" + StringUtil.capitalize(item.getName()), type);
      PsiUtil.setModifierProperty(getter, PsiModifier.PUBLIC, true);
      outPsiMethods[index + 1] = new LightDataBindingMethod(item.getXmlTag(), psiManager, getter, this, javaLang);
    }

    private void createStaticMethods(PsiElementFactory factory, PsiMethod[] outPsiMethods, int index) {
      PsiClassType myType = factory.createType(this);
      PsiClassType viewGroupType = PsiType
        .getTypeByName(SdkConstants.CLASS_VIEWGROUP, myInfo.getProject(),
                       myInfo.getModule().getModuleWithDependenciesAndLibrariesScope(true));
      PsiClassType layoutInflaterType = PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, myInfo.getProject(),
                                                              myInfo.getModule().getModuleWithDependenciesAndLibrariesScope(true));
      PsiClassType viewType = PsiType
        .getTypeByName(SdkConstants.CLASS_VIEW, myInfo.getProject(), myInfo.getModule().getModuleWithDependenciesAndLibrariesScope(true));
      PsiParameter layoutInflaterParam = factory.createParameter("inflater", layoutInflaterType);
      PsiParameter rootParam = factory.createParameter("root", viewGroupType);
      PsiParameter attachToRootParam = factory.createParameter("attachToRoot", PsiType.BOOLEAN);
      PsiParameter viewParam = factory.createParameter("view", viewType);

      PsiMethod inflate3Arg = factory.createMethod("inflate", myType);
      inflate3Arg.getParameterList().add(layoutInflaterParam);
      inflate3Arg.getParameterList().add(rootParam);
      inflate3Arg.getParameterList().add(attachToRootParam);

      PsiMethod inflate1Arg = factory.createMethod("inflate", myType);
      inflate1Arg.getParameterList().add(layoutInflaterParam);

      PsiMethod bind = factory.createMethod("bind", myType);
      bind.getParameterList().add(viewParam);

      PsiMethod[] methods = new PsiMethod[]{inflate1Arg, inflate3Arg, bind};
      PsiManager psiManager = PsiManager.getInstance(myInfo.getProject());
      for (PsiMethod method : methods) {
        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
        PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
        outPsiMethods[index++] =
          new LightDataBindingMethod(myInfo.getPsiFile(), psiManager, method, this, Language.findLanguageByID("JAVA"));
      }
    }

    private PsiField createPsiField(PsiElementFactory factory, DataBindingInfo.ViewWithId viewWithId) {
      PsiField field = factory.createField(viewWithId.name, PsiType
        .getTypeByName(viewWithId.className, myInfo.getProject(), myInfo.getModule().getModuleWithDependenciesAndLibrariesScope(false)));
      PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      return new LightDataBindingField(viewWithId, PsiManager.getInstance(myInfo.getProject()), field, this);
    }

    @Override
    public boolean isInterface() {
      return false;
    }

    @NotNull
    @Override
    public PsiElement getNavigationElement() {
      return myInfo.getNavigationElement();
    }

    @Override
    public String getName() {
      return myInfo.getClassName();
    }

    @Nullable
    @Override
    public PsiFile getContainingFile() {
      return myInfo.getPsiFile();
    }
  }

  /**
   * The light method class that represents the generated data binding methods for a layout file.
   */
  static class LightDataBindingMethod extends LightMethod {
    private PsiElement myNavigationElement;

    public LightDataBindingMethod(@NotNull PsiElement navigationElement,
                                  @NotNull PsiManager manager,
                                  @NotNull PsiMethod method,
                                  @NotNull PsiClass containingClass,
                                  @NotNull Language language) {
      super(manager, method, containingClass, language);
      myNavigationElement = navigationElement;
    }

    @NotNull
    @Override
    public PsiElement getNavigationElement() {
      return myNavigationElement;
    }

    @Override
    public PsiIdentifier getNameIdentifier() {
      return new LightIdentifier(getManager(), getName());
    }
  }

  /**
   * The light field class that represents the generated view fields for a layout file.
   */
  static class LightDataBindingField extends LightField {
    private final DataBindingInfo.ViewWithId myViewWithId;

    public LightDataBindingField(DataBindingInfo.ViewWithId viewWithId,
                                 @NotNull PsiManager manager,
                                 @NotNull PsiField field,
                                 @NotNull PsiClass containingClass) {
      super(manager, field, containingClass);
      myViewWithId = viewWithId;
    }

    @NotNull
    @Override
    public PsiElement getNavigationElement() {
      return myViewWithId.tag;
    }
  }

  /**
   * The light class that represents a data binding BR file
   */
  static class LightBrClass extends AndroidLightClassBase {
    private static final String BINDABLE_QUALIFIED_NAME = "android.databinding.Bindable";
    private final AndroidFacet myFacet;
    private CachedValue<PsiField[]> myFieldCache;
    @Nullable
    private String[] myCachedFieldNames = new String[]{"_all"};
    private final String myQualifiedName;
    private PsiFile myContainingFile;
    private PsiElement myNavigationElement;

    public LightBrClass(@NotNull PsiManager psiManager, final AndroidFacet facet) {
      super(psiManager);
      myQualifiedName = getBrQualifiedName(facet);
      myFacet = facet;
      myFieldCache =
        CachedValuesManager.getManager(facet.getModule().getProject()).createCachedValue(new ResourceCacheValueProvider<PsiField[]>(facet) {
          @Override
          PsiField[] doCompute() {
            Project project = facet.getModule().getProject();
            PsiElementFactory elementFactory = PsiElementFactory.SERVICE.getInstance(project);
            LocalResourceRepository moduleResources = facet.getModuleResources(false);
            Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
            if (dataBindingResourceFiles == null) {
              return defaultValue();
            }
            Set<String> variableNames = new HashSet<String>();
            for (DataBindingInfo info : dataBindingResourceFiles.values()) {
              for (PsiDataBindingResourceItem item : info.getItems(DataBindingResourceType.VARIABLE)) {
                variableNames.add(item.getName());
              }
            }
            Set<String> bindables = collectVariableNamesFromBindables();
            if (bindables != null) {
              variableNames.addAll(bindables);
            }
            PsiField[] result = new PsiField[variableNames.size() + 1];
            result[0] = createPsiField(project, elementFactory, "_all");
            int i = 1;
            for (String variable : variableNames) {
              result[i++] = createPsiField(project, elementFactory, variable);
            }
            myCachedFieldNames = variableNames.toArray(new String[variableNames.size()]);
            return result;
          }

          @Override
          PsiField[] defaultValue() {
            Project project = facet.getModule().getProject();
            return new PsiField[]{createPsiField(project, PsiElementFactory.SERVICE.getInstance(project), "_all")};
          }

          @Override
          protected Object getAdditionalTracker() {
            return PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT;
          }
        });
    }

    private Set<String> collectVariableNamesFromBindables() {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myFacet.getModule().getProject());
      PsiClass aClass = facade.findClass(BINDABLE_QUALIFIED_NAME, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (aClass == null) {
        return null;
      }
      final Collection<? extends PsiNameIdentifierOwner> psiElements =
        AnnotatedElementsSearch.searchElements(aClass, myFacet.getModule().getModuleScope(), PsiMethod.class, PsiField.class).findAll();
      return BrUtil.collectIds(psiElements);
    }

    private PsiField createPsiField(Project project, PsiElementFactory factory, String id) {
      PsiField field = factory.createField(id, PsiType.INT);
      PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      return new LightBRField(PsiManager.getInstance(project), field, this);
    }

    @Override
    public String toString() {
      return "BR class for " + myFacet;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return myQualifiedName;
    }

    @Override
    public String getName() {
      return BR;
    }

    @NotNull
    public String[] getAllFieldNames() {
      return myCachedFieldNames;
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
      return null;
    }

    @NotNull
    @Override
    public PsiField[] getFields() {
      return myFieldCache.getValue();
    }

    @NotNull
    @Override
    public PsiField[] getAllFields() {
      return getFields();
    }


    @Nullable
    @Override
    public PsiFile getContainingFile() {
      if (myContainingFile == null) {
        // TODO: using R file for now. Would be better if we create a real VirtualFile for this.
        PsiClass aClass = JavaPsiFacade.getInstance(myFacet.getModule().getProject())
          .findClass(getGeneratedPackageName(myFacet) + ".R", myFacet.getModule().getModuleScope());
        if (aClass != null) {
          myContainingFile = aClass.getContainingFile();
        }
      }
      return myContainingFile;
    }

    @Override
    public PsiIdentifier getNameIdentifier() {
      return new LightIdentifier(getManager(), getName());
    }

    @NotNull
    @Override
    public PsiElement getNavigationElement() {
      return getContainingFile();
    }
  }

  /**
   * The light field representing elements of BR class
   */
  static class LightBRField extends LightField {

    public LightBRField(@NotNull PsiManager manager, @NotNull PsiField field, @NotNull PsiClass containingClass) {
      super(manager, field, containingClass);
    }
  }
}
