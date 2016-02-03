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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.dom.layout.Data;
import org.jetbrains.android.dom.layout.Import;
import org.jetbrains.android.dom.layout.Layout;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Used to suggest completions related to data-binding. This is used in the expressions ({@code @{...}}) and in the {@code <data>} tag.
 */
public class DataBindingCompletionUtil {

  public static final String JAVA_LANG = "java.lang.";

  public static void addCompletions(@NotNull CompletionParameters params, @NotNull CompletionResultSet resultSet) {
    final PsiElement originalPosition = params.getOriginalPosition();
    final PsiElement originalParent = originalPosition == null ? null : originalPosition.getParent();
    if (originalParent == null) {
      return;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(originalParent);
    if (module == null) {
      return;
    }
    final PsiFile containingFile = getRealContainingFile(originalParent.getContainingFile());
    if (containingFile == null) {
      return;
    }
    final String packagePrefix = getPackagePrefix(originalParent, params.getOffset());
    fillAliases(resultSet, packagePrefix, originalPosition, module, originalParent);
    fillClassNames(resultSet, packagePrefix, module);
  }

  /**
   * Add completion suggestions for classes included via {@code <import>}s.
   * @param resultSet the set to add the suggestions to.
   */
  private static void fillAliases(@NotNull CompletionResultSet resultSet,
                                  @NotNull String packagePrefix,
                                  @NotNull PsiElement originalPosition,
                                  @NotNull Module module,
                                  @NotNull PsiElement originalParent) {
    PsiFile containingFile = getRealContainingFile(originalParent.getContainingFile());
    if (containingFile instanceof XmlFile) {
      final Project project = module.getProject();
      DomManager domManager = DomManager.getDomManager(project);
      XmlTag tag = PsiTreeUtil.getParentOfType(originalPosition, XmlTag.class, false, PsiFile.class);
      if (domManager.getDomElement(tag) instanceof Import) {
        return;  // No referencing aliases in import tags.
      }
      DomFileElement<Layout> file = domManager.getFileElement((XmlFile)containingFile, Layout.class);
      if (file != null) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        for (Data data : file.getRootElement().getDatas()) {
          for (Import anImport : data.getImports()) {
            String alias = AndroidLayoutUtil.getAlias(anImport);
            if (packagePrefix.isEmpty()) {
              XmlAttributeValue type = anImport.getType().getXmlAttributeValue();
              if (type != null && alias != null) {
                String typeValue = type.getValue().replace('$', '.');
                PsiClass aClass = facade.findClass(typeValue, module.getModuleWithDependenciesAndLibrariesScope(false));
                if (aClass != null) {
                  resultSet.addElement(getClassReferenceElement(alias, aClass));
                }
              }
            }
            else {
              int i = packagePrefix.indexOf('.');
              String possibleAlias = i < 0 ? packagePrefix : packagePrefix.substring(0, i);
              if (possibleAlias.equals(alias)) {
                String type = anImport.getType().getStringValue();
                if (type != null) {
                  String fqcn = packagePrefix.equals(alias) ? type : type + packagePrefix.substring(alias.length());
                  PsiClass aClass = facade.findClass(fqcn, module.getModuleWithDependenciesAndLibrariesScope(false));
                  if (aClass != null) {
                    for (PsiClass innerClass : aClass.getInnerClasses()) {
                      String name = innerClass.getQualifiedName();
                      if (name != null) {
                        resultSet.addElement(getClassReferenceElement(name.substring(type.length() + 1), innerClass));
                      }
                    }
                    break;
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private static void fillClassNames(@NotNull CompletionResultSet resultSet, @NotNull String packagePrefix, @NotNull Module module) {
    final Project project = module.getProject();
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    PsiPackage basePackage = javaPsiFacade.findPackage(packagePrefix);
    if (basePackage == null) {
      PsiClass aClass = javaPsiFacade.findClass(packagePrefix, module.getModuleWithDependenciesAndLibrariesScope(false));
      if (aClass != null) {
        PsiClass[] innerClasses = aClass.getInnerClasses();
        for (PsiClass innerClass : innerClasses) {
          resultSet.addElement(new JavaPsiClassReferenceElement(innerClass));
        }
      }
      // TODO: add completions for java.lang classes
    }
    else {
      GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
      PsiPackage[] subPackages = basePackage.getSubPackages(scope);
      for (PsiPackage pkg : subPackages) {
        // Make sure that the package contains some useful content before suggesting it. Without this check,
        // many res folders also show up as package suggestions - eg. drawable-hdpi, which is clearly not a package.
        if (pkg.getSubPackages(scope).length > 0 || pkg.getClasses(scope).length > 0) {
          // For some reason, we see some invalid packages here - eg. META-INF. Filter them out.
          String name = pkg.getName();
          boolean invalidPkg = false;
          assert name != null;  // can only be null for default package, which this is not, as it's a subpackage.
          for (int i = 0; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
              invalidPkg = true;
              break;
            }
          }
          if (invalidPkg) {
            continue;  // skip adding this package.
          }
          LookupElement element = new TailTypeDecorator<LookupElement>(LookupElementBuilder.createWithIcon(pkg)) {
            @Nullable
            @Override
            protected TailType computeTailType(InsertionContext context) {
              return TailType.DOT;
            }

            @Override
            public void handleInsert(InsertionContext context) {
              super.handleInsert(context);
              AutoPopupController.getInstance(project).scheduleAutoPopup(context.getEditor());
            }
          };
          resultSet.addElement(element);
        }
      }
      for (PsiClass psiClass : basePackage.getClasses(scope)) {
        resultSet.addElement(new JavaPsiClassReferenceElement(psiClass));
      }
    }
  }

  /**
   * In case of editing the injected language fragment (alt+enter -> Edit AndroidDataBinding Fragment), the top level file isn't the file
   * where the code is actually located.
   */
  @Contract("!null -> !null; null -> null")
  private static PsiFile getRealContainingFile(@Nullable PsiFile file) {
    if (file != null && file.getFileType() == DbFileType.INSTANCE) {
      PsiElement context = file.getContext();
      if (context != null) {
        file = context.getContainingFile();
      }
    }
    return file;
  }

  @NotNull
  private static JavaPsiClassReferenceElement getClassReferenceElement(String alias, PsiClass referenceClass) {
    JavaPsiClassReferenceElement element = new JavaPsiClassReferenceElement(referenceClass);
    element.setForcedPresentableName(alias);
    element.setInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        // Override the default InsertHandler to prevent adding the FQCN.
      }
    });
    return element;
  }

  /**
   * Copied from {@link AllClassesGetter}#getPackagePrefix(PsiElement, int), since that method is private.
   */
  private static String getPackagePrefix(@NotNull final PsiElement context, final int offset) {
    final CharSequence fileText = context.getContainingFile().getViewProvider().getContents();
    int i = offset - 1;
    while (i >= 0) {
      final char c = fileText.charAt(i);
      if (!Character.isJavaIdentifierPart(c) && c != '.') break;
      i--;
    }
    String prefix = fileText.subSequence(i + 1, offset).toString();
    final int j = prefix.lastIndexOf('.');
    return j > 0 ? prefix.substring(0, j) : "";
  }
}
