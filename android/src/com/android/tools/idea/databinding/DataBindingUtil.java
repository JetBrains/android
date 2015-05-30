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
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.rendering.DataBindingInfo;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.PsiDataBindingResourceItem;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class that handles the interaction between Data Binding and the IDE.
 * <p/>
 * This class handles adding class finders and short names caches for DataBinding related code
 * completion etc.
 */
public class DataBindingUtil {
  public static final String BR = "BR";

  private static List<String> VIEW_PACKAGE_ELEMENTS = Arrays.asList(SdkConstants.VIEW, SdkConstants.VIEW_GROUP, SdkConstants.VIEW_STUB,
                                                                    SdkConstants.TEXTURE_VIEW, SdkConstants.SURFACE_VIEW);

  private static AtomicLong ourDataBindingEnabledModificationCount = new AtomicLong(0);

  /**
   * Package private class used by BR class finder and BR short names cache to create a BR file on demand.
   *
   * @param facet The facet for which the BR file is necessary.
   * @return The LightBRClass that belongs to the given AndroidFacet
   */
  static LightBrClass getOrCreateBrClassFor(AndroidFacet facet) {
    LightBrClass existing = facet.getLightBrClass();
    if (existing == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (facet) {
        existing = facet.getLightBrClass();
        if (existing == null) {
          existing = new LightBrClass(PsiManager.getInstance(facet.getModule().getProject()), facet);
          facet.setLightBrClass(existing);
        }
      }
    }
    return existing;
  }

  private static PsiType parsePsiType(String text, AndroidFacet facet) {
    return PsiElementFactory.SERVICE.getInstance(facet.getModule().getProject()).createTypeFromText(text, null);
  }

  private static void handleGradleSyncResult(Project project, AndroidFacet facet) {
    boolean wasEnabled = facet.isDataBindingEnabled();
    boolean enabled = project != null && resolveHasDataBinding(facet);
    if (enabled != wasEnabled) {
      facet.setDataBindingEnabled(enabled);
      ourDataBindingEnabledModificationCount.incrementAndGet();
    }
  }

  public static PsiType resolveViewPsiType(DataBindingInfo.ViewWithId viewWithId, AndroidFacet facet) {
    String viewClassName = getViewClassName(viewWithId.tag, facet);
    if (StringUtil.isNotEmpty(viewClassName)) {
      return parsePsiType(viewClassName, facet);
    }
    return null;
  }
  /**
   * Receives an {@linkplain XmlTag} and returns the View class that is represented by the tag.
   * May return null if it cannot find anything reasonable (e.g. it is a merge but does not have data binding)
   *
   * @param tag The {@linkplain XmlTag} that represents the View
   */
  @Nullable
  private static String getViewClassName(XmlTag tag, AndroidFacet facet) {
    final String elementName = getViewName(tag);
    if (elementName.indexOf('.') == -1) {
      if (VIEW_PACKAGE_ELEMENTS.contains(elementName)) {
        return SdkConstants.VIEW_PKG_PREFIX + elementName;
      } else if (SdkConstants.WEB_VIEW.equals(elementName)) {
        return SdkConstants.ANDROID_WEBKIT_PKG + elementName;
      } else if (SdkConstants.VIEW_MERGE.equals(elementName)) {
        return getViewClassNameFromMerge(tag, facet);
      } else if (SdkConstants.VIEW_INCLUDE.equals(elementName)) {
        return getViewClassNameFromInclude(tag, facet);
      }
      return SdkConstants.WIDGET_PKG_PREFIX + elementName;
    } else {
      return elementName;
    }
  }

  private static String getViewClassNameFromInclude(XmlTag tag, AndroidFacet facet) {
    String reference = getViewClassNameFromLayoutReferenceTag(tag, facet);
    return reference == null ? SdkConstants.CLASS_VIEW : reference;
  }

