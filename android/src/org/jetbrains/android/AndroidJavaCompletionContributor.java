package org.jetbrains.android;

import static com.android.SdkConstants.R_CLASS;

import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import java.util.Objects;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidJavaCompletionContributor extends CompletionContributor {
  private static final String[] EXCLUDED_PACKAGES = new String[]{"javax.swing", "javafx"};

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull final CompletionResultSet resultSet) {
    super.fillCompletionVariants(parameters, resultSet);
    final PsiElement position = parameters.getPosition();
    final AndroidFacet facet = AndroidFacet.getInstance(position);

    if (facet == null) {
      return;
    }

    if (AndroidMavenUtil.isMavenizedModule(facet.getModule())) {
      resultSet.runRemainingContributors(parameters, result -> {
        final Object obj = result.getLookupElement().getObject();

        if (obj instanceof PsiClass) {
          final String qName = ((PsiClass)obj).getQualifiedName();

          if (qName != null && !isAllowedInAndroid(qName)) {
            return;
          }
        }
        resultSet.passResult(result);
      });
    }

    // Filter out private resources when completing R.type.name expressions, if any
    if (position.getParent() instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)position.getParent();
      if (ref.getQualifierExpression() != null &&
          ref.getQualifierExpression() instanceof PsiReferenceExpression) {
        PsiReferenceExpression ref2 = (PsiReferenceExpression)ref.getQualifierExpression();
        if (ref2.getQualifierExpression() instanceof PsiReferenceExpression) {
          PsiReferenceExpression ref3 = (PsiReferenceExpression)ref2.getQualifierExpression();
          if (R_CLASS.equals(ref3.getReferenceName())) {
            // We do the filtering only on the R class of this module, users who explicitly reference other R classes are assumed to know
            // what they're doing.
            boolean filterPrivateResources = false;
            PsiExpression qualifierExpression = ref3.getQualifierExpression();
            if (qualifierExpression == null) {
              filterPrivateResources = true;
            }
            else if (qualifierExpression instanceof PsiReferenceExpression) {
              PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
              if (Objects.equals(AndroidManifestUtils.getPackageName(facet), referenceExpression.getQualifiedName()) ||
                  Objects.equals(AndroidManifestUtils.getTestPackageName(facet), referenceExpression.getQualifiedName())) {
                filterPrivateResources = true;
              }
            }

            if (filterPrivateResources) {
              filterPrivateResources(parameters, resultSet, facet);
            }
          }
        }
      }
    }
  }

  public void filterPrivateResources(@NotNull CompletionParameters parameters,
                                     @NotNull final CompletionResultSet resultSet,
                                     AndroidFacet facet) {
    final ResourceVisibilityLookup lookup = ResourceRepositoryManager.getOrCreateInstance(facet).getResourceVisibility();
    if (lookup.isEmpty()) {
      return;
    }
    resultSet.runRemainingContributors(parameters, result -> {
      final Object obj = result.getLookupElement().getObject();

      if (obj instanceof PsiField) {
        PsiField field = (PsiField)obj;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          PsiClass rClass = containingClass.getContainingClass();
          if (rClass != null && R_CLASS.equals(rClass.getName())) {
            ResourceType type = ResourceType.fromClassName(containingClass.getName());
            if (type != null && lookup.isPrivate(type, field.getName())) {
              return;
            }
          }
        }
      }
      resultSet.passResult(result);
    });
  }

  private static boolean isAllowedInAndroid(@NotNull String qName) {
    for (String aPackage : EXCLUDED_PACKAGES) {
      if (qName.startsWith(aPackage + ".")) {
        return false;
      }
    }
    return true;
  }
}
