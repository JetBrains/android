package org.jetbrains.android.augment;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLightClass extends AndroidLightClassBase {
  private final PsiClass myContainingClass;
  protected final String myName;

  protected AndroidLightClass(@NotNull PsiClass context, @NotNull String name) {
    super(context.getManager());
    myContainingClass = context;
    myName = name;
  }

  @Override
  public String toString() {
    return "AndroidRClass";
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot add elements to R class");
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

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement getParent() {
    return myContainingClass;
  }
}