  private static String getViewClassNameFromMerge(XmlTag tag, AndroidFacet facet) {
    return getViewClassNameFromLayoutReferenceTag(tag, facet);
  }

  private static String getViewClassNameFromLayoutReferenceTag(XmlTag tag, AndroidFacet facet) {
    String layout = tag.getAttributeValue(SdkConstants.ATTR_LAYOUT);
    if (layout == null) {
      return null;
    }
    LocalResourceRepository moduleResources = facet.getModuleResources(false);
    if (moduleResources == null) {
      return null;
    }
    ResourceUrl resourceUrl = ResourceUrl.parse(layout);
    if (resourceUrl == null || resourceUrl.type != ResourceType.LAYOUT) {
      return null;
    }
    DataBindingInfo info = moduleResources.getDataBindingInfoForLayout(resourceUrl.name);
    if (info == null) {
      return null;
    }
    return info.getQualifiedName();
  }

  private static String getViewName(XmlTag tag) {
    String viewName = tag.getName();
    if (SdkConstants.VIEW_TAG.equals(viewName)) {
      viewName = tag.getAttributeValue(SdkConstants.ATTR_CLASS, SdkConstants.ANDROID_URI);
    }
    return viewName;
  }

  private static boolean resolveHasDataBinding(AndroidFacet facet) {
    if (!facet.isGradleProject()) {
      return false;
    }
    if (facet.getIdeaAndroidProject() == null) {
      return false;
    }
    // TODO Instead of checking library dependency, we should be checking whether data binding plugin is
    // applied to this facet or not. Having library dependency does not guarantee data binding
    // unless the plugin is applied as well.
    return GradleUtil.dependsOn(facet.getIdeaAndroidProject(), SdkConstants.DATA_BINDING_LIB_ARTIFACT);
  }

