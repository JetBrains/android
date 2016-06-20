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

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.BlockGraph;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by haowei on 6/1/16.
 */
public class PsiCFGClass implements PsiAnnotationOwner {

  public static PsiAnnotation[] emptyAnnotationArray = new PsiAnnotation[0];

  public static final PsiCFGClass[] EMPTY_ARRAY = new PsiCFGClass[0];

  private PsiClass mRef;
  private PsiLambdaExpression mLambdaExpressionRef;
  private PsiAnnotation[] annotationArray = emptyAnnotationArray;
  private PsiFile mPsiFileRef;
  private int mModifierbits;
  private boolean mIsPublic = false;
  private boolean mIsInterface = false;

  protected Map<PsiMethod, PsiCFGMethod> mMethodMap;
  protected Map<PsiCFGPartialMethodSignature, PsiCFGMethod> mSignatureMethodMap;
  protected ArrayList<PsiCFGMethod> mMethodList;
  protected Map<String, PsiCFGField> mFieldMap;

  protected boolean mIsAnonlymous;
  protected boolean mIsLambda;
  protected boolean mIsNestedClass;
  protected PsiCFGMethod mNestedClassParentMethod;
  protected BlockGraph mNestedClassParentBlock;

  protected ArrayList<PsiCFGClass> declaredAnonymousClass;
  protected ArrayList<PsiCFGClass> declaredLambda;

  protected String qualifiedClassName;


  //For Class Hierachy Analysis
  //Save it direct super class
  protected PsiCFGClass mSuperCFGClass;
  protected Set<PsiCFGClass> mImplementedInterfacesSet;
  protected Set<PsiCFGClass> mDirectSubClasses;
  protected Set<PsiCFGClass> mDirectSubInterfaces;

  //For nested class
  //Including Anonymous Classes
  protected Map<String, Set<PsiCFGClass>> nestedInnerClassMap;


  /***
   * Library Classes are classes that their methods does not have
   * source code.
   */
  protected boolean mLibraryClass;

  //For AnonymousClass and Lambda only
  protected PsiCFGClass mDirectOverriddenInterface;


  public PsiCFGClass(PsiClass origin, PsiFile declearingFile) {
    this.mRef = origin;
    this.mPsiFileRef = declearingFile;
    ParseModifierList();


    this.mIsAnonlymous = false;
    this.mIsLambda = false;
    this.mLibraryClass = false;
    this.mIsNestedClass = false;
    this.mNestedClassParentMethod = null;

    if (origin != null && origin.getQualifiedName() != null) {
      qualifiedClassName = origin.getQualifiedName();
    }
    else {
      qualifiedClassName = "";
    }

    this.declaredAnonymousClass = Lists.newArrayList();
    this.declaredLambda = Lists.newArrayList();
    mMethodMap = Maps.newHashMap();
    mFieldMap = Maps.newHashMap();

    mSuperCFGClass = null;
    mImplementedInterfacesSet = Sets.newHashSet();
    mDirectSubClasses = Sets.newHashSet();
    mDirectSubInterfaces = Sets.newHashSet();
    mMethodList = Lists.newArrayList();


    mSignatureMethodMap = Maps.newHashMap();
    nestedInnerClassMap = Maps.newHashMap();
  }

  //For CHA Usage
  public PsiCFGClass getSuperClass() {
    return this.mSuperCFGClass;
  }

  public void setLibraryClass() {
    this.mLibraryClass = true;
  }

  public boolean isLibraryClass() {
    return this.mLibraryClass;
  }

  public void setSuperClass(PsiCFGClass clazz) {
    this.mSuperCFGClass = clazz;
  }

  public void addSubClass(PsiCFGClass clazz) {
    this.mDirectSubClasses.add(clazz);
  }

  public Set<PsiCFGClass> getSubClassSet() {
    return this.mDirectSubClasses;
  }

  public void addSubInterface(PsiCFGClass interfaze) {
    this.mDirectSubClasses.add(interfaze);
  }

  public void addInterface(PsiCFGClass interfaze) {
    this.mImplementedInterfacesSet.add(interfaze);
  }

  public Set<PsiCFGClass> getImplementedInterfaceSet() {
    return this.mImplementedInterfacesSet;
  }

  public PsiCFGClass[] getImplementedInterfaceArray() {
    return this.mImplementedInterfacesSet.toArray(PsiCFGClass.EMPTY_ARRAY);
  }

  public PsiCFGClass[] getAllSupers() {
    List<PsiCFGClass> supersList = Lists.newArrayList();
    supersList.add(this.mSuperCFGClass);
    supersList.addAll(this.mImplementedInterfacesSet);
    return supersList.toArray(PsiCFGClass.EMPTY_ARRAY);
  }

  public PsiLambdaExpression getPsiLambdaRef() {
    return mLambdaExpressionRef;
  }

  public void setLambdaRef(PsiLambdaExpression lambdaRef) {
    this.mLambdaExpressionRef = lambdaRef;
    this.mIsLambda = true;
  }

  public PsiClass getPsiClass() {
    return mRef;
  }

  public boolean isPublic() {
    return this.mIsPublic;
  }

  public boolean isInterface() {
    return mIsInterface;
  }

  public void setIsInterface(boolean isInterface) {
    this.mIsInterface = isInterface;
  }

  public PsiFile getDeclearingFile() {
    return this.mPsiFileRef;
  }

  public boolean isAnonymous() {
    return mIsAnonlymous;
  }

  public void setAnonlymous() {
    this.mIsAnonlymous = true;
  }

  public void setNested() {
    this.mIsNestedClass = true;
  }

  public boolean isNested() {
    return mIsNestedClass;
  }

  public PsiCFGMethod getDeclaringCFGMethod() {
    return mNestedClassParentMethod;
  }

