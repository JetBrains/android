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
package com.android.tools.idea.databinding.psiclass;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.ide.common.resources.ResourcesUtil.stripPrefixFromId;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.BindingLayout;
import com.android.tools.idea.databinding.BindingLayoutFile;
import com.android.tools.idea.databinding.index.BindingLayoutType;
import com.android.tools.idea.databinding.index.BindingXmlData;
import com.android.tools.idea.databinding.index.ImportData;
import com.android.tools.idea.databinding.index.VariableData;
import com.android.tools.idea.databinding.index.ViewIdData;
import com.android.tools.idea.databinding.util.DataBindingUtil;
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil;
import com.android.tools.idea.databinding.util.ViewBindingUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ScopeType;
import com.android.tools.idea.psi.light.DeprecatableLightMethodBuilder;
import com.android.tools.idea.psi.light.NullabilityLightFieldBuilder;
import com.android.tools.idea.psi.light.NullabilityLightMethodBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.light.LightField;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kotlin.Pair;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory PSI for classes generated from a layout file (or a list of related layout files from
 * different configurations)
 * <p>
 * See also: https://developer.android.com/topic/libraries/data-binding/expressions#binding_data
 * <p>
 * In the case of common, single-config layouts, only a single "Binding" class will be generated.
 * However, if there are multi-config layouts, e.g. "layout" and "layout-land", a base "Binding"
 * class as well as layout-specific implementations, e.g. "BindingImpl", "BindingLandImpl", will
 * be generated.
 */
public class LightBindingClass extends AndroidLightClassBase {
  private enum NullabilityType {
    UNSPECIFIED,
    NONNULL,
    NULLABLE;

    public boolean isNullable() { return this == NULLABLE; }
  }

  @NotNull private final LightBindingClassConfig myConfig;
  @NotNull private final PsiJavaFile myBackingFile;

  @Nullable private PsiClass[] myPsiSupers; // Created lazily
  @Nullable private PsiMethod[] myPsiConstructors; // Created lazily
  @Nullable private PsiMethod[] myPsiAllMethods; // Created lazily
  @Nullable private PsiMethod[] myPsiMethods; // Created lazily
  @Nullable private PsiField[] myPsiFields; // Created lazily
  @Nullable private PsiReferenceList myExtendsList; // Created lazily
  @Nullable private PsiClassType[] myExtendsListTypes; // Created lazily

  public LightBindingClass(@NotNull PsiManager psiManager, @NotNull LightBindingClassConfig config) {
    super(psiManager, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));
    myConfig = config;

    // Create a fake backing file to represent this binding class
    PsiFileFactory fileFactory = PsiFileFactory.getInstance(getProject());
    myBackingFile = (PsiJavaFile)fileFactory.createFileFromText(myConfig.getClassName() + ".java",
                                                                JavaFileType.INSTANCE,
                                                                "// This class is generated on-the-fly by the IDE.");
    myBackingFile.setPackageName(StringUtil.getPackageName(myConfig.getQualifiedName()));

