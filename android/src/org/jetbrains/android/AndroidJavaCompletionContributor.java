package org.jetbrains.android;

import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.util.Consumer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.R_CLASS;

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
      resultSet.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
        @Override
        public void consume(CompletionResult result) {
          final Object obj = result.getLookupElement().getObject();

          if (obj instanceof PsiClass) {
            final String qName = ((PsiClass)obj).getQualifiedName();

            if (qName != null && !isAllowedInAndroid(qName)) {
              return;
            }
          }
          resultSet.passResult(result);
        }
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
          if (ref3.getQualifierExpression() == null && R_CLASS.equals(ref3.getReferenceName())) {
            filterPrivateResources(parameters, resultSet, facet);
          }
        }
      }
    }
  }

  public void filterPrivateResources(@NotNull CompletionParameters parameters,
                                     @NotNull final CompletionResultSet resultSet,
                                     AndroidFacet facet) {
    final ResourceVisibilityLookup lookup = AppResourceRepository.getOrCreateInstance(facet).getResourceVisibility(facet);
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
          if (rClass != null && rClass.getName().equals(R_CLASS)) {
            ResourceType type = ResourceType.getEnum(containingClass.getName());
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
