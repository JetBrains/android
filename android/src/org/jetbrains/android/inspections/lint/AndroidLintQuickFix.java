package org.jetbrains.android.inspections.lint;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidLintQuickFix {
  AndroidLintQuickFix[] EMPTY_ARRAY = new AndroidLintQuickFix[0];
  
  void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context);

  boolean isApplicable(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.ContextType contextType);

  @NotNull
  String getName();

  public static class LocalFixWrapper implements LocalQuickFix {
    private final AndroidLintQuickFix myFix;
    private final PsiElement myStart;
    private final PsiElement myEnd;

    public LocalFixWrapper(@NotNull AndroidLintQuickFix fix, @NotNull PsiElement start, @NotNull PsiElement end) {
      myFix = fix;
      myStart = start;
      myEnd = end;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myFix.getName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myFix.getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myFix.apply(myStart, myEnd, AndroidQuickfixContexts.BatchContext.getInstance());
    }
  }
}
