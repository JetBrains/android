/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.experimental.codeanalysis.datastructs;

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.MethodGraph;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by haowei on 6/9/16.
 */
public class PsiCFGMethod implements ClassMember, PsiAnnotationOwner {
  protected PsiElement mPsiElementReference;
  protected PsiCFGClass mParentClass;
  protected int modifers;
  protected MethodGraph mCFG;
  protected PsiElement mBody;
  protected boolean mIsLambdaMethod;
  protected PsiMethod mDirectOverridenMethod;
  protected PsiCFGPartialMethodSignature mSignature;

  public static final PsiCFGMethod[] EMPTY_ARRAY = new PsiCFGMethod[0];

  public PsiCFGMethod(PsiElement method, PsiCFGClass delearingClass) {
    this.mPsiElementReference = method;
    this.mParentClass = delearingClass;
    if (mPsiElementReference instanceof PsiMethod) {
      mBody = ((PsiMethod)method).getBody();
      mIsLambdaMethod = false;
      parseModifiers();
    }
    else if (mPsiElementReference instanceof PsiLambdaExpression) {
      mBody = ((PsiLambdaExpression)method).getBody();
      mIsLambdaMethod = true;
    }
    else {
      mBody = null;
    }
    generateSignature();
  }

  private void generateSignature() {
    if (mPsiElementReference != null && mPsiElementReference instanceof PsiMethod) {
      mSignature = PsiCFGPartialMethodSignatureBuilder.buildFromPsiMethod(
        (PsiMethod)mPsiElementReference);
    }
  }

  public PsiCFGPartialMethodSignature getSignature() {
    return this.mSignature;
  }

  private void parseModifiers() {
    if (this.mPsiElementReference instanceof PsiMethod) {
      PsiMethod curMethod = (PsiMethod)this.mPsiElementReference;
      PsiModifierList modList = curMethod.getModifierList();
      this.modifers = Modifier.ParseModifierList(modList);
    }
  }

  public String getName() {
    if (mPsiElementReference instanceof PsiMethod) {
      return ((PsiMethod)mPsiElementReference).getName();
    }
    else if (mPsiElementReference instanceof PsiLambdaExpression) {
      if (mDirectOverridenMethod != null) {
        return mDirectOverridenMethod.getName();
      }
    }
    return "[MethodWOName]";
  }

  public String toString() {
    return this.getName();
  }

  public boolean isLambda() {
    return mIsLambdaMethod;
  }

  public void setLambdaDirectParentMethod(PsiMethod parent) {
    this.mDirectOverridenMethod = parent;
    mIsLambdaMethod = true;
    this.mSignature = PsiCFGPartialMethodSignatureBuilder.buildFromPsiMethod(parent);

    PsiModifierList modList = parent.getModifierList();
    this.modifers = Modifier.ParseModifierList(modList);
  }

  /**
   * Return the body of this method. It could be a PsiCodeBlock
   * Or a PsiElement. (The PsiLambda Expression can use a statement as
   * method body)
   *
   * @return
   */
  public PsiElement getBody() {
    return mBody;
  }

  public PsiElement getPsiRef() {
    return mPsiElementReference;
  }

  @Override
  public PsiCFGClass getDeclaringClass() {
    return mParentClass;
  }

  @Override
  public boolean isProtected() {
    return !((modifers & Modifier.PROTECTED) == 0);
  }

  @Override
  public boolean isPrivate() {
    return !((modifers & Modifier.PRIVATE) == 0);
  }

  @Override
  public boolean isPublic() {
    return !((modifers & Modifier.PUBLIC) == 0);
  }

  @Override
  public boolean isStatic() {
    return !((modifers & Modifier.STATIC) == 0);
  }


  public boolean isAbstract() {
    return !((modifers & Modifier.ABSTRACT) == 0);
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations() {
    return new PsiAnnotation[0];
  }

  @NotNull
  @Override
  public PsiAnnotation[] getApplicableAnnotations() {
    return new PsiAnnotation[0];
  }

  @Nullable
  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

  public MethodGraph getControlFlowGraph() {
    return this.mCFG;
  }

  public void setControlFlowGraph(MethodGraph cfg) {
    this.mCFG = cfg;
  }
}