  static PsiClass getOrCreatePsiClass(DataBindingInfo info) {
    PsiClass psiClass = info.getPsiClass();
    if (psiClass == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (info) {
        psiClass = info.getPsiClass();
        if (psiClass == null) {
          psiClass = new LightBindingClass(info.getFacet(), PsiManager.getInstance(info.getProject()), info);
          info.setPsiClass(psiClass);
        }
      }
    }
    return psiClass;
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
    private final AndroidFacet myFacet;

    protected LightBindingClass(AndroidFacet facet, @NotNull PsiManager psiManager, DataBindingInfo info) {
      super(psiManager);
      myInfo = info;
      myFacet = facet;
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
            int unresolved = 0;
            for (DataBindingInfo.ViewWithId viewWithId : viewsWithIds) {
              PsiField psiField = createPsiField(factory, viewWithId);
              if (psiField == null) {
                unresolved ++;
              } else {
                result[i++] = psiField;
              }
            }
            if (unresolved > 0) {
              PsiField[] validResult = new PsiField[i];
              System.arraycopy(result, 0, validResult, 0, i);
              return validResult;
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
        .findClass(BASE_CLASS, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
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
          PsiType.getTypeByName(BASE_CLASS, myInfo.getProject(),
                                myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false))};
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
      PsiType type = parsePsiType(item.getExtra(SdkConstants.ATTR_TYPE), myFacet);
      PsiParameter param = factory.createParameter(item.getName(), type);
      setter.getParameterList().add(param);
      PsiUtil.setModifierProperty(setter, PsiModifier.PUBLIC, true);
      PsiManager psiManager = PsiManager.getInstance(myInfo.getProject());
      final Language javaLang = Language.findLanguageByID("JAVA");
      assert javaLang != null;
      outPsiMethods[index] = new LightDataBindingMethod(item.getXmlTag(), psiManager, setter, this, javaLang);

      PsiMethod getter = factory.createMethod("get" + StringUtil.capitalize(item.getName()), type);
      PsiUtil.setModifierProperty(getter, PsiModifier.PUBLIC, true);
      outPsiMethods[index + 1] = new LightDataBindingMethod(item.getXmlTag(), psiManager, getter, this, javaLang);
    }

    private void createStaticMethods(PsiElementFactory factory, PsiMethod[] outPsiMethods, int index) {
      PsiClassType myType = factory.createType(this);
      PsiClassType viewGroupType = PsiType
        .getTypeByName(SdkConstants.CLASS_VIEWGROUP, myInfo.getProject(),
                       myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
      PsiClassType layoutInflaterType = PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, myInfo.getProject(),
                                                              myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
      PsiClassType viewType = PsiType
        .getTypeByName(SdkConstants.CLASS_VIEW, myInfo.getProject(),
                       myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
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
        //noinspection ConstantConditions
        outPsiMethods[index++] =
          new LightDataBindingMethod(myInfo.getPsiFile(), psiManager, method, this, Language.findLanguageByID("JAVA"));
      }
    }

    @Nullable
    private PsiField createPsiField(PsiElementFactory factory, DataBindingInfo.ViewWithId viewWithId) {
      PsiType type = resolveViewPsiType(viewWithId, myFacet);
      assert type != null;
      PsiField field = factory.createField(viewWithId.name, type);
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
  public static class LightBrClass extends AndroidLightClassBase {
    private static final String BINDABLE_QUALIFIED_NAME = "android.databinding.Bindable";
    private final AndroidFacet myFacet;
    private CachedValue<PsiField[]> myFieldCache;
    @NotNull
    private String[] myCachedFieldNames = new String[]{"_all"};
    private final String myQualifiedName;
    private PsiFile myContainingFile;

    public LightBrClass(@NotNull PsiManager psiManager, final AndroidFacet facet) {
      super(psiManager);
      myQualifiedName = getBrQualifiedName(facet);
      myFacet = facet;
      myFieldCache =
        CachedValuesManager.getManager(facet.getModule().getProject()).createCachedValue(
          new ResourceCacheValueProvider<PsiField[]>(facet, psiManager.getModificationTracker().getJavaStructureModificationTracker()) {
            @Override
            PsiField[] doCompute() {
              Project project = facet.getModule().getProject();
              PsiElementFactory elementFactory = PsiElementFactory.SERVICE.getInstance(project);
              LocalResourceRepository moduleResources = facet.getModuleResources(false);
              if (moduleResources == null) {
                return defaultValue();
              }
              Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
              if (dataBindingResourceFiles == null) {
                return defaultValue();
              }
              Set<String> variableNames = new HashSet<String>();
              for (DataBindingInfo info : dataBindingResourceFiles.values()) {
                List<PsiDataBindingResourceItem> variables = info.getItems(DataBindingResourceType.VARIABLE);
                if (variables != null) {
                  for (PsiDataBindingResourceItem item : variables) {
                    variableNames.add(item.getName());
                  }
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
              myCachedFieldNames = ArrayUtil.toStringArray(variableNames);
              return result;
            }

            @Override
            PsiField[] defaultValue() {
              Project project = facet.getModule().getProject();
              return new PsiField[]{createPsiField(project, PsiElementFactory.SERVICE.getInstance(project), "_all")};
            }
          }
        );
    }

    private Set<String> collectVariableNamesFromBindables() {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myFacet.getModule().getProject());
      PsiClass aClass = facade.findClass(BINDABLE_QUALIFIED_NAME, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (aClass == null) {
        return null;
      }
      //noinspection unchecked
      final Collection<? extends PsiModifierListOwner> psiElements =
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
      PsiFile containingFile = getContainingFile();
      return containingFile == null ? super.getNavigationElement() : containingFile;
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

  /**
   * Tracker that changes when a facet's data binding enabled value changes
   */
  public static ModificationTracker DATA_BINDING_ENABLED_TRACKER = new ModificationTracker() {
    @Override
    public long getModificationCount() {
      return ourDataBindingEnabledModificationCount.longValue();
    }
  };
}
