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

public class PsiCFGMethod implements ClassMember, PsiAnnotationOwner {

  protected PsiMethod mMethodRef;
  protected PsiLambdaExpression mLambdaExprRef;

  protected PsiElement mPsiElementReference;
  protected PsiCFGClass mParentClass;
  protected int modifers;
  protected MethodGraph mCFG;
  protected boolean mIsLambdaMethod;
  protected PsiMethod mLamdaOverridenMethod;
  protected PsiCFGPartialMethodSignature mSignature;

  public static final PsiCFGMethod[] EMPTY_ARRAY = new PsiCFGMethod[0];

  public PsiCFGMethod(@NotNull PsiMethod method,
                      @NotNull PsiCFGClass declaringClass) {
    this.mMethodRef = method;
    this.mParentClass = declaringClass;
    this.mIsLambdaMethod = false;
    this.modifers = Modifier.ParseModifierList(method.getModifierList());
    this.mSignature = PsiCFGPartialMethodSignatureBuilder.buildFromPsiMethod(method);
  }

  public PsiCFGMethod(@NotNull PsiLambdaExpression lambdaExpr,
                      @NotNull PsiMethod parentMethod,
                      @NotNull PsiCFGClass declearingClass) {
    this.mLambdaExprRef = lambdaExpr;
    this.mMethodRef = null;
    this.mLamdaOverridenMethod = parentMethod;
    this.mParentClass = declearingClass;
    this.mIsLambdaMethod = true;
    this.modifers = Modifier.ParseModifierList(parentMethod.getModifierList());
    this.mSignature = PsiCFGPartialMethodSignatureBuilder.buildFromPsiMethod(parentMethod);
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

  public String getName() {
    if (mIsLambdaMethod) {
      return this.mLamdaOverridenMethod.getName();
    } else {
      return this.mMethodRef.getName();
    }
  }

  public String toString() {
    return this.getName();
  }

  public boolean isLambda() {
    return mIsLambdaMethod;
  }

  /**
   * Return the body of this method. It could be a PsiCodeBlock
   * Or a PsiElement. (The PsiLambda Expression can use a statement as
   * method body)
   *
   * @return
   */
  public PsiElement getBody() {
    return mIsLambdaMethod ? mLambdaExprRef.getBody() : mMethodRef.getBody();
  }

  public PsiMethod getMethodRef() {
    return mMethodRef;
  }

  public PsiLambdaExpression getLambdaRef() {
    return mLambdaExprRef;
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
