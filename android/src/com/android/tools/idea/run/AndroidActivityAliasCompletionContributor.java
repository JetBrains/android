/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run;

import com.android.tools.idea.run.editor.LaunchOptionConfigurableContext;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.manifest.ActivityAlias;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AndroidActivityAliasCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }

    final Editor editor = parameters.getEditor();
    LaunchOptionConfigurableContext context = editor.getUserData(LaunchOptionConfigurableContext.KEY);
    if (context == null) {
      return;
    }

    final Module module = context.getModule();
    if (module == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    final String prefix = parameters.getEditor().getDocument().getText().substring(0, parameters.getOffset());
    result = result.withPrefixMatcher(prefix);
    final PsiClass activityClass = JavaPsiFacade.getInstance(module.getProject())
      .findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(false));

    if (activityClass != null) {
      final CompletionResultSet finalResult = result;

      ClassInheritorsSearch.search(activityClass, module.getModuleWithDependenciesScope(), true, true, false).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(PsiClass psiClass) {
          final PsiModifierList modifierList = psiClass.getModifierList();

          if (modifierList != null &&
              modifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
              !modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
            final String qName = psiClass.getQualifiedName();

            if (qName != null && !qName.isEmpty()) {
              finalResult.addElement(JavaLookupElementBuilder.forClass(psiClass, qName));
            }
          }
          return true;
        }
      });
    }
    final Set<String> aliases = collectActivityAliases(facet);

    if (!aliases.isEmpty()) {
      for (String alias : aliases) {
        LookupElementBuilder element = LookupElementBuilder.create(alias);
        final String shortName = StringUtilRt.getShortName(alias);

        if (!shortName.equals(alias)) {
          element = element.withLookupString(shortName);
        }
        result.addElement(element);
      }
    }
    result.stopHere();
  }

  @NotNull
  private static Set<String> collectActivityAliases(@NotNull AndroidFacet facet) {
    final Set<String> result = new HashSet<String>();

    doCollectActivityAliases(facet, result);

    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      doCollectActivityAliases(depFacet, result);
    }
    return result;
  }

  private static void doCollectActivityAliases(@NotNull AndroidFacet facet, @NotNull Set<String> result) {
    final Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return;
    }
    final String aPackage = manifest.getPackage().getStringValue();
    final Application application = manifest.getApplication();

    if (application == null) {
      return;
    }
    for (ActivityAlias activityAlias : application.getActivityAliases()) {
      String alias = activityAlias.getName().getStringValue();

      if (alias != null && !alias.isEmpty()) {
        if (!alias.startsWith(".")) {
          if (alias.indexOf('.') > 0) {
            result.add(alias);
          }
          alias = "." + alias;
        }
        if (aPackage != null && !aPackage.isEmpty() && StringUtil.commonPrefixLength(aPackage, alias) == 0) {
          result.add(aPackage + alias);
        }
      }
    }
  }
}
