package org.jetbrains.android.inspections.lint;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface AndroidLintQuickFix extends WriteActionAware {
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
  class LocalFixWrapper extends LocalQuickFixOnPsiElement {
    private final AndroidLintQuickFix myFix;

    public LocalFixWrapper(@NotNull AndroidLintQuickFix fix, @NotNull PsiElement start, @NotNull PsiElement end) {
      super(start, end);
      myFix = fix;
    }

    @NotNull
    @Override
    public String getText() {
      return myFix.getName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      // Ensure that we use different family names so actions are not collapsed into a single button in
      // the inspections UI (and then *all* processed when the user invokes the action; see
      // https://code.google.com/p/android/issues/detail?id=235641)
      return myFix.getName();
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      myFix.apply(startElement, endElement, AndroidQuickfixContexts.BatchContext.getInstance());
    }

    @Override
    public boolean startInWriteAction() {
      return myFix.startInWriteAction();
    }
  }
}
