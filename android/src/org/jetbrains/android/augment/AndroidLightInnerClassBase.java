package org.jetbrains.android.augment;

import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Base for all light inner classes implementations, e.g. {@code R.string} or {@code Manifest.permission}.
 */
public abstract class AndroidLightInnerClassBase extends AndroidLightClassBase {
  @NotNull private final AndroidLightClassBase myContainingClass;
  @NotNull protected final String myName;

  protected AndroidLightInnerClassBase(@NotNull AndroidLightClassBase context, @NotNull String name) {
    super(context, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    myContainingClass = context;
    myName = name;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return myContainingClass.add(element);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myContainingClass.addBefore(element, anchor);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myContainingClass.addAfter(element, anchor);
  }

  @Override
  public String getQualifiedName() {
    return myContainingClass.getQualifiedName() + '.' + myName;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myContainingClass;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement getParent() {
    return myContainingClass;
  }
}
