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
package com.android.tools.idea.lint;

import com.android.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.helpers.DefaultJavaEvaluator;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.refactoring.psi.SearchUtils;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidUtils.createChildDirectoryIfNotExist;

/**
 * Quickfix for generating a backup descriptor.
 * <ul>
 * <li>Scan the project for all databases in use.</li>
 * <li>Scan the project for all sharedpreferences in use. </li>
 * <li>Generate a descriptor, exclude the databases and sharedpreferences.</li>
 * <li>Reformat and open the generated descriptor.</li>
 * </ul>
 */
class GenerateBackupDescriptorFix implements AndroidLintQuickFix {

  private static final String XML_CONTENT_START = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                  "<full-backup-content>\n";

  private static final String XML_CONTENT_END = "</full-backup-content>\n";

  private final ResourceUrl myUrl;

  public GenerateBackupDescriptorFix(@NotNull ResourceUrl url) {
    myUrl = url;
  }

  @Override
  public void apply(@NotNull final PsiElement startElement,
                    @NotNull PsiElement endElement,
                    @NotNull AndroidQuickfixContexts.Context context) {
    final Project project = startElement.getProject();
    final AndroidFacet facet = AndroidFacet.getInstance(startElement);
    if (facet == null) {
      return;
    }
    // Find all classes that extend the SQLiteOpenHelper
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    GlobalSearchScope useScope = GlobalSearchScope.projectScope(project);

    // All necessary PsiClassType's
    PsiClassType stringType = PsiType.getJavaLangString(PsiManager.getInstance(project), allScope);
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    PsiClass psiOpenHelperClass = javaPsiFacade.findClass("android.database.sqlite.SQLiteOpenHelper", allScope);
    assert psiOpenHelperClass != null;
    PsiClass psiContext = javaPsiFacade.findClass(CLASS_CONTEXT, allScope);
    assert psiContext != null;

    final Set<String> databaseNames =
      findDatabasesInProject(useScope, psiOpenHelperClass, stringType, javaPsiFacade);
    final Set<String> sharedPreferenceFiles =
      findSharedPrefsInProject(useScope, psiContext, facet, stringType, javaPsiFacade);

    WriteCommandAction.runWriteCommandAction(project, "Create Backup Descriptor", null, () -> {
      try {
        @SuppressWarnings("deprecation")
        VirtualFile primaryResourceDir = ResourceFolderManager.getInstance(facet).getPrimaryFolder();
        assert primaryResourceDir != null;
        VirtualFile xmlDir = createChildDirectoryIfNotExist(project, primaryResourceDir, FD_RES_XML);
        VirtualFile resFile = xmlDir.createChildData(project, myUrl.name + DOT_XML);
        VfsUtil.saveText(resFile, generateBackupDescriptorContents(databaseNames, sharedPreferenceFiles));
        TemplateUtils.reformatAndRearrange(project, resFile);
        TemplateUtils.openEditor(project, resFile);
        TemplateUtils.selectEditor(project, resFile);
      }
      catch (IOException e) {
        String error = String.format("Failed to create file: %1$s", e.getMessage());
        Messages.showErrorDialog(project, error, "Create Backup Resource");
      }
    });
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    AndroidFacet facet = AndroidFacet.getInstance(startElement);
    AppResourceRepository appResources = facet == null ? null : AppResourceRepository.getOrCreateInstance(facet);
    return appResources == null || !appResources.getItemsOfType(ResourceType.XML).contains(myUrl.name);
  }

  @NotNull
  @Override
  public String getName() {
    return "Generate full-backup-content descriptor";
  }

