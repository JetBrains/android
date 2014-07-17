package org.jetbrains.android.run;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.*;
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

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidActivityAliasCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }
    final Editor editor = parameters.getEditor();

    if (editor == null) {
      return;
    }
    final ApplicationRunParameters runParameters = editor.getUserData(ApplicationRunParameters.ACTIVITY_CLASS_TEXT_FIELD_KEY);

    if (runParameters == null) {
      return;
    }
    final Module module = runParameters.getModule();

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

            if (qName != null && qName.length() > 0) {
              finalResult.addElement(JavaLookupElementBuilder.forClass(psiClass, qName));
            }
          }
          return true;
        }
      });
    }
    final Set<String> aliases = collectActivityAliases(facet);

    if (aliases.size() > 0) {
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
    for (ActivityAlias activityAlias : application.getActivityAliass()) {
      String alias = activityAlias.getName().getStringValue();

      if (alias != null && alias.length() > 0) {
        if (!alias.startsWith(".")) {
          if (alias.indexOf('.') > 0) {
            result.add(alias);
          }
          alias = "." + alias;
        }
        if (aPackage != null && aPackage.length() > 0 && StringUtil.commonPrefixLength(aPackage, alias) == 0) {
          result.add(aPackage + alias);
        }
      }
    }
  }
}
