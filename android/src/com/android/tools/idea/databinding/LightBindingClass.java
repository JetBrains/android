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
package com.android.tools.idea.databinding;

import com.android.SdkConstants;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightField;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The light class that represents the generated data binding code for a layout file or a list of layout files from different
 * configurations.
 */
public class LightBindingClass extends AndroidLightClassBase {
  private static final int STATIC_METHOD_COUNT = 6;
  private DataBindingInfo myInfo;
  private CachedValue<PsiMethod[]> myPsiMethodsCache;
  private CachedValue<PsiField[]> myPsiFieldsCache;
  private CachedValue<Map<String, String>> myAliasCache;

  private PsiReferenceList myExtendsList;
  private PsiClassType[] myExtendsListTypes;
  private final AndroidFacet myFacet;
  private static Lexer ourJavaLexer;
  private PsiFile myVirtualPsiFile;
  private final Object myLock = new Object();

  protected LightBindingClass(final AndroidFacet facet, @NotNull PsiManager psiManager, DataBindingInfo info) {
    super(psiManager);
    myInfo = info;
    myFacet = facet;
    // TODO we should create a virtual one not use the XML.
    myVirtualPsiFile = info.getPsiFile();

    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(info.getProject());
    myAliasCache =
      cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<Map<String, String>>(facet, myLock) {
        @Override
        Map<String, String> doCompute() {
          Map<String, String> result = new HashMap<>();
          for (PsiDataBindingResourceItem imp : myInfo.getItems(DataBindingResourceType.IMPORT)) {
            String alias = imp.getExtra(SdkConstants.ATTR_ALIAS);
            if (alias != null) {
              result.put(alias, imp.getExtra(SdkConstants.ATTR_TYPE));
            }
          }
          return result;
        }

        @Override
        Map<String, String> defaultValue() {
          return new HashMap<>();
        }
      }, false);

