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

public class PsiCFGClass implements PsiAnnotationOwner {

  public static PsiAnnotation[] emptyAnnotationArray = new PsiAnnotation[0];

  public static final PsiCFGClass[] EMPTY_ARRAY = new PsiCFGClass[0];

  private PsiClass mRef;
  private PsiLambdaExpression mLambdaExpressionRef;
  private PsiAnnotation[] annotationArray = emptyAnnotationArray;
  private PsiFile mPsiFileRef;
  private int mModifierbits;
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


  /**
   * Constructor of a wrapper class for PsiClass
   * @param origin The original PsiClass. It can only be null when this PsiCFGClass instance
   *               represents a lambda.
   * @param declearingFile The File that contains this class. It can be null because library
   *                       classes does not have a PsiFile reference.
   */
  public PsiCFGClass(@Nullable PsiClass origin, @Nullable PsiFile declearingFile) {
    this.mRef = origin;
    this.mPsiFileRef = declearingFile;
    parseModifierList();

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

  /**
   * In default. The initialized PsiCFGClass is an application class. Call this method
   * to set is as a Library class.
   */
  public void setLibraryClass() {
    this.mLibraryClass = true;
  }

  /**
   * @return Return true if this class is library class.
   */
  public boolean isLibraryClass() {
    return this.mLibraryClass;
  }

  //For CHA Usage
  @Nullable
  public PsiCFGClass getSuperClass() {
    return this.mSuperCFGClass;
  }

  public void setSuperClass(@NotNull PsiCFGClass clazz) {
    this.mSuperCFGClass = clazz;
  }

  public void addSubClass(@NotNull PsiCFGClass clazz) {
    this.mDirectSubClasses.add(clazz);
  }

  @NotNull
  public Set<PsiCFGClass> getSubClassSet() {
    return this.mDirectSubClasses;
  }

  /**
   * Add interface class that extends this interface.
   * @param interfaze The subinterface
   */
  public void addSubInterface(@NotNull PsiCFGClass interfaze) {
    this.mDirectSubClasses.add(interfaze);
  }

  /**
   * Add extended interface (Super interface)
   * @param interfaze The extended interface.
   */
  public void addInterface(@NotNull PsiCFGClass interfaze) {
    this.mImplementedInterfacesSet.add(interfaze);
  }

  /**
   * Get the set of implemented interface.
   * @return The Set of implemented Interfaces.
   */
  @NotNull
  public Set<PsiCFGClass> getImplementedInterfaceSet() {
    return this.mImplementedInterfacesSet;
  }

  /**
   * Get the array of implemented interface.
   * @return The Array of implemented Interfaces.
   */
  @NotNull
  public PsiCFGClass[] getImplementedInterfaceArray() {
    return this.mImplementedInterfacesSet.toArray(PsiCFGClass.EMPTY_ARRAY);
  }

  /**
   * Get the super class and implemented interfaces.
   * Does not include super class' super class
   * @return The array of super class and implemented interfaces.
   */
  @NotNull
  public PsiCFGClass[] getAllSupers() {
    List<PsiCFGClass> supersList = Lists.newArrayList();
    supersList.add(this.mSuperCFGClass);
    supersList.addAll(this.mImplementedInterfacesSet);
    return supersList.toArray(PsiCFGClass.EMPTY_ARRAY);
  }

  @Nullable
  public PsiLambdaExpression getPsiLambdaRef() {
    return mLambdaExpressionRef;
  }

  public void setLambdaRef(@NotNull PsiLambdaExpression lambdaRef) {
    this.mLambdaExpressionRef = lambdaRef;
    this.mIsLambda = true;
  }

  /**
   * @return Return the PsiClass reference of this class. It can be null if it is a lambda
   */
  @Nullable
  public PsiClass getPsiClass() {
    return mRef;
  }

  public boolean isPublic() {
    return (this.mModifierbits & Modifier.PUBLIC) != 0;
  }

  public boolean isInterface() {
    return mIsInterface;
  }

  public void setIsInterface(boolean isInterface) {
    this.mIsInterface = isInterface;
  }

  /**
   * @return Return the File that contain this class. It can be null if the class is declared in
   * library.
   */
  @Nullable
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

  /**
   * For nested class that declared in the method only.
   * void method() {
   *   class exampleClass {
   *   }
   * }
   * @return Return the method that declared this class. It can be null
   */
  @Nullable
  public PsiCFGMethod getDeclaringCFGMethod() {
    return mNestedClassParentMethod;
  }

  /**
   * For nested class that declared in the method only. Set the method that declared this class
   * void method() {
   *   class exampleClass {
   *   }
   * }
   * @param method The method that declared this class.
   */
  public void setDeclaringCFGMethod(PsiCFGMethod method) {
    this.mNestedClassParentMethod = method;
  }

  /**
   * For nested class that declared in the method only. Set the block graph that contains this class.
   * @return Return the block graph that contains this graph.
   */
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
   * @return The modified PsiCFGClass
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

  /**
   * The purpose of this method is to assign the lambda expression a proper name.
   * For a lambda expression declared within a class.
   * public class A {
   *   public void method() {
   *     Interface () ->{};
   *   }
   * }
   *
   * Its name will be A.$lambda$NUMBER
   *
   * @param lambdaClass The lambda class
   * @return The modified PsiCFGClass
   */
  public PsiCFGClass addLambda(PsiCFGClass lambdaClass) {
    int curCount = declaredLambda.size() + 1;
    lambdaClass.qualifiedClassName = this.qualifiedClassName + "$lambda$" + curCount;
    this.declaredLambda.add(lambdaClass);
    //lambdaClass.mDirectOverridenInterface = this;
    return lambdaClass;
  }

  /**
   * For anonymos Class and lambda expression only.
   * @param cfgClass The Super class or interface
   */
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

  private void parseModifierList() {
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

  /**
   * Get the qualified name of this class
   * @return The qualified name.
   */
  @NotNull
  public String getQualifiedClassName() {
    return this.qualifiedClassName;
  }

  public void addMethod(@NotNull PsiCFGMethod method) {

    this.mMethodList.add(method);
    PsiMethod methodRef = method.getMethodRef();
    if (methodRef != null) {
      this.mMethodMap.put((PsiMethod)methodRef, method);
    }
    this.mSignatureMethodMap.put(method.getSignature(), method);
  }

  public void addField(@NotNull PsiCFGField field) {
    this.mFieldMap.put(field.getPsiFieldRef().getName(), field);
  }

  @Nullable
  public PsiCFGField getField(String name) {
    if (mFieldMap.containsKey(name)) {
      return mFieldMap.get(name);
    }
    else {
      return null;
    }
  }

  @Nullable
  public PsiCFGMethod getMethod(PsiMethod method) {
    if (mMethodMap.containsKey(method)) {
      return mMethodMap.get(method);
    }
    else {
      return null;
    }
  }

  @Nullable
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
