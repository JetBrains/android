package org.jetbrains.android;

import static com.android.SdkConstants.R_CLASS;

import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import java.util.Objects;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.AndroidDeprecationInspection;
import org.jetbrains.annotations.NotNull;

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

    boolean filterPrivateResources = shouldFilterPrivateResources(position, facet);

    resultSet.runRemainingContributors(parameters, result -> {
      CompletionResult modifiedResult = result;
      if (filterPrivateResources) {
        if (isForPrivateResource(modifiedResult, facet)) {
          modifiedResult = null;
        }
      }

      if (modifiedResult != null) {
        modifiedResult = fixDeprecationPresentation(modifiedResult, parameters);
      }

      if (modifiedResult != null) {
        resultSet.passResult(modifiedResult);
      }
    });
  }

  private static boolean shouldFilterPrivateResources(PsiElement position, AndroidFacet facet) {
    boolean filterPrivateResources = false;
    // Filter out private resources when completing R.type.name expressions, if any.
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
            PsiExpression qualifierExpression = ref3.getQualifierExpression();
            if (qualifierExpression == null) {
              filterPrivateResources = true;
            }
            else if (qualifierExpression instanceof PsiReferenceExpression) {
              PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
              if (Objects.equals(ProjectSystemUtil.getModuleSystem(facet).getPackageName(), referenceExpression.getQualifiedName()) ||
                  Objects.equals(ProjectSystemUtil.getModuleSystem(facet).getTestPackageName(), referenceExpression.getQualifiedName())) {
                filterPrivateResources = true;
              }
            }
          }
        }
      }
    }
    return filterPrivateResources;
  }

  @NotNull
  public static CompletionResult fixDeprecationPresentation(@NotNull CompletionResult result,
                                                            @NotNull CompletionParameters parameters) {
    Object obj = result.getLookupElement().getObject();
    if (obj instanceof PsiDocCommentOwner) {
      PsiDocCommentOwner docCommentOwner = (PsiDocCommentOwner)obj;
      if (docCommentOwner.isDeprecated()) {
        for (AndroidDeprecationInspection.DeprecationFilter filter : AndroidDeprecationInspection.getFilters()) {
          if (filter.isExcluded(docCommentOwner, parameters.getPosition(), null)) {
            result = result.withLookupElement(new NonDeprecatedDecorator(result.getLookupElement()));
          }
        }
      }
    }
    return result;
  }

  public static boolean isForPrivateResource(@NotNull CompletionResult result, @NotNull AndroidFacet facet) {
    Object obj = result.getLookupElement().getObject();
    if (!(obj instanceof PsiField)) {
      return false;
    }

    PsiField psiField = (PsiField)obj;
    PsiClass containingClass = psiField.getContainingClass();
    if (containingClass != null) {
      PsiClass rClass = containingClass.getContainingClass();
      if (rClass != null && R_CLASS.equals(rClass.getName())) {
        String resourceTypeName = containingClass.getName();
        if (resourceTypeName == null) {
          return false;
        }

        ResourceType type = ResourceType.fromClassName(containingClass.getName());
        StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(facet);
        return type != null && !IdeResourcesUtil.isAccessible(repositoryManager.getNamespace(), type, psiField.getName(), facet);
      }
    }
    return false;
  }

  private static boolean isAllowedInAndroid(@NotNull String qName) {
    for (String aPackage : EXCLUDED_PACKAGES) {
      if (qName.startsWith(aPackage + ".")) {
        return false;
      }
    }
    return true;
  }

  /**
   * Wrapper around a {@link LookupElement} that removes the deprecation strikeout. It's used when we we are in a code branch specific to
   * an old SDK where a given {@link PsiElement} was not yet deprecated.
   *
   * @see AndroidDeprecationInspection.DeprecationFilter
   */
  private static class NonDeprecatedDecorator extends LookupElementDecorator<LookupElement> {
    protected NonDeprecatedDecorator(@NotNull LookupElement delegate) {
      super(delegate);
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setStrikeout(false);
    }
  }
}
