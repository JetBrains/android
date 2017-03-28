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
package com.android.tools.idea.experimental.codeanalysis;

import com.android.tools.idea.experimental.codeanalysis.callgraph.Callgraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.BlockGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.utils.CFGUtil;
import com.android.tools.idea.experimental.codeanalysis.utils.PsiCFGAnalysisUtil;
import com.android.tools.idea.experimental.codeanalysis.utils.PsiCFGDebugUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * A class that saves all information of the interprocedural control
 * flow analysis
 */
public class PsiCFGScene {
  private static ConcurrentMap<Project, PsiCFGScene> instanceMap = Maps.newConcurrentMap();

  private Map<String, PsiCFGClass> mAppClassNamePsiMap;
  private Map<String, PsiCFGClass> mLibraryClassNamePsiMap;

  private Map<PsiClass, PsiCFGClass> mAppPsiClassPsiCFGClassMap;
  private Map<PsiClass, PsiCFGClass> mLibraryPsiClassPsiCFGClassMap;

  private Map<PsiLambdaExpression, PsiCFGClass> mLambdaPsiCFGClassMap;

  private Project mProject;

  protected ArrayList<GraphNode> mInvocationNodes;

  public PsiCFGAnalysisUtil analysisUtil;

  protected Callgraph mCallGraph;

  public Deque<PsiCFGClass> workingList;


  //private Map<PsiClass, PsiCFGClassBase> mPsiClassPsiMap;

  /**
   * Return the project associated to this scene object.
   * @return
   */
  @NotNull
  public Project getProject() {
    return this.mProject;
  }

  private PsiCFGScene(Project proj) {
    mAppClassNamePsiMap = Maps.newHashMap();
    mLibraryClassNamePsiMap = Maps.newHashMap();
    mProject = proj;
    analysisUtil = new PsiCFGAnalysisUtil(this);
    mAppPsiClassPsiCFGClassMap = Maps.newHashMap();
    mLibraryPsiClassPsiCFGClassMap = Maps.newHashMap();
    mLambdaPsiCFGClassMap = Maps.newHashMap();
    mInvocationNodes = Lists.newArrayList();
    workingList = Queues.newArrayDeque();
  }

  public void setCallGraph(Callgraph callGraph) {
    mCallGraph = callGraph;
  }

  public Callgraph getCallGraph() {
    return mCallGraph;
  }

  public void addInvocationNode(GraphNode node) {
    mInvocationNodes.add(node);
  }

  public GraphNode[] getAllInvocationNode() {
    return mInvocationNodes.toArray(GraphNode.EMPTY_ARRAY);
  }

  /**
   * Get the Scene object for this project.
   * The original idea is that the CFG does not need be recreated each time it is used.
   * So the the instance of the PsiCFGScene is saved into a Project, PsiCFGSCene map.
   *
   * However, in the entry of the analysis, the CFG and call graph is still recreated each
   * time the analysis is invoked.
   *
   * This may raise a memory leak issue.
   * @param proj The current project instance
   * @return The PsiCFGScene associated with this project.
   */
  public static PsiCFGScene getInstance(Project proj) {
    if (instanceMap.containsKey(proj)) {
      //A instance of Static Analysis Scene is already existed
      return instanceMap.get(proj);
    }
    else {
      //Not exist, create one.
      PsiCFGScene instance = new PsiCFGScene(proj);
      instanceMap.put(proj, instance);
      return instance;
    }
  }

  /**
   * Create a fresh PsiCFGScene for this project. It there is one exists, discard it.
   * @param proj The current project instance
   * @return The PsiCFGScene associated with this project.
   */
  public static PsiCFGScene createFreshInstance(Project proj) {
    if (instanceMap.containsKey(proj)) {
      //A instance of Static Analysis Scene is already existed
      instanceMap.remove(proj);
    }
    PsiCFGScene instance = new PsiCFGScene(proj);
    instanceMap.put(proj, instance);
    return instance;
  }

