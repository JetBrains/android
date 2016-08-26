package org.jetbrains.android.inspections.lint;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface AndroidLintQuickFix {
  AndroidLintQuickFix[] EMPTY_ARRAY = new AndroidLintQuickFix[0];
  
  void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context);

  boolean isApplicable(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.ContextType contextType);

  @NotNull
  String getName();

  /** Wrapper class allowing a {@link LocalQuickFixOnPsiElement} to be used as a {@link AndroidLintQuickFix} */
  class LocalFixWrappee implements AndroidLintQuickFix {
    private final LocalQuickFixOnPsiElement myFix;

    public LocalFixWrappee(@NotNull LocalQuickFixOnPsiElement fix) {
      myFix = fix;
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      myFix.invoke(startElement.getProject(), startElement.getContainingFile(), startElement, endElement);
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      return startElement.isValid();
    }

    @NotNull
    @Override
    public String getName() {
      return myFix.getName();
    }
  }

  /** Wrapper class allowing an {@link AndroidLintQuickFix} to be used as a {@link LocalQuickFix} */
  class LocalFixWrapper implements LocalQuickFix {
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