  private static Set<String> findSharedPrefsInProject(GlobalSearchScope useScope,
                                                      PsiClass psiContext,
                                                      @NotNull AndroidFacet facet,
                                                      @NotNull final PsiClassType stringType,
                                                      @NotNull JavaPsiFacade psiFacade) {
    final Set<String> prefFiles = new SmartHashSet<>();
    // Note: To find the usages of a given method, we need to use the following:
    // 1. First find all the methods that override the given method.
    // 2. Search of usages of the given method and all the overriding methods.
    PsiMethod[] methods = psiContext.findMethodsByName("getSharedPreferences", true);
    List<PsiMethod> allMethods = new ArrayList<>(Arrays.asList(methods));
    // Find all overriding methods of getSharedPreferences(..)
    for (PsiMethod method : methods) {
      allMethods.addAll(Lists.newArrayList(SearchUtils.findOverridingMethods(method)));
    }

    for (final PsiMethod method : allMethods) {
      Iterable<PsiReference> references = SearchUtils.findAllReferences(method, useScope);
      for (final PsiReference ref : references) {
        ref.getElement().getParent().accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            PsiMethod psiMethod = expression.resolveMethod();
            String methodName = psiMethod == null ? null : psiMethod.getName();
            // Processing an inner call to getString(R.string.$name$)
            // For example getSharedPreferences(getString(R.string.pref_name), mode)
            if (GET_STRING_METHOD.equals(methodName)) {

              PsiExpression[] expressions = expression.getArgumentList().getExpressions();
              if (expressions.length == 1 && PsiType.INT.equals(expressions[0].getType())) {

                // Use a ResourceEvaluator to find the resource type/name. This has the
                // advantage that it can also resolve complex expressions used as the
                // getString argument.
                ResourceUrl resource = ResourceEvaluator.getResource(
                  new DefaultJavaEvaluator(expression.getProject(), null),
                  expressions[0]);

                if (resource == null || resource.framework || resource.type != ResourceType.STRING) {
                  return;
                }

                List<PsiElement> resources = ModuleResourceManagers.getInstance(facet).getLocalResourceManager()
                    .findResourcesByFieldName(ResourceType.STRING.getName(), resource.name);

                for (PsiElement resElement : resources) {
                  if (resElement instanceof XmlAttributeValue) {
                    // get the parent XmlTag and drill down to it's text.
                    XmlTagValue value = ((XmlTagImpl)resElement.getParent().getParent()).getValue();
                    prefFiles.add(value.getText());
                    break;
                  }
                }
              }
            }
            else if (method.getName().equals(methodName)) {
              // Look for getSharedPreferences(String name, int mode) on the Context object
              PsiExpression[] expressions = expression.getArgumentList().getExpressions();
              if (expressions.length == 2 && stringType.equals(expressions[0].getType())) {
                Object result = psiFacade.getConstantEvaluationHelper()
                  .computeConstantExpression(expressions[0]);
                if (result != null) {
                  prefFiles.add((String)result);
                }
                else {
                  // let it run through in case it contains a call to getString
                  super.visitMethodCallExpression(expression);
                }
              }
            }

          }
        });
      }
    }

    return prefFiles;
  }

  @NotNull
  private static Set<String> findDatabasesInProject(GlobalSearchScope useScope,
                                                    final PsiClass psiClass,
                                                    final PsiClassType stringType,
                                                    final JavaPsiFacade psiFacade) {

    PsiMethod[] constructors = psiClass.getConstructors();

    final Set<String> databaseNames = new SmartHashSet<>();
    for (final PsiMethod method : constructors) {
      Iterable<PsiReference> references = SearchUtils.findAllReferences(method, useScope);
      for (final PsiReference ref : references) {
        final PsiElement element = ref.getElement();
        element.getParent().accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            PsiMethod method = expression.resolveMethod();
            if (method != null && method.getContainingClass() != null
                && method.getContainingClass().isEquivalentTo(psiClass)) {
              PsiExpression[] expressions = expression.getArgumentList().getExpressions();
              if (expressions.length > 2 && stringType.equals(expressions[1].getType())) {
                // 2nd parameter of one of the following constructors:
                // 1. SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version)
                // 2. SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version,
                //             DatabaseErrorHandler errorHandler)
                PsiExpression expressionToEvaluate = expressions[1];
                Object result = psiFacade.getConstantEvaluationHelper()
                  .computeConstantExpression(expressionToEvaluate);
                if (result != null) {
                  databaseNames.add((String)result);
                }
              }
            }
            super.visitMethodCallExpression(expression);
          }
        });
      }
    }
    return databaseNames;
  }

  // TODO/consider: The error message from ManifestDetector.ALLOW_BACKUP already
  // contains an indication that the AndroidManifest.xml has a GCM receiver.
  // This could've been used to add a very specific comment in the descriptor
  // saying that the GCM regId should be excluded *but* relying on text of the message
  // seems a bit brittle (given that it can be localized).

  // Another way to address this would be to have a specific Lint Issue so that
  // this information is passed from the lint check to the IDE.
  // Yet another way would be to re-parse the AndroidManifest.xml to see if there is
  // a GCM receiver. (Since this is just for adding a comment, I've left that out)
  private static String generateBackupDescriptorContents(Set<String> databaseNames,
                                                         Set<String> sharedPrefs) {
    StringBuilder sb = new StringBuilder();
    sb.append(XML_CONTENT_START);

    if (!databaseNames.isEmpty() || !sharedPrefs.isEmpty()) {
      sb.append("<!-- TODO Remove the following \"exclude\" elements to make them a part of the auto backup -->\n");
    }
    // Databases
    for (String name : databaseNames) {
      sb.append(String.format("<exclude domain=\"database\" path=\"%1$s\"/>\n", name));
    }
    // shared preferences
    if (!sharedPrefs.isEmpty()) {
      sb.append("<!-- Exclude the shared preferences file that contains the GCM registrationId -->\n");
    } else {
      sb.append("<!-- Exclude specific shared preferences that contain GCM registration Id -->\n");
    }

    for (String name : sharedPrefs) {
      sb.append(String.format("<exclude domain=\"sharedpref\" path=\"%1$s.xml\" />\n", name));
    }
    sb.append(XML_CONTENT_END);
    return sb.toString();
  }
}