  /**
   * Get all PsiClass instances found by visiting all java files in the project.
   * @return A new array of PsiClass instances which are application classes.
   */
  public PsiClass[] getAllApplicationPsiClasses() {
    PsiClass[] retArray = new PsiClass[mAppClassNamePsiMap.size()];
    int i = 0;
    for (String className : mAppClassNamePsiMap.keySet()) {
      PsiCFGClass clazzBase = mAppClassNamePsiMap.get(className);
      retArray[i] = clazzBase.getPsiClass();
      i++;
    }
    return retArray;
  }

  /**
   * Get all PsiCFGClass instances constructed from the application classses.
   * @return A new array of PsiCFGClass instances which are application classes will be returned.
   */
  public PsiCFGClass[] getAllApplicationClasses() {
    PsiCFGClass[] retArray = new PsiCFGClass[mAppClassNamePsiMap.size()];
    int i = 0;
    for (String className : mAppClassNamePsiMap.keySet()) {
      PsiCFGClass clazzBase = mAppClassNamePsiMap.get(className);
      retArray[i++] = clazzBase;
    }
    return retArray;
  }

  /**
   * Get the set of PsiCFGClass instances which are application classes.
   * @return A new set of PsiCFGClass instances which are application classes will be returned.
   */
  public Set<PsiClass> getAllLibraryClassPsiSet() {
    return Sets.newHashSet(this.mLibraryPsiClassPsiCFGClassMap.keySet());
  }

  /**
   * Get all PsiCFGClass instances constructed from the library classses.
   * @return A new array of PsiCFGClass instances which are library classes will be returned.
   */
  public PsiCFGClass[] getAllLibraryClasses() {
    PsiCFGClass[] retArray = new PsiCFGClass[mLibraryClassNamePsiMap.size()];
    int i = 0;
    for (String className : mLibraryClassNamePsiMap.keySet()) {
      PsiCFGClass clazzBase = mLibraryClassNamePsiMap.get(className);
      retArray[i++] = clazzBase;
    }
    return retArray;
  }

  /**
   * Get all PsiCFGClass instances constructed from the lambda expression.
   * @return A new array of PsiCFGClass instances which are lambda anonymous classes will be
   * returned.
   */
  public PsiCFGClass[] getAllLambdaClass() {
    PsiCFGClass[] retArray = new PsiCFGClass[mLambdaPsiCFGClassMap.size()];
    int i = 0;
    for (PsiLambdaExpression lbdExpr : mLambdaPsiCFGClassMap.keySet()) {
      PsiCFGClass mLbdClass = mLambdaPsiCFGClassMap.get(lbdExpr);
      retArray[i++] = mLbdClass;
    }
    return retArray;
  }

  /**
   * Get the PsiCFGClass by the full qualified class name.
   * Return null if it does not exist.
   * @param name The qualified name of the class
   * @return The PsiCFGClass instance.
   */
  public PsiCFGClass getPsiCFGClass(String name) {
    if (mAppClassNamePsiMap.containsKey(name)) {
      return mAppClassNamePsiMap.get(name);
    }
    else if (mLibraryClassNamePsiMap.containsKey(name)) {
      return mLibraryClassNamePsiMap.get(name);
    }
    else {
      //Both two maps does not have this class
      return null;
    }
  }

  /**
   * Get the PsiCFGClass by the instance PsiClass
   * Return null if it does not exist.
   * @param name The PsiClass
   * @return The PsiCFGClass instance.
   */
  public PsiCFGClass getPsiCFGClass(PsiClass psiClazz) {
    if (mAppPsiClassPsiCFGClassMap.containsKey(psiClazz)) {
      return mAppPsiClassPsiCFGClassMap.get(psiClazz);
    }
    else if (mLibraryPsiClassPsiCFGClassMap.containsKey(psiClazz)) {
      return mLibraryPsiClassPsiCFGClassMap.get(psiClazz);
    }
    else {
      //Both two maps does not have this class
      return null;
    }
  }