  public void setDeclaringCFGMethod(PsiCFGMethod method) {
    this.mNestedClassParentMethod = method;
  }

  public BlockGraph getDeclaringBlock() {
    return mNestedClassParentBlock;
  }

  public void setDeclaringBlock(BlockGraph parentBlock) {
    this.mNestedClassParentBlock = parentBlock;
  }

  public String toString() {
    return this.getQualifiedClassName();
  }

  public PsiCFGClass getAnonymousClassDirectParent() {
    return this.mDirectOverriddenInterface;
  }

  public PsiCFGClass addAnonymousClass(PsiCFGClass anonymousClass) {
    int curCount = declaredAnonymousClass.size() + 1;
    anonymousClass.qualifiedClassName = this.qualifiedClassName + "$" + curCount;
    this.declaredAnonymousClass.add(anonymousClass);
    //anonymousClass.mDirectOverridenInterface = this;
    return anonymousClass;
  }


  /**
   * The purpose of this method is to assign
   * the nested inner class a proper name.
   * For a class decleared in a method. E.g.
   * public class A {
   *   public void mehtod() {
   *     class B {
   *       public void apply(){}
   *     }
   *   }
   * }
   *
   * The name of B after the compilation is
   * A.$1B
   *
   * This information is not provided by the Psi
   * @param nestedClass The CFGClass of nested class
   * @param name The name of the class. Empty if it is anounymous
   * @return
   */
  public PsiCFGClass addNestedInnerClass(PsiCFGClass nestedClass, String name) {
    if (name == null) {
      name = "";
    }

    Set<PsiCFGClass> currentCFGClassSet;
    if (nestedInnerClassMap.containsKey(name)) {
      currentCFGClassSet = nestedInnerClassMap.get(name);
    } else {
      currentCFGClassSet = Sets.newHashSet();
      nestedInnerClassMap.put(name, currentCFGClassSet);
    }

    int indexNumber = currentCFGClassSet.size() + 1;
    String qualifiedName = String.format("%s.$%d%s", this.qualifiedClassName, indexNumber, name);
    nestedClass.qualifiedClassName = qualifiedName;
    currentCFGClassSet.add(nestedClass);
    return nestedClass;
  }


  public PsiCFGClass addLambda(PsiCFGClass lambdaClass) {
    int curCount = declaredLambda.size() + 1;
    lambdaClass.qualifiedClassName = this.qualifiedClassName + "$lambda$" + curCount;
    this.declaredLambda.add(lambdaClass);
    //lambdaClass.mDirectOverridenInterface = this;
    return lambdaClass;
  }

  public void setDirectOverride(PsiCFGClass cfgClass) {
    this.mDirectOverriddenInterface = cfgClass;
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations() {
    if (this.mRef.getModifierList() != null) {
      return this.mRef.getModifierList().getAnnotations();
    }
    else {
      return emptyAnnotationArray;
    }
  }

  @NotNull
  @Override
  public PsiAnnotation[] getApplicableAnnotations() {
    if (this.mRef.getModifierList() != null) {
      return this.mRef.getModifierList().getApplicableAnnotations();
    }
    else {
      return emptyAnnotationArray;
    }
  }

  @Nullable
  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    if (this.mRef.getModifierList() != null) {
      return this.mRef.getModifierList().findAnnotation(qualifiedName);
    }
    else {
      return null;
    }
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    if (this.mRef.getModifierList() != null) {
      return this.mRef.getModifierList().addAnnotation(qualifiedName);
    }
    else {
      return null;
    }
  }

  private void ParseModifierList() {
    if (this.mRef != null) {
      PsiModifierList modList = this.mRef.getModifierList();
      if (modList == null) {
        this.mModifierbits = Modifier.DEFAULT;
      }
      else {
        Modifier.ParseModifierList(modList);
      }
    }
  }

  public String getQualifiedClassName() {
    return this.qualifiedClassName;
  }

  public void addMethod(@NotNull PsiCFGMethod method) {

    //this.mMethodMap.put(((PsiMethod)method.getPsiRef()).getName(), method);
    //this.mMethodMap.put(method.getPsiRef(), method);
    this.mMethodList.add(method);
    PsiElement methodRef = method.getPsiRef();
    if (methodRef != null && (methodRef instanceof PsiMethod)) {
      this.mMethodMap.put((PsiMethod)methodRef, method);
      this.mSignatureMethodMap.put(method.getSignature(), method);
    }
  }

  public void addField(@NotNull PsiCFGField field) {
    this.mFieldMap.put(field.getPsiFieldRef().getName(), field);
  }

  public PsiCFGField getField(String name) {
    if (mFieldMap.containsKey(name)) {
      return mFieldMap.get(name);
    }
    else {
      return null;
    }
  }

  public PsiCFGMethod getMethod(PsiMethod method) {
    if (mMethodMap.containsKey(method)) {
      return mMethodMap.get(method);
    }
    else {
      return null;
    }
  }

  public PsiCFGMethod getMethod(PsiCFGPartialMethodSignature signature) {
    if (mSignatureMethodMap.containsKey(signature)) {
      return mSignatureMethodMap.get(signature);
    }
    else {
      return null;
    }
  }

  @NotNull
  public PsiCFGMethod[] getAllMethods() {
    PsiCFGMethod[] retArray = mMethodList.toArray(PsiCFGMethod.EMPTY_ARRAY);
    return retArray;

  }

  @NotNull
  public PsiCFGField[] getAllFields() {
    PsiCFGField[] retArray = new PsiCFGField[this.mFieldMap.size()];
    int i = 0;
    for (String key : this.mFieldMap.keySet()) {
      retArray[i++] = this.mFieldMap.get(key);
    }
    return retArray;
  }
}
