package org.jetbrains.android;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
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

    if (facet == null || !AndroidMavenUtil.isMavenizedModule(facet.getModule())) {
      return;
    }
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

  private static boolean isAllowedInAndroid(@NotNull String qName) {
    for (String aPackage : EXCLUDED_PACKAGES) {
      if (qName.startsWith(aPackage + ".")) {
        return false;
      }
    }
    return true;
  }
}