    myPsiMethodsCache =
      cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<PsiMethod[]>(facet, myLock) {
        @Override
        PsiMethod[] doCompute() {
          List<PsiDataBindingResourceItem> variables = myInfo.getItems(DataBindingResourceType.VARIABLE);
          PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
          // generate getter if this is merged or does not have an alternative layout in another configuration
          List<PsiMethod> methods = new ArrayList<>(variables.size() * 2 + STATIC_METHOD_COUNT);
          // if this is merged, we override all setters (even if we don't use that variable
          DataBindingInfo mergedInfo = myInfo.getMergedInfo();
          if (mergedInfo == null) {
            for (PsiDataBindingResourceItem variable : variables) {
              createVariableMethods(factory, variable, methods, true);
            }
            createStaticMethods(factory, methods);
          } else {
            for (PsiDataBindingResourceItem variable : mergedInfo.getItems(DataBindingResourceType.VARIABLE)) {
              // just the setters to be overriding super class abstract setters
              createVariableMethods(factory, variable, methods, false);
            }
          }
          // create hidden constructor
          PsiMethod constructor = createConstructor(factory);
          methods.add(constructor);
          return methods.toArray(new PsiMethod[methods.size()]);
        }

        @Override
        PsiMethod[] defaultValue() {
          return PsiMethod.EMPTY_ARRAY;
        }
      }, false);

    myPsiFieldsCache =
      cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<PsiField[]>(facet, myLock) {
        @Override
        PsiField[] doCompute() {
          if (myInfo.getMergedInfo() != null) {
            // fields are generated in the base class.
            return PsiField.EMPTY_ARRAY;
          }
          List<DataBindingInfo.ViewWithId> viewsWithIds = myInfo.getViewsWithIds();
          PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
          PsiField[] result = new PsiField[viewsWithIds.size()];
          int i = 0;
          int unresolved = 0;
          for (DataBindingInfo.ViewWithId viewWithId : viewsWithIds) {
            PsiField psiField = createPsiField(factory, viewWithId);
            if (psiField == null) {
              unresolved++;
            }
            else {
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
      }, false);
  }

  @NotNull
  private static PsiMethod createConstructor(PsiElementFactory factory) {
    PsiMethod constructor = factory.createConstructor();
    PsiUtil.setModifierProperty(constructor, PsiModifier.PRIVATE, true);
    return constructor;
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
    DataBindingInfo mergedInfo = myInfo.getMergedInfo();
    String superClassName = mergedInfo == null ? SdkConstants.CLASS_DATA_BINDING_BASE_BINDING : mergedInfo.getQualifiedName();
    return JavaPsiFacade.getInstance(myInfo.getProject())
      .findClass(superClassName, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
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
      DataBindingInfo mergedInfo = myInfo.getMergedInfo();
      String superClassName = mergedInfo == null ? SdkConstants.CLASS_DATA_BINDING_BASE_BINDING : mergedInfo.getQualifiedName();
      myExtendsListTypes = new PsiClassType[]{
        PsiType.getTypeByName(superClassName, myInfo.getProject(),
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
    List<PsiMethod> matched = null;
    for (PsiMethod method : getMethods()) {
      if (name.equals(method.getName())) {
        if (matched == null) {
          matched = new ArrayList<>();
        }
        matched.add(method);
      }
    }
    return matched == null ? PsiMethod.EMPTY_ARRAY : matched.toArray(new PsiMethod[matched.size()]);
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
    List<PsiDataBindingResourceItem> imports = myInfo.getItems(DataBindingResourceType.IMPORT);
    if (imports.isEmpty()) {
      return true;
    }
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint != null && classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      final NameHint nameHint = processor.getHint(NameHint.KEY);
      final String name = nameHint != null ? nameHint.getName(state) : null;
      for (PsiDataBindingResourceItem imp : imports) {
        String alias = imp.getExtra(SdkConstants.ATTR_ALIAS);
        if (alias != null) {
          continue; // aliases are pre-resolved in {@linkplain #replaceImportAliases}
        }
        String qName = imp.getExtra(SdkConstants.ATTR_TYPE);
        if (qName == null) {
          continue;
        }

        if (name != null && !qName.endsWith("." + name)) {
          continue;
        }

        Module module = myInfo.getModule();
        if (module == null) {
          return true; // this should not really happen but just to be safe
        }
        PsiClass aClass = JavaPsiFacade.getInstance(myManager.getProject()).findClass(qName, module
          .getModuleWithDependenciesAndLibrariesScope(true));
        if (aClass != null) {
          if (!processor.execute(aClass, state)) {
            // found it!
            return false;
          }
        }
      }
    }
    return true;
  }

  private static Lexer getJavaLexer() {
    if (ourJavaLexer == null) {
      ourJavaLexer = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_6);
    }
    return ourJavaLexer;
  }

  private String replaceImportAliases(String type) {
    Map<String, String> lookup = myAliasCache.getValue();
    if (lookup == null || lookup.isEmpty()) {
      return type;
    }
    Lexer lexer = getJavaLexer();
    lexer.start(type);
    boolean checkNext = true;
    StringBuilder out = new StringBuilder();
    IElementType tokenType = lexer.getTokenType();
    while (tokenType != null) {
      if (checkNext && tokenType == JavaTokenType.IDENTIFIER) {
        // this might be something we want to replace
        String tokenText = lexer.getTokenText();
        String replacement = lookup.get(tokenText);
        if (replacement != null) {
          out.append(replacement);
        }
        else {
          out.append(tokenText);
        }
      }
      else {
        out.append(lexer.getTokenText());
      }
      if (tokenType != TokenType.WHITE_SPACE) { // ignore spaces
        if (tokenType == JavaTokenType.LT || tokenType == JavaTokenType.COMMA) {
          checkNext = true;
        }
        else {
          checkNext = false;
        }
      }
      lexer.advance();
      tokenType = lexer.getTokenType();
    }
    return out.toString();
  }

  private void createVariableMethods(PsiElementFactory factory, PsiDataBindingResourceItem item, List<PsiMethod> outPsiMethods,
                                     boolean addGetter) {
    PsiManager psiManager = PsiManager.getInstance(myInfo.getProject());
    PsiMethod setter = factory.createMethod("set" + StringUtil.capitalize(item.getName()), PsiType.VOID);

    String variableType = replaceImportAliases(item.getExtra(SdkConstants.ATTR_TYPE));

    PsiType type = DataBindingUtil.parsePsiType(variableType, myFacet, this);
    if (type == null) {
      return;
    }
    PsiParameter param = factory.createParameter(item.getName(), type);
    setter.getParameterList().add(param);
    PsiUtil.setModifierProperty(setter, PsiModifier.PUBLIC, true);
    if (myInfo.isMerged()) {
      PsiUtil.setModifierProperty(setter, PsiModifier.ABSTRACT, true);
    }

    outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, setter, this, JavaLanguage.INSTANCE));
    if (addGetter) {
      PsiMethod getter = factory.createMethod("get" + StringUtil.capitalize(item.getName()), type);
      PsiUtil.setModifierProperty(getter, PsiModifier.PUBLIC, true);
      outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, getter, this, JavaLanguage.INSTANCE));
    }
  }

  private void createStaticMethods(PsiElementFactory factory, List<PsiMethod> outPsiMethods) {
    PsiClassType myType = factory.createType(this);
    PsiClassType viewGroupType = PsiType
      .getTypeByName(SdkConstants.CLASS_VIEWGROUP, myInfo.getProject(),
                     myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType layoutInflaterType = PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, myInfo.getProject(),
                                                            myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType dataBindingComponent = PsiType.getTypeByName(SdkConstants.CLASS_DATA_BINDING_COMPONENT, myInfo.getProject(),
                                                              myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType viewType = PsiType
      .getTypeByName(SdkConstants.CLASS_VIEW, myInfo.getProject(), myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
    PsiParameter layoutInflaterParam = factory.createParameter("inflater", layoutInflaterType);
    PsiParameter rootParam = factory.createParameter("root", viewGroupType);
    PsiParameter attachToRootParam = factory.createParameter("attachToRoot", PsiType.BOOLEAN);
    PsiParameter viewParam = factory.createParameter("view", viewType);
    PsiParameter componentParam = factory.createParameter("bindingComponent", dataBindingComponent);

    PsiMethod inflate4Arg = factory.createMethod("inflate", myType);
    inflate4Arg.getParameterList().add(layoutInflaterParam);
    inflate4Arg.getParameterList().add(rootParam);
    inflate4Arg.getParameterList().add(attachToRootParam);
    inflate4Arg.getParameterList().add(componentParam);

    PsiMethod inflate3Arg = factory.createMethod("inflate", myType);
    inflate3Arg.getParameterList().add(layoutInflaterParam);
    inflate3Arg.getParameterList().add(rootParam);
    inflate3Arg.getParameterList().add(attachToRootParam);

    PsiMethod inflate2Arg = factory.createMethod("inflate", myType);
    inflate2Arg.getParameterList().add(layoutInflaterParam);
    inflate2Arg.getParameterList().add(componentParam);

    PsiMethod inflate1Arg = factory.createMethod("inflate", myType);
    inflate1Arg.getParameterList().add(layoutInflaterParam);


    PsiMethod bind = factory.createMethod("bind", myType);
    bind.getParameterList().add(viewParam);

    PsiMethod bindWithComponent = factory.createMethod("bind", myType);
    bindWithComponent.getParameterList().add(viewParam);
    bindWithComponent.getParameterList().add(componentParam);

    PsiMethod[] methods = new PsiMethod[]{inflate1Arg, inflate2Arg, inflate3Arg, inflate4Arg, bind, bindWithComponent};
    PsiManager psiManager = PsiManager.getInstance(myInfo.getProject());
    for (PsiMethod method : methods) {
      PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
      PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
      outPsiMethods.add(new LightDataBindingMethod(myInfo.getPsiFile(), psiManager, method, this, JavaLanguage.INSTANCE));
    }
  }

  @Nullable
  private PsiField createPsiField(PsiElementFactory factory, DataBindingInfo.ViewWithId viewWithId) {
    PsiType type = DataBindingUtil.resolveViewPsiType(viewWithId, myFacet);
    if (type == null) {
      return null;
    }
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
    return myVirtualPsiFile;
  }

  @Override
  public boolean isValid() {
    // it is always valid. Not having this valid creates IDE errors because it is not always resolved instantly
    return true;
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

    @Override
    @Nullable
    public PsiFile getContainingFile() {
      PsiClass containingClass = super.getContainingClass();
      return containingClass.getContainingFile();
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

    @Override
    @Nullable
    public PsiFile getContainingFile() {
      PsiClass containingClass = super.getContainingClass();
      return containingClass == null ? null : containingClass.getContainingFile();
    }

    @Override
    @NotNull
    public PsiElement getNavigationElement() {
      return myViewWithId.tag;
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