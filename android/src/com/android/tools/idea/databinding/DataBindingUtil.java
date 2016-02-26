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
import com.android.tools.idea.lang.databinding.DbFile;
import com.android.tools.idea.lang.databinding.psi.DbTokenTypes;
import com.android.tools.idea.lang.databinding.psi.PsiDbConstantValue;
import com.android.tools.idea.lang.databinding.psi.PsiDbDefaults;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightField;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private static PsiType parsePsiType(String text, AndroidFacet facet, PsiElement context) {
    PsiElementFactory instance = PsiElementFactory.SERVICE.getInstance(facet.getModule().getProject());
    try {
      PsiType type = instance.createTypeFromText(text, context);
      if ((type instanceof PsiClassReferenceType) && ((PsiClassReferenceType)type).getClassName() == null) {
        // Ensure that if the type is a reference, it's a reference to a valid type.
        return null;
      }
      return type;
    }
    catch (IncorrectOperationException e) {
      // Class named "text" not found.
      return null;
    }
  }

  public static PsiType resolveViewPsiType(DataBindingInfo.ViewWithId viewWithId, AndroidFacet facet) {
    String viewClassName = getViewClassName(viewWithId.tag, facet);
    if (StringUtil.isNotEmpty(viewClassName)) {
      return parsePsiType(viewClassName, facet, null);
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

  public static PsiClass getOrCreatePsiClass(DataBindingInfo info) {
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
    return ManifestInfo.get(facet).getPackage();
  }

  /**
   * Called by the {@linkplain AndroidFacet} to refresh its data binding status.
   *
   * @param facet the {@linkplain AndroidFacet} whose IdeaProject is just set.
   */
  public static void onIdeaProjectSet(AndroidFacet facet) {
    AndroidModel androidModel = facet.getAndroidModel();
    if (androidModel != null) {
      boolean wasEnabled = facet.isDataBindingEnabled();
      boolean enabled = androidModel.getDataBindingEnabled();
      if (enabled != wasEnabled) {
        facet.setDataBindingEnabled(enabled);
        ourDataBindingEnabledModificationCount.incrementAndGet();
      }
    }
  }

  @Nullable
  public static String getBindingExprDefault(@NotNull XmlAttribute psiAttribute) {
    XmlAttributeValue attrValue = psiAttribute.getValueElement();
    if (attrValue instanceof PsiLanguageInjectionHost) {
      final Ref<PsiElement> injections = Ref.create();
      InjectedLanguageUtil.enumerate(attrValue, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        @Override
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          if (injectedPsi instanceof DbFile) {
            injections.set(injectedPsi);
          }
        }
      });
      if (injections.get() != null) {
        PsiDbDefaults defaults = PsiTreeUtil.getChildOfType(injections.get(), PsiDbDefaults.class);
        if (defaults != null) {
          // TODO: extract value from literals and resolve variable values if needed.
          PsiDbConstantValue constantValue = defaults.getConstantValue();
          if (constantValue.getNode().getElementType() == DbTokenTypes.STRING_LITERAL) {
            String text = constantValue.getText();
            return text.substring(1, text.length() -1);  // return unquoted string literal.
          }
          return constantValue.getText();
        }
      }
    }
    return null;
  }

  /**
   * @param exprn Data binding expression enclosed in @{}
   */
  @Nullable
  public static String getBindingExprDefault(@NotNull String exprn) {
    if (!exprn.contains(DbTokenTypes.DEFAULT_KEYWORD.toString())) {
      // A fast check since many expressions would likely not have a default.
      return null;
    }
    Pattern defaultCheck = Pattern.compile(",\\s*default\\s*=\\s*");
    int index = 0;
    Matcher matcher = defaultCheck.matcher(exprn);
    while (matcher.find()) {
      index = matcher.end();
    }
    String def = exprn.substring(index, exprn.length() - 1).trim();  // remove the trailing "}"
    if (def.startsWith("\"") && def.endsWith("\"")) {
      def = def.substring(1, def.length() - 1);       // Unquote the string.
    }
    return def;
  }

  public static boolean isBindingExpression(@NotNull String string) {
    return string.startsWith(SdkConstants.PREFIX_BINDING_EXPR) || string.startsWith(SdkConstants.PREFIX_TWOWAY_BINDING_EXPR);
  }

  /**
   * The light class that represents the generated data binding code for a layout file.
   */
  static class LightBindingClass extends AndroidLightClassBase {
    static final int STATIC_METHOD_COUNT = 6;
    private DataBindingInfo myInfo;
    private CachedValue<PsiMethod[]> myPsiMethodsCache;
    private CachedValue<PsiField[]> myPsiFieldsCache;
    private CachedValue<Map<String, String>> myAliasCache;

    private PsiReferenceList myExtendsList;
    private PsiClassType[] myExtendsListTypes;
    private final AndroidFacet myFacet;
    private static Lexer ourJavaLexer;

    protected LightBindingClass(final AndroidFacet facet, @NotNull PsiManager psiManager, DataBindingInfo info) {
      super(psiManager);
      myInfo = info;
      myFacet = facet;
      CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(info.getProject());
      myAliasCache =
        cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<Map<String, String>>(facet) {
          @Override
          Map<String, String> doCompute() {
            Map<String, String> result = new HashMap<String, String>();
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
            return Maps.newHashMap();
          }
        }, false);

      myPsiMethodsCache =
        cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<PsiMethod[]>(facet) {
          @Override
          PsiMethod[] doCompute() {
            List<PsiDataBindingResourceItem> variables = myInfo.getItems(DataBindingResourceType.VARIABLE);
            if (variables.isEmpty()) {
              return PsiMethod.EMPTY_ARRAY;
            }
            List<PsiMethod> methods = Lists.newArrayListWithCapacity(variables.size() * 2 + STATIC_METHOD_COUNT);
            PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
            for (PsiDataBindingResourceItem variable : variables) {
              createVariableMethods(factory, variable, methods);
            }
            createStaticMethods(factory, methods);
            return methods.toArray(new PsiMethod[methods.size()]);
          }

          @Override
          PsiMethod[] defaultValue() {
            return PsiMethod.EMPTY_ARRAY;
          }
        });

      myPsiFieldsCache =
        cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<PsiField[]>(facet) {
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
                unresolved++;
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
        .findClass(SdkConstants.CLASS_DATA_BINDING_BASE_BINDING, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
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
          PsiType.getTypeByName(SdkConstants.CLASS_DATA_BINDING_BASE_BINDING, myInfo.getProject(),
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
            matched = Lists.newArrayList();
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
          } else {
            out.append(tokenText);
          }
        } else {
          out.append(lexer.getTokenText());
        }
        if (tokenType != TokenType.WHITE_SPACE) { // ignore spaces
          if (tokenType == JavaTokenType.LT || tokenType == JavaTokenType.COMMA) {
            checkNext = true;
          } else {
            checkNext = false;
          }
        }
        lexer.advance();
        tokenType = lexer.getTokenType();
      }
      return out.toString();
    }

    private void createVariableMethods(PsiElementFactory factory, PsiDataBindingResourceItem item, List<PsiMethod> outPsiMethods) {
      PsiManager psiManager = PsiManager.getInstance(myInfo.getProject());
      PsiMethod setter = factory.createMethod("set" + StringUtil.capitalize(item.getName()), PsiType.VOID);

      String variableType = replaceImportAliases(item.getExtra(SdkConstants.ATTR_TYPE));

      PsiType type = parsePsiType(variableType, myFacet, this);
      if (type == null) {
        return;
      }
      PsiParameter param = factory.createParameter(item.getName(), type);
      setter.getParameterList().add(param);
      PsiUtil.setModifierProperty(setter, PsiModifier.PUBLIC, true);

      outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, setter, this, JavaLanguage.INSTANCE));

      PsiMethod getter = factory.createMethod("get" + StringUtil.capitalize(item.getName()), type);
      PsiUtil.setModifierProperty(getter, PsiModifier.PUBLIC, true);
      outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, getter, this, JavaLanguage.INSTANCE));
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
      PsiType type = resolveViewPsiType(viewWithId, myFacet);
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
              myCachedFieldNames = ArrayUtil.toStringArray(variableNames);
              return result;
            }

            @Override
            PsiField[] defaultValue() {
              Project project = facet.getModule().getProject();
              return new PsiField[]{createPsiField(project, PsiElementFactory.SERVICE.getInstance(project), "_all")};
            }
          });
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
