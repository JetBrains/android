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
package com.android.tools.idea.lang.databinding;

import android.databinding.tool.reflection.Callable;
import android.databinding.tool.reflection.ModelClass;
import android.databinding.tool.reflection.ModelMethod;
import com.android.ide.common.res2.DataBindingResourceType;
import com.android.tools.idea.lang.databinding.model.PsiModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelMethod;
import com.android.tools.idea.lang.databinding.psi.*;
import com.android.tools.idea.rendering.DataBindingInfo;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.PsiDataBindingResourceItem;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.android.dom.converters.DataBindingConverter;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.lang.databinding.DataBindingCompletionUtil.JAVA_LANG;


/**
 * For references in DataBinding expressions. For references in {@code <data>} tag, see {@link DataBindingConverter}
 */
public class DataBindingXmlReferenceContributor extends PsiReferenceContributor {

  @Nullable
  public static DataBindingInfo getDataBindingInfo(PsiElement element) {
    DataBindingInfo dataBindingInfo = null;
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.isDataBindingEnabled()) {
        LocalResourceRepository moduleResources = facet.getModuleResources(true);
        PsiFile topLevelFile = InjectedLanguageUtil.getTopLevelFile(element);
        if (topLevelFile != null) {
          String name = topLevelFile.getName();
          name = name.substring(0, name.lastIndexOf('.'));
          dataBindingInfo = moduleResources.getDataBindingInfoForLayout(name);
        }
      }
    }
    return dataBindingInfo;
  }

  // TODO: Support generics
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbIdExpr.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiDbIdExpr id = (PsiDbIdExpr) element;
        final String text = element.getText();
        if (text == null) {
          return PsiReference.EMPTY_ARRAY;
        }

        DataBindingInfo dataBindingInfo = null;
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null && facet.isDataBindingEnabled()) {
            LocalResourceRepository moduleResources = facet.getModuleResources(true);
            PsiFile topLevelFile = InjectedLanguageUtil.getTopLevelFile(element);
            if (topLevelFile != null) {
              if (topLevelFile.getFileType() == DbFileType.INSTANCE) {
                PsiElement fileContext = topLevelFile.getContext();
                if (fileContext != null) {
                  topLevelFile = fileContext.getContainingFile();
                }
              }
              String name = topLevelFile.getName();
              name = name.substring(0, name.lastIndexOf('.'));
              dataBindingInfo = moduleResources.getDataBindingInfoForLayout(name);
            }
          }
        }
        if (dataBindingInfo == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        for (PsiDataBindingResourceItem variable : dataBindingInfo.getItems(DataBindingResourceType.VARIABLE)) {
          if (text.equals(variable.getName())) {
            final XmlTag xmlTag = variable.getXmlTag();
            return toArray(new VariableDefinitionReference(id, xmlTag, variable, dataBindingInfo, module));
          }
        }
        for (PsiDataBindingResourceItem anImport : dataBindingInfo.getItems(DataBindingResourceType.IMPORT)) {
          if (text.equals(AndroidLayoutUtil.getAlias(anImport))) {
            final XmlTag xmlTag = anImport.getXmlTag();
            return toArray(new ImportDefinitionReference(id, xmlTag, anImport, module));
          }
        }
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(id.getProject());
        if (text.indexOf('.') == -1) {
          PsiClass langClass = javaPsiFacade.findClass(JAVA_LANG + text, GlobalSearchScope.moduleWithLibrariesScope(module));
          if (langClass != null) {
            return toArray(new ClassDefinitionReference(id, langClass));
          }
        }
        PsiPackage aPackage = javaPsiFacade.findPackage(text);
        if (aPackage != null) {
          return toArray(new PackageReference(id, aPackage));
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbDotExpr.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiDbDotExpr dotExpr = (PsiDbDotExpr)element;

        ResolvesToModelClass ref = resolveClassReference(dotExpr.getExpr());
        PsiModelClass psiModelClass = resolveClassType(ref);
        if (psiModelClass == null) {
          PsiReference[] references = dotExpr.getExpr().getReferences();

          if (references.length > 0) {
            String fieldText = dotExpr.getFieldName().getText();
            Module module = ModuleUtilCore.findModuleForPsiElement(element);
            if (module == null || StringUtil.isEmpty(fieldText)) {
              return PsiReference.EMPTY_ARRAY;
            }
            GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
            for (PsiReference reference : references) {
              if (reference instanceof PackageReference) {
                PsiPackage aPackage = ((PackageReference)reference).resolve();
                if (aPackage != null) {
                  for (PsiPackage subPackage : aPackage.getSubPackages(scope)) {
                    String name = subPackage.getName();
                    //noinspection ConstantConditions as a subpackage's name can't be null.
                    if (name.endsWith(fieldText) &&
                        (name.length() == fieldText.length() || name.charAt(name.length() - fieldText.length() - 1) == '.')) {
                      return toArray(new PackageReference(element, subPackage));
                    }
                  }
                  PsiClass[] classes = aPackage.findClassByShortName(fieldText, scope);
                  if (classes.length > 0) {
                    PsiReference[] refs = new PsiReference[classes.length];
                    for (int i = 0; i < classes.length; i++) {
                      refs[i] = new ClassDefinitionReference(element, classes[i]);
                    }
                    return refs;
                  }
                }
              }
            }
          } return PsiReference.EMPTY_ARRAY;
        }
        PsiDbFieldName fieldName = dotExpr.getFieldName();
        String fieldText = fieldName.getText();
        if (StringUtil.isEmpty(fieldText)) {
          return PsiReference.EMPTY_ARRAY;
        }
        // TODO: Search for methods with args also. The following only searches for methods with no args.
        // This results in attributes like 'android:onClick="@{variable.method}"' to be unresolved.
        Callable getterOrField = psiModelClass.findGetterOrField(fieldText, ref.isStatic());
        PsiClass psiClass = psiModelClass.getPsiClass();
        if (psiClass == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        // TODO: If psiClass is ObservableField<Foo> or ObservablePrimitive, change it to Foo (by an implicit call to #get()).
        if (getterOrField != null) {
          if (getterOrField.type.equals(Callable.Type.METHOD)) {
            PsiMethod[] methodsByName = psiClass.findMethodsByName(getterOrField.name, true);
            if (methodsByName.length > 0) {
              return toArray(new PsiMethodReference(dotExpr, methodsByName[0]));
            }
          } else if (getterOrField.type.equals(Callable.Type.FIELD)) {
            PsiField fieldsByName = psiClass.findFieldByName(getterOrField.name, true);
            if (fieldsByName != null) {
              return toArray(new PsiFieldReference(dotExpr, fieldsByName));
            }
          }
        }

        // The field probably references an inner class.
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
          String innerName = psiClass.getQualifiedName() + '.' + fieldText;
          PsiClass clazz =
            JavaPsiFacade.getInstance(element.getProject()).findClass(innerName, module.getModuleWithDependenciesAndLibrariesScope(false));
          if (clazz != null) {
            return toArray(new ClassDefinitionReference(element, clazz));
          }
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbMethodExpr.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiDbMethodExpr methodExpr = (PsiDbMethodExpr)element;
        PsiModelClass psiModelClass = resolveClassType(resolveClassReference(methodExpr.getExpr()));
        if (psiModelClass == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        // TODO we need to do this incrementally. e.g. find a method then a method which matches all params
        List<ModelClass> args = Lists.newArrayList();
        boolean hasInvalidArg = false;
        PsiDbExpressionList expressionList = methodExpr.getExpressionList();
        if (expressionList != null) {
          for (PsiDbExpr expr : expressionList.getExprList()) {
            ModelClass refClass = resolveClassType(resolveClassReference(expr));
            if (refClass == null) {
              hasInvalidArg = true;
              break;
            }
            args.add(refClass);
          }
        }

        if (!hasInvalidArg) {
          // todo check static
          ModelMethod method = psiModelClass.getMethod(methodExpr.getMethodName().getText(), args, false);
          if (method instanceof PsiModelMethod) {
            return toArray(new PsiMethodReference(methodExpr, ((PsiModelMethod)method).getPsiMethod()));
          }
        }
        List<ModelMethod> methods = psiModelClass.findMethods(methodExpr.getMethodName().getText(), false);
        if (methods == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        List<PsiMethodReference> selected = Lists.newArrayList();
        for (ModelMethod modelMethod : methods) {
          if (modelMethod instanceof PsiModelMethod) {
            selected.add(new PsiMethodReference(methodExpr,
                                   ((PsiModelMethod)modelMethod).getPsiMethod()));
          }
        }
        return selected.toArray(new PsiReference[selected.size()]);

      }
    });
  }

  @Nullable
  private static PsiModelClass resolveClassType(@Nullable ResolvesToModelClass ref) {
    return ref == null ? null : ref.getResolvedType();
  }

  @Nullable
  private static ResolvesToModelClass resolveClassReference(@NotNull PsiDbExpr expr) {
    PsiReference[] references = expr.getReferences();
    for (PsiReference ref : references) {
      if (ref instanceof ResolvesToModelClass) {
        return (ResolvesToModelClass) ref;
      }
    }
    return null;
  }

  public interface ResolvesToModelClass {
    @Nullable PsiModelClass getResolvedType();
    boolean isStatic();
  }

  private static class PsiFieldReference extends DefinitionReference {

    public PsiFieldReference(@NotNull PsiDbDotExpr dotExpr, @NotNull PsiField field) {
      super(dotExpr, field, dotExpr.getFieldName().getTextRange().shiftRight(-dotExpr.getStartOffsetInParent()));
    }

    @NotNull
    @Override
    public PsiModelClass getResolvedType() {
      return new PsiModelClass(((PsiField)myTarget).getType());
    }

    @Override
    public boolean isStatic() {
      PsiModifierList modifierList = ((PsiField)myTarget).getModifierList();
      return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC);
    }
  }

  private static class PsiMethodReference extends DefinitionReference {

    public PsiMethodReference(@NotNull PsiDbMethodExpr expr, @NotNull PsiMethod method) {
      super(expr, method, expr.getMethodName().getTextRange().shiftRight(-expr.getStartOffsetInParent()));
    }

    public PsiMethodReference(@NotNull PsiDbDotExpr expr, @NotNull PsiMethod method) {
      super(expr, method, expr.getFieldName().getTextRange().shiftRight(-expr.getStartOffsetInParent()));
    }

    @Nullable
    @Override
    public PsiModelClass getResolvedType() {
      PsiType returnType = ((PsiMethod)myTarget).getReturnType();
      return returnType != null ? new PsiModelClass(returnType) : null;
    }

    @Override
    public boolean isStatic() {
      return false;
    }
  }

  private static class VariableDefinitionReference extends DefinitionReference {
    private PsiModelClass myModelClass;

    public VariableDefinitionReference(@NotNull PsiElement element,
                                       @NotNull XmlTag resolveTo,
                                       @NotNull PsiDataBindingResourceItem variable,
                                       @NotNull DataBindingInfo dataBindingInfo,
                                       @NotNull Module module) {
      super(element, resolveTo);
      final String type = DataBindingConverter.getQualifiedType(variable.getTypeDeclaration(), dataBindingInfo);
      if (type != null) {
        final PsiClass psiType =
          JavaPsiFacade.getInstance(element.getProject()).findClass(type, module.getModuleWithDependenciesAndLibrariesScope(false));
        if (psiType != null) {
          myModelClass = new PsiModelClass(PsiTypesUtil.getClassType(psiType));
        }
      }
    }

    @Override
    @Nullable("Unable to resolve type for the variable.")
    public PsiModelClass getResolvedType() {
      return myModelClass;
    }

    @Override
    public boolean isStatic() {
      return false;
    }
  }

  private static class ImportDefinitionReference extends DefinitionReference {
    private PsiModelClass myModelClass;

    public ImportDefinitionReference(@NotNull PsiElement element,
                                     @NotNull XmlTag resolveTo,
                                     @NotNull PsiDataBindingResourceItem variable,
                                     @NotNull Module module) {
      super(element, resolveTo);
      final String type = variable.getTypeDeclaration();
      if (type != null) {
        final PsiClass psiType =
          JavaPsiFacade.getInstance(element.getProject()).findClass(type, module.getModuleWithDependenciesAndLibrariesScope(false));
        if (psiType != null) {
          myModelClass = new PsiModelClass(PsiTypesUtil.getClassType(psiType));
        }
      }
    }

    @NotNull
    @Override
    public PsiModelClass getResolvedType() {
      return myModelClass;
    }

    @Override
    public boolean isStatic() {
      return true;
    }
  }

  private static class ClassDefinitionReference extends DefinitionReference {

    public ClassDefinitionReference(@NotNull PsiElement element, @NotNull PsiClass resolveTo) {
      super(element, resolveTo);
    }

    @NotNull
    @Override
    public PsiModelClass getResolvedType() {
      return new PsiModelClass(PsiTypesUtil.getClassType((PsiClass)myTarget));
    }

    @Override
    public boolean isStatic() {
      return true;
    }
  }

  private static PsiReference[] toArray(PsiReference... ref) {
    return ref;
  }

  private static class PackageReference implements PsiReference {

    private final PsiElement myElement;
    private final PsiPackage myTarget;
    private final TextRange myTextRange;

    public PackageReference(@NotNull PsiElement element, @NotNull PsiPackage target) {
      myElement = element;
      myTarget = target;
      myTextRange = myElement.getTextRange().shiftRight(-myElement.getStartOffsetInParent());
    }

    @Override
    public PsiElement getElement() {
      return myElement;
    }

    @Override
    public TextRange getRangeInElement() {
      return myTextRange;
    }

    @Nullable
    @Override
    public PsiPackage resolve() {
      return myTarget;
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return myElement.getText();
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return null;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }
}