    setModuleInfo(myConfig.getFacet().getModule(), false);
  }

  private PsiMethod[] computeMethods() {
    List<PsiMethod> methods = new ArrayList<>();

    createRootOverride(methods);

    for (Pair<VariableData, XmlTag> variableTag : myConfig.getVariableTags()) {
      createVariableMethods(variableTag, methods);
    }

    if (myConfig.shouldGenerateGettersAndStaticMethods()) {
      createStaticMethods(methods);
    }

    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  private PsiField[] computeFields() {
    Map<BindingLayout, Collection<ViewIdData>> scopedViewIds = myConfig.getScopedViewIds();
    if (scopedViewIds.isEmpty()) {
      return PsiField.EMPTY_ARRAY;
    }
    boolean allEmpty = true;
    for (Collection<ViewIdData> viewIds : scopedViewIds.values()) {
      if (!viewIds.isEmpty()) {
        allEmpty = false;
        break;
      }
    }
    if (allEmpty) {
      return PsiField.EMPTY_ARRAY;
    }

    PsiField[] computed;

    int numLayouts = scopedViewIds.keySet().size();
    if (numLayouts == 1) {
      // In the overwhelmingly common case, there's only a single layout, which means that all the
      // IDs are present in every layout (there's only the one!), so the fields generated for it
      // are always non-null.
      Collection<ViewIdData> viewIds = scopedViewIds.values().stream().findFirst().get();
      computed = viewIds.stream()
        .map(viewId -> {
          PsiType typeOverride = null;
          String typeOverrideStr = viewId.getTypeOverride();
          if (typeOverrideStr != null) {
            typeOverrideStr = LayoutBindingTypeUtil.getFqcn(typeOverrideStr);
            typeOverride = LayoutBindingTypeUtil.parsePsiType(typeOverrideStr, this);
          }

          return createPsiField(viewId, true, typeOverride);
        })
        .filter(Objects::nonNull)
        .toArray(PsiField[]::new);
    }
    else { // Two or more layouts.
      // Generated fields are non-null only if their source IDs are defined consistently across all layouts.
      Set<ViewIdData> dedupedViewIds = new HashSet<>(); // Only create one field per ID
      Map<String, Integer> idCounts = new HashMap<>();
      {
        for (Collection<ViewIdData> viewIds : scopedViewIds.values()) {
          for (ViewIdData viewId : viewIds) {
            int count = idCounts.compute(viewId.getId(), (key, value) -> (value == null) ? 1 : (value + 1));
            if (count == 1) {
              // It doesn't matter which copy of the ID we keep, so just keep the first one
              dedupedViewIds.add(viewId);
            }
          }
        }
      }

      // If tags have inconsitent IDs, e.g. <TextView ...> in one configuration and <Button ...> in another,
      // the databinding compiler reverts to View
      Set<String> inconsistentlyTypedIds = new HashSet<>();
      {
        Map<String, String> idTypes = new HashMap<>();

        for (Collection<ViewIdData> viewIds : scopedViewIds.values()) {
          for (ViewIdData viewId : viewIds) {
            String id = viewId.getId();
            String viewFqcn = LayoutBindingTypeUtil.getFqcn(viewId.getViewName());
            String viewTypeOverride = viewId.getTypeOverride();
            if (viewTypeOverride != null) {
              viewFqcn = LayoutBindingTypeUtil.getFqcn(viewTypeOverride);
            }

            String previousViewName = idTypes.get(id);
            if (previousViewName == null) {
              idTypes.put(id, viewFqcn);
            }
            else {
              if (!viewFqcn.equals(previousViewName)) {
                inconsistentlyTypedIds.add(id);
              }
            }
          }
        }
      }

      computed = dedupedViewIds.stream()
        .map(viewId -> {
          PsiType typeOverride = null;
          String typeOverrideStr = viewId.getTypeOverride();
          if (inconsistentlyTypedIds.contains(viewId.getId())) {
            typeOverride = LayoutBindingTypeUtil.parsePsiType(CLASS_VIEW, this);
          }
          else if (typeOverrideStr != null) {
            typeOverrideStr = LayoutBindingTypeUtil.getFqcn(typeOverrideStr);
            typeOverride = LayoutBindingTypeUtil.parsePsiType(typeOverrideStr, this);
          }
          return createPsiField(viewId, idCounts.get(viewId.getId()) == numLayouts, typeOverride);
        })
        .filter(Objects::nonNull)
        .toArray(PsiField[]::new);
    }

    return computed;
  }

  /**
   * Creates a private no-argument constructor.
   */
  @NotNull
  private PsiMethod createConstructor() {
    LightMethodBuilder constructor = new LightMethodBuilder(this, JavaLanguage.INSTANCE);
    constructor.setConstructor(true);
    constructor.addModifier(PsiModifier.PRIVATE);
    return constructor;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return myConfig.getQualifiedName();
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    if (myPsiFields == null) {
      myPsiFields = computeFields();
    }
    return myPsiFields;
  }

  @NotNull
  @Override
  public PsiField[] getAllFields() {
    return getFields();
  }

  @NotNull
  @Override
  public PsiMethod[] getConstructors() {
    if (myPsiConstructors == null) {
      myPsiConstructors = new PsiMethod[] {
        createConstructor()
      };
    }
    return myPsiConstructors;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    if (myPsiMethods == null) {
      myPsiMethods = computeMethods();
    }
    return myPsiMethods;
  }

  @NotNull
  @Override
  public PsiClass[] getSupers() {
    if (myPsiSupers == null) {
      PsiClass superClass = getSuperClass();
      if (superClass != null) {
        myPsiSupers = new PsiClass[] { superClass };
      }
      else {
        // superClass shouldn't be null but we handle just in case
        myPsiSupers = PsiClass.EMPTY_ARRAY;
      }
    }
    return myPsiSupers;
  }

  @Override
  public PsiClass getSuperClass() {
    return JavaPsiFacade.getInstance(getProject()).findClass(myConfig.getSuperName(), getModuleScope());
  }

  @Override
  public PsiReferenceList getExtendsList() {
    if (myExtendsList == null) {
      PsiElementFactory factory = PsiElementFactory.getInstance(getProject());
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
      myExtendsListTypes = new PsiClassType[]{PsiType.getTypeByName(myConfig.getSuperName(), getProject(), getModuleScope())};
    }
    return myExtendsListTypes;
  }


  @NotNull
  @Override
  public PsiMethod[] getAllMethods() {
    if (myPsiAllMethods == null) {
      PsiClass superClass = getSuperClass();
      if (superClass != null) {
        myPsiAllMethods = ObjectArrays.concat(superClass.getAllMethods(), getMethods(), PsiMethod.class);
      }
      else {
        // superClass shouldn't be null but we handle just in case
        myPsiAllMethods = getMethods();
      }
    }

    return myPsiAllMethods;
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    List<PsiMethod> matched = null;
    PsiMethod[] methods = checkBases ? getAllMethods() : getMethods();
    for (PsiMethod method : methods) {
      if (name.equals(method.getName())) {
        if (matched == null) {
          matched = new ArrayList<>();
        }
        matched.add(method);
      }
    }
    return matched == null ? PsiMethod.EMPTY_ARRAY : matched.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    boolean continueProcessing = super.processDeclarations(processor, state, lastParent, place);
    if (!continueProcessing) {
      return false;
    }
    Collection<ImportData> imports = myConfig.getTargetLayout().getData().getImports();
    if (imports.isEmpty()) {
      return true;
    }
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint != null && classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      String name = nameHint != null ? nameHint.getName(state) : null;
      for (ImportData anImport : imports) {
        if (anImport.getAlias() != null) {
          continue; // Aliases are pre-resolved.
        }
        String qName = anImport.getType();
        if (name != null && !qName.endsWith(name)) {
          continue;
        }

        PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(qName, getModuleScope());
        if (aClass != null && !processor.execute(aClass, state)) {
          return false; // Found it.
        }
      }
    }
    return true;
  }

  @NotNull
  private GlobalSearchScope getModuleScope() {
    return ProjectSystemUtil.getModuleSystem(myConfig.getFacet()).getResolveScope(ScopeType.MAIN);
  }

  /**
   * If applicable, create a `getRoot` method that overrides / specializes the one in the base
   * class.
   *
   * For example, "View getRoot()" in the base interface could be returned as
   * "LinearLayout getRoot()" in this binding class.
   *
   * If this binding is for a layout with multiple configurations that define inconsistent
   * root tags, then "View" will be returned.
   */
  private void createRootOverride(@NotNull List<PsiMethod> outPsiMethods) {
    XmlFile xmlFile = myConfig.getTargetLayout().toXmlFile();
    if (xmlFile == null) {
      return;
    }

    BindingXmlData xmlData = myConfig.getTargetLayout().getData();
    // For legacy reasons, data binding does not override getRoot with a more specialized return
    // type (e.g. FrameLayout instead of View). Only view binding does this at this time.
    if (xmlData.getLayoutType() == BindingLayoutType.PLAIN_LAYOUT && ViewBindingUtil.isViewBindingEnabled(myConfig.getFacet())) {
      XmlTag xmlRootTag = xmlFile.getRootTag();
      if (xmlRootTag == null) {
        return; // Abort if we can't find an actual PSI tag we can navigate to
      }

      // Note: We don't simply use xmlRootTag's name, since the final return type could be
      // different if root tag names are not consistent across layout configurations.
      String rootTag = myConfig.getRootType();

      PsiType type = LayoutBindingTypeUtil.resolveViewPsiType(xmlData, rootTag, this);
      if (type != null) {
        LightMethodBuilder rootMethod = createPublicMethod("getRoot", type);
        outPsiMethods.add(new LightDataBindingMethod(xmlRootTag, getManager(), rootMethod, this, JavaLanguage.INSTANCE));
      }
    }
  }

  private void createVariableMethods(@NotNull Pair<VariableData, XmlTag> variableTag, @NotNull List<PsiMethod> outPsiMethods) {
    PsiManager psiManager = getManager();

    VariableData variable = variableTag.getFirst();
    XmlTag xmlTag = variableTag.getSecond();

    String typeName = variable.getType();
    String variableType = DataBindingUtil.getQualifiedType(getProject(), typeName, myConfig.getTargetLayout().getData(), true);
    if (variableType == null) {
      return;
    }
    PsiType type = LayoutBindingTypeUtil.parsePsiType(variableType, xmlTag);
    if (type == null) {
      return;
    }

    String javaName = DataBindingUtil.convertVariableNameToJavaFieldName(variable.getName());
    String capitalizedName = StringUtil.capitalize(javaName);
    LightMethodBuilder setter = createPublicMethod("set" + capitalizedName, PsiType.VOID);
    setter.addParameter(javaName, type);
    if (myConfig.settersShouldBeAbstract()) {
      setter.addModifier("abstract");
    }
    outPsiMethods.add(new LightDataBindingMethod(xmlTag, psiManager, setter, this, JavaLanguage.INSTANCE));

    if (myConfig.shouldGenerateGettersAndStaticMethods()) {
      LightMethodBuilder getter = createPublicMethod("get" + capitalizedName, type);
      outPsiMethods.add(new LightDataBindingMethod(xmlTag, psiManager, getter, this, JavaLanguage.INSTANCE));
    }
  }

  private void createStaticMethods(@NotNull List<PsiMethod> outPsiMethods) {
    XmlFile xmlFile = myConfig.getTargetLayout().toXmlFile();
    if (xmlFile == null) {
      return;
    }

    Project project = getProject();
    GlobalSearchScope moduleScope = getModuleScope();
    PsiClassType bindingType = PsiElementFactory.getInstance(getProject()).createType(this);
    PsiClassType viewGroupType = PsiType.getTypeByName(SdkConstants.CLASS_VIEWGROUP, project, moduleScope);
    PsiClassType inflaterType = PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, project, moduleScope);
    PsiClassType viewType = PsiType.getTypeByName(SdkConstants.CLASS_VIEW, project, moduleScope);
    PsiClassType dataBindingComponentType = PsiType.getJavaLangObject(getManager(), moduleScope);

    List<PsiMethod> methods = new ArrayList<>();
    BindingXmlData xmlData = myConfig.getTargetLayout().getData();

    // Methods generated for data binding and view binding diverge a little
    if (xmlData.getLayoutType() == BindingLayoutType.DATA_BINDING_LAYOUT) {
      DeprecatableLightMethodBuilder inflate4Params = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL);
      inflate4Params.addNullabilityParameter("inflater", inflaterType, true);
      inflate4Params.addNullabilityParameter("root", viewGroupType, false);
      inflate4Params.addParameter("attachToRoot", PsiType.BOOLEAN);
      inflate4Params.addNullabilityParameter("bindingComponent", dataBindingComponentType, false);
      // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
      inflate4Params.setDeprecated(true);

      NullabilityLightMethodBuilder inflate3Params = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL);
      inflate3Params.addNullabilityParameter("inflater", inflaterType, true);
      inflate3Params.addNullabilityParameter("root", viewGroupType, false);
      inflate3Params.addParameter("attachToRoot", PsiType.BOOLEAN);

      DeprecatableLightMethodBuilder inflate2Params = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL);
      inflate2Params.addNullabilityParameter("inflater", inflaterType, true);
      inflate2Params.addNullabilityParameter("bindingComponent", dataBindingComponentType, false);
      // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
      inflate2Params.setDeprecated(true);

      NullabilityLightMethodBuilder inflate1Param = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL);
      inflate1Param.addNullabilityParameter("inflater", inflaterType, true);

      NullabilityLightMethodBuilder bind = createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL);
      bind.addNullabilityParameter("view", viewType, true);

      DeprecatableLightMethodBuilder bindWithComponent = createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL);
      bindWithComponent.addNullabilityParameter("view", viewType, true);
      bindWithComponent.addNullabilityParameter("bindingComponent", dataBindingComponentType, false);
      // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
      bindWithComponent.setDeprecated(true);

      methods.add(inflate1Param);
      methods.add(inflate2Params);
      methods.add(inflate3Params);
      methods.add(inflate4Params);
      methods.add(bind);
      methods.add(bindWithComponent);
    }
    else {
      // Expected: If not a data binding layout, this is a view binding layout
      assert (xmlData.getLayoutType() == BindingLayoutType.PLAIN_LAYOUT && ViewBindingUtil.isViewBindingEnabled(myConfig.getFacet()));

      // View Binding is a fresh start - don't show the deprecated methods for them
      if (!xmlData.getRootTag().equals(SdkConstants.VIEW_MERGE)) {
        NullabilityLightMethodBuilder inflate3Params = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL);
        inflate3Params.addNullabilityParameter("inflater", inflaterType, true);
        inflate3Params.addNullabilityParameter("parent", viewGroupType, false);
        inflate3Params.addParameter("attachToParent", PsiType.BOOLEAN);

        NullabilityLightMethodBuilder inflate1Param = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL);
        inflate1Param.addNullabilityParameter("inflater", inflaterType, true);

        methods.add(inflate1Param);
        methods.add(inflate3Params);
      }
      else {
        // View Bindings with <merge> roots have a different set of inflate methods
        NullabilityLightMethodBuilder inflate2Params = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL);
        inflate2Params.addNullabilityParameter("inflater", inflaterType, true);
        inflate2Params.addNullabilityParameter("parent", viewGroupType, true);
        methods.add(inflate2Params);
      }

      NullabilityLightMethodBuilder bind = createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL);
      bind.addNullabilityParameter("view", viewType, true);
      methods.add(bind);
    }

    PsiManager psiManager = getManager();
    for (PsiMethod method : methods) {
      outPsiMethods.add(new LightDataBindingMethod(xmlFile, psiManager, method, this, JavaLanguage.INSTANCE));
    }
  }

  @NotNull
  private DeprecatableLightMethodBuilder createPublicStaticMethod(@NotNull String name,
                                                                  @NotNull PsiType returnType,
                                                                  @NotNull NullabilityType nullabilityType) {
    DeprecatableLightMethodBuilder method = createPublicMethod(name, returnType, nullabilityType);
    method.addModifier("static");
    return method;
  }

  @NotNull
  private DeprecatableLightMethodBuilder createPublicMethod(@NotNull String name, @NotNull PsiType returnType) {
    return createPublicMethod(name, returnType, NullabilityType.UNSPECIFIED);
  }

  @NotNull
  private DeprecatableLightMethodBuilder createPublicMethod(@NotNull String name,
                                                            @NotNull PsiType returnType,
                                                            @NotNull NullabilityType nullabilityType) {
    DeprecatableLightMethodBuilder method = new DeprecatableLightMethodBuilder(getManager(), JavaLanguage.INSTANCE, name);
    method.setContainingClass(this);
    if (nullabilityType == NullabilityType.UNSPECIFIED) {
      method.setMethodReturnType(returnType);
    }
    else {
      method.setMethodReturnType(returnType, !nullabilityType.isNullable());
    }
    method.addModifier("public");
    return method;
  }

  @Nullable
  private PsiField createPsiField(@NotNull ViewIdData viewIdData, boolean isNonNull, @Nullable PsiType typeOverride) {
    String name = DataBindingUtil.convertAndroidIdToJavaFieldName(viewIdData.getId());

    PsiType type;
    if (typeOverride == null) {
      type = LayoutBindingTypeUtil.resolveViewPsiType(myConfig.getTargetLayout().getData(), viewIdData, this);
      if (type == null) {
        return null;
      }
    }
    else {
      type = typeOverride;
    }

    LightFieldBuilder field = new NullabilityLightFieldBuilder(
      PsiManager.getInstance(getProject()), name, type, isNonNull, PsiModifier.PUBLIC, PsiModifier.FINAL);
    return new LightDataBindingField(myConfig.getTargetLayout(), viewIdData, getManager(), field, this);
  }

  @Override
  public boolean isInterface() {
    return super.isInterface();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    XmlFile xmlFile = myConfig.getTargetLayout().toXmlFile();
    if (xmlFile == null) {
      return super.getNavigationElement();
    }

    return new BindingLayoutFile(this, xmlFile);
  }

  @Override
  @NotNull
  public String getName() {
    return myConfig.getClassName();
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myBackingFile;
  }

  @Override
  public boolean isValid() {
    // It is always valid. Not having this valid creates IDE errors because it is not always resolved instantly.
    return true;
  }

  /**
   * The light method class that represents the generated data binding methods for a layout file.
   */
  public static class LightDataBindingMethod extends LightMethod {
    private PsiElement myNavigationElement;

    public LightDataBindingMethod(@NotNull PsiElement navigationElement,
                                  @NotNull PsiManager manager,
                                  @NotNull PsiMethod method,
                                  @NotNull PsiClass containingClass,
                                  @NotNull Language language) {
      super(manager, method, containingClass, language);
      myNavigationElement = navigationElement;
    }

    @Override
    public TextRange getTextRange() {
      return TextRange.EMPTY_RANGE;
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
  public static class LightDataBindingField extends LightField {
    private final BindingLayout myLayout;
    private final ViewIdData myViewIdData;
    @Nullable private XmlTag myNavigationTag = null;

    public LightDataBindingField(@NotNull BindingLayout layout,
                                 @NotNull ViewIdData viewIdData,
                                 @NotNull PsiManager manager,
                                 @NotNull PsiField field,
                                 @NotNull PsiClass containingClass) {
      super(manager, field, containingClass);
      myLayout = layout;
      myViewIdData = viewIdData;
    }

    @Nullable
    private XmlTag computeTag() {
      XmlFile xmlFile = myLayout.toXmlFile();
      if (xmlFile == null) {
        return null;
      }
      Ref<XmlTag> resultTag = new Ref<>();
      xmlFile.accept(new XmlRecursiveElementWalkingVisitor() {
        @Override
        public void visitXmlTag(@NotNull XmlTag tag) {
          super.visitXmlTag(tag);
          String idValue = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
          if (idValue != null && myViewIdData.getId().equals(stripPrefixFromId(idValue))) {
            resultTag.set(tag);
            stopWalking();
          }
        }
      });
      return resultTag.get();
    }

    @Override
    @Nullable
    public PsiFile getContainingFile() {
      return myLayout.toXmlFile();
    }

    @Override
    public TextRange getTextRange() {
      return TextRange.EMPTY_RANGE;
    }

    @Override
    @NotNull
    public PsiElement getNavigationElement() {
      if (myNavigationTag != null) {
        return myNavigationTag;
      }
      myNavigationTag = computeTag();
      return (myNavigationTag != null) ? myNavigationTag : super.getNavigationElement();
    }

    @Override
    @NotNull
    public PsiElement setName(@NotNull String name) {
      // This method is called by rename refactoring and has to succeed in order for the refactoring to succeed.
      // There no need to change the name since once the refactoring is complete, this object will be replaced
      // by a new one reflecting the changed source code.
      return this;
    }
  }
}