  public PsiCFGClass getOrCreateCFGClass(PsiClass psiClazz) {
    PsiCFGClass retClass = getPsiCFGClass(psiClazz);
    if (retClass == null) {
      retClass = createAndParsePsiCFGClassOnTheFly(psiClazz);
    }
    return retClass;
  }

  public PsiCFGClass createPsiCFGClass(PsiClass psiClass,
                                       PsiFile declaringFile,
                                       boolean bAppClass) {
    String fullClassName = psiClass.getQualifiedName();
    if (fullClassName == null) {
      //TODO: Local or anonymous class
      PsiCFGDebugUtil.LOG.warning("The class has no name: ");
      PsiCFGDebugUtil.debugOutputPsiElement(psiClass);
      throw new RuntimeException(String.format("Class %s does not have a full name",
                                               psiClass.getText()));
    }
    if (!psiClass.isInterface()) {
      PsiCFGClass newClass = new PsiCFGClass(psiClass, declaringFile);
      if (bAppClass) {
        mAppClassNamePsiMap.put(fullClassName, newClass);
        mAppPsiClassPsiCFGClassMap.put(psiClass, newClass);
      }
      else {
        mLibraryClassNamePsiMap.put(fullClassName, newClass);
        mLibraryPsiClassPsiCFGClassMap.put(psiClass, newClass);
        newClass.setLibraryClass();
      }
      return newClass;
    }
    else {
      PsiCFGClass newInterface = new PsiCFGClass(psiClass, declaringFile);
      newInterface.setIsInterface(true);
      if (bAppClass) {
        mAppClassNamePsiMap.put(fullClassName, newInterface);
        mAppPsiClassPsiCFGClassMap.put(psiClass, newInterface);
      }
      else {
        mLibraryClassNamePsiMap.put(fullClassName, newInterface);
        mLibraryPsiClassPsiCFGClassMap.put(psiClass, newInterface);
        newInterface.setLibraryClass();
      }
      return newInterface;
    }
  }

  /**
   * The purpose of this method is create PsiCFGClasses on the fly during the
   * CFG construction. As inside the code, the ReferenceExpression may refer to
   * a class that is not in the mAppClass maps.
   * As all users' code are analyzed during phase 1. So the classes passed into this
   * method should be treated as library classes. Therefore, we do not need to create
   * CFG for these classes.
   *
   * If the PsiClass passed in is a anonymous class. return null.
   *
   * @param psiClass
   * @return
   */
  public PsiCFGClass createAndParsePsiCFGClassOnTheFly(PsiClass psiClass) {
    //Sanity check. Make sure the psiClass param is really not in side the App classes.
    if (mAppPsiClassPsiCFGClassMap.containsKey(psiClass)) {
      //Not expected. Print a log
      PsiCFGDebugUtil.LOG.warning(
        "A user class is passed into createAndParsePsiCFGClassOnTheFly\n" +
        "ClassName: " + psiClass.getQualifiedName());
      return mAppPsiClassPsiCFGClassMap.get(psiClass);
    }

    //Check if the class is a anonymous class
    if (psiClass instanceof PsiAnonymousClass) {
      return null;
    }

    //Not exist in app classes or libraryes class maps.
    //Create one on the fly
    PsiCFGClass retClass = createLibraryCFGClassesWInnerClasses(psiClass);
    return retClass;
  }

  /**
   * @param clazz
   * @return
   */
  public PsiCFGClass createLibraryCFGClassesWInnerClasses(PsiClass clazz) {
    ArrayList<PsiClass> classList = Lists.newArrayList();
    retriveClassAndInnerClass(classList, clazz);
    classList.remove(clazz);
    PsiCFGClass retVal = createPsiCFGClass(clazz, null, false);
    analysisUtil.parseFields(retVal);
    analysisUtil.parseMethods(retVal);
    for (PsiClass curClassRef : classList) {
      PsiCFGClass curCFGClass = createPsiCFGClass(curClassRef, null, false);
      analysisUtil.parseFields(curCFGClass);
      analysisUtil.parseMethods(curCFGClass);
    }
    retVal.setLibraryClass();
    return retVal;
  }

  private void retriveClassAndInnerClass(@NotNull ArrayList<PsiClass> retList,
                                         @NotNull PsiClass psiClass) {
    PsiClass[] innerClasses = psiClass.getInnerClasses();
    if (innerClasses.length == 0) {
      return;
    }
    for (PsiClass innerClazz : innerClasses) {
      retriveClassAndInnerClass(retList, innerClazz);
    }
    retList.add(psiClass);
  }


  public PsiCFGClass getOrCreateNestedClass(PsiClass nestedClass,
                                            PsiCFGClass parentCFGClass,
                                            PsiCFGMethod declaringMethod,
                                            BlockGraph declaringBlock) {
    PsiCFGClass currentNestedCFGClass =
      new PsiCFGClass(nestedClass, parentCFGClass.getDeclearingFile());

    currentNestedCFGClass.setNested();
    currentNestedCFGClass.setDeclaringCFGMethod(declaringMethod);
    currentNestedCFGClass.setDeclaringBlock(declaringBlock);

    if (nestedClass instanceof PsiAnonymousClass) {
      currentNestedCFGClass.setAnonlymous();
    }

    String className = nestedClass.getName();
    if (className == null) {
      className = "";
    }

    parentCFGClass.addNestedInnerClass(currentNestedCFGClass, className);

    workingList.addLast(currentNestedCFGClass);

    this.mAppPsiClassPsiCFGClassMap.put(nestedClass, currentNestedCFGClass);
    this.mAppClassNamePsiMap
      .put(currentNestedCFGClass.getQualifiedClassName(), currentNestedCFGClass);

    analysisUtil.parseFields(currentNestedCFGClass);
    analysisUtil.parseMethods(currentNestedCFGClass);

    return currentNestedCFGClass;
  }

  public PsiCFGClass createLambdaAnonymousClass(PsiLambdaExpression lambdaExpress,
                                                PsiClass parentInterface,
                                                PsiCFGClass declearingClass) {
    PsiCFGClass parentInterfaceCFGClass = getOrCreateCFGClass(parentInterface);
    //PsiCFGMethod[] methodsArray = parentInterfaceCFGClass.getAllMethods();
    PsiMethod overridedMethod = extractLambdaMethod(parentInterface);

    //Create the wrapper anonymous class
    PsiCFGClass wrapperClass = new PsiCFGClass(null, declearingClass.getDeclearingFile());
    wrapperClass.setAnonlymous();
    wrapperClass.setLambdaRef(lambdaExpress);
    //parentInterfaceCFGClass.addLambda(wrapperClass);
    declearingClass.addLambda(wrapperClass);
    wrapperClass.setDirectOverride(parentInterfaceCFGClass);

    PsiCFGMethod wrapperMethod = new PsiCFGMethod(lambdaExpress, overridedMethod, wrapperClass);
    wrapperClass.addMethod(wrapperMethod);
    mLambdaPsiCFGClassMap.put(lambdaExpress, wrapperClass);
    CFGUtil.constructMethodGraphForLambda(this, wrapperMethod);
    return wrapperClass;
  }

  public PsiMethod extractLambdaMethod(PsiClass parentInterface) {
    PsiMethod[] allMethods = parentInterface.getAllMethods();
    PsiMethod retMethod = null;
    for (PsiMethod m : allMethods) {
      if (m.getContainingClass().getQualifiedName().equals("java.lang.Object")) {
        continue;
      }
      if (retMethod == null) {
        retMethod = m;
      }
      else {
        PsiCFGDebugUtil.LOG.warning("More than 1 method in the lambda's parent Interface");
        PsiCFGDebugUtil.LOG.warning("Type: " + parentInterface.getQualifiedName());
      }
    }
    return retMethod;
  }
}
