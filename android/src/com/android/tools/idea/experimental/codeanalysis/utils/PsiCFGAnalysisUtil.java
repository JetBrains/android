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
package com.android.tools.idea.experimental.codeanalysis.utils;

import com.android.tools.idea.experimental.codeanalysis.PsiCFGScene;
import com.android.tools.idea.experimental.codeanalysis.callgraph.Callgraph;
import com.android.tools.idea.experimental.codeanalysis.callgraph.CallgraphBuilder;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGField;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.MethodGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

public class PsiCFGAnalysisUtil {
  private PsiCFGScene mScene;
  private PsiClass mLangOjectClass;

  public PsiCFGAnalysisUtil(PsiCFGScene scene) {
    this.mScene = scene;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(mScene.getProject());
    mLangOjectClass = facade.findClass("java.lang.Object",
                                       GlobalSearchScope.allScope(mScene.getProject()));
  }

  /**
   * The purpose of the Stage0 is to load the classes that are referred by
   * the user's code by looking up the import table.
   *
   * Currently reserved. Do not have a reliable way to list all available
   * classes from an IntelliJ project.
   */
  public void performStage0() {

  }

  /**
   * The purpose of the Stage1 is scan the project java files and
   * create wrapper objects for the Fields and Methods.
   */
  public void performStage1() {
    PsiCFGClass[] appClasses = mScene.getAllApplicationClasses();
    for (PsiCFGClass curClass : appClasses) {
      parseFields(curClass);
      parseMethods(curClass);
    }
  }

  /**
   * The purpose of the Stage 2 is to put the class hierarchy
   * info into the Application PsiCFGClasses.
   */
  public void performStage2() {
    PsiCFGClass[] appClasses = mScene.getAllApplicationClasses();
    for (PsiCFGClass cfgClazz : appClasses) {
      setClassHierarchyForApplicationClass(cfgClazz);
    }
  }

  /**
   * The purpose of the Stage3 is create IntraProcedural
   * CFG for the methods and lambdas inside the app
   * class, including the constructor and the init code
   */
  public void performStage3() {
    PsiCFGClass[] appClasses = mScene.getAllApplicationClasses();

    mScene.workingList.clear();
    mScene.workingList.addAll(Arrays.asList(appClasses));

    while (!mScene.workingList.isEmpty()) {
      //While the working list is not empty
      //Process the working list
      PsiCFGClass currentClass = mScene.workingList.removeFirst();
      PsiCFGMethod[] allMethods = currentClass.getAllMethods();

      for (PsiCFGMethod currentMethod : allMethods) {
        //Abstract method does not have a body
        //Lambda methods' CFG is created by the time it is decleared
        if (currentMethod.isAbstract() || currentMethod.isLambda()) {
          continue;
        }

        PsiMethod methodRef = currentMethod.getMethodRef();
        if (methodRef != null) {
          PsiCodeBlock codeBlock = methodRef.getBody();

          if (codeBlock == null) {
            PsiCFGDebugUtil.LOG.info("In " + currentClass.getQualifiedClassName() + "."
                                     + currentMethod.getName() + "Code block is null");
            continue;
          }

          MethodGraph cfg = CFGUtil.constructMethodGraph(mScene, codeBlock, currentMethod);
          currentMethod.setControlFlowGraph(cfg);
        }
      }
    }
  }

  /**
   * Because the list of library classes is not complete
   * before the construction of control flow graph in the Stage 2,
   * this stage is created to put the class hierarchy info
   * into the PsiCFGClasses
   */
  public void performStage4() {
    PsiCFGClass[] libraryClasses = mScene.getAllLibraryClasses();
    Set<PsiClass> LibraryClassSet = mScene.getAllLibraryClassPsiSet();
    for (PsiCFGClass curLibClass : libraryClasses) {
      PsiClass psiRef = curLibClass.getPsiClass();
      if (psiRef == null) {
        continue;
      }

      PsiClass superRef = psiRef.getSuperClass();
      PsiClass[] interfaceRefs = psiRef.getInterfaces();
      if (LibraryClassSet.contains(superRef)) {
        PsiCFGClass superCFGClass = mScene.getOrCreateCFGClass(superRef);
        curLibClass.setSuperClass(superCFGClass);
        superCFGClass.addSubClass(curLibClass);
      }
      setInterfacesIfInLibrary(curLibClass, interfaceRefs, LibraryClassSet);
    }
  }

  /**
   * In this stage, callgraph is being built.
   * The callgraph will be saved in current
   * scene.
   */
  public void performStage5() {
    CallgraphBuilder cgBuilder = new CallgraphBuilder(mScene, this);
    cgBuilder.build();
    Callgraph cg = cgBuilder.getCallGraph();

    mScene.setCallGraph(cg);
    CFGUtil.outputCallGraphDotFile(cg);
  }

  /**
   * Analysis Stage
   * Run analysis on CFG and Call Graph
   */
  public void performStage6() {
    //AnalysisClientBase analysis = new LocationPermissionExperimentClient(mScene);
    //analysis.runAnalysis();
  }

  /**
   * Print out summary information about
   * Application class and Library Class
   * For debug purpose only.
   */
  public void summarizeStage() {
    PsiCFGClass[] applicationClasses = mScene.getAllApplicationClasses();
    PsiCFGClass[] libraryClasses = mScene.getAllLibraryClasses();
    PsiCFGClass[] lamdbdaClasses = mScene.getAllLambdaClass();
    GraphNode[] invocationNodes = mScene.getAllInvocationNode();

    System.out.println("Application Classes: ");
    printCFGClassArray(applicationClasses);
    System.out.println();

    System.out.println("Library Classes: ");
    printCFGClassArray(libraryClasses);
    System.out.println();

    System.out.println("Lambda Classes: ");
    printCFGClassArray(lamdbdaClasses);
    System.out.println();

    //mScene.get
    System.out.println("InvocationNodes: " + invocationNodes.length);

  }

  public void setInterfacesIfInLibrary(PsiCFGClass curLibraryClass,
                                       PsiClass[] interfazes,
                                       Set<PsiClass> interfaceSet) {
    for (PsiClass curInterface : interfazes) {
      if (interfaceSet.contains(curInterface)) {
        PsiCFGClass curCFGInterface = mScene.getOrCreateCFGClass(curInterface);
        curLibraryClass.setSuperClass(curCFGInterface);
        curCFGInterface.addSubClass(curLibraryClass);
      }
    }
  }

  public void printCFGClassArray(PsiCFGClass[] classArray) {
    for (int i = 0; i < classArray.length; i++) {
      System.out.println(classArray[i].getQualifiedClassName());
    }
  }

  public void setClassHierarchyForApplicationClass(@NotNull PsiCFGClass clazz) {

    if (clazz.getPsiClass() != null) {
      PsiClass psiClassRef = clazz.getPsiClass();
      PsiClass directSuperClass = psiClassRef.getSuperClass();
      PsiClass[] implementedInterface = psiClassRef.getInterfaces();

      if (directSuperClass == null && mLangOjectClass != null) {
        PsiCFGDebugUtil.LOG.warning("Super class is null for class "
                                    + clazz.getQualifiedClassName());
        directSuperClass = mLangOjectClass;
      }

      if (directSuperClass != null) {
        PsiCFGClass directCFGSuperClass = mScene.getOrCreateCFGClass(directSuperClass);
        //Set superClass
        clazz.setSuperClass(directCFGSuperClass);
        directCFGSuperClass.addSubClass(clazz);
      }

      for (PsiClass interfaze : implementedInterface) {

        PsiCFGClass cfgInterfaze = mScene.getOrCreateCFGClass(interfaze);
        clazz.addInterface(cfgInterfaze);
        if (clazz.isInterface()) {
          cfgInterfaze.addSubInterface(clazz);
        }
        else {
          cfgInterfaze.addSubClass(clazz);
        }
      }
    }
  }

  /**
   * The purpose of this method is to create wrapper class for all fields and
   * methods and its modifiers and annotations
   */
  public void parseFields(@NotNull PsiCFGClass clazz) {
    PsiField[] fields = clazz.getPsiClass().getFields();
    for (PsiField curField : fields) {
      PsiCFGField curCFGField = new PsiCFGField(curField, clazz);
      clazz.addField(curCFGField);
    }
  }

  public void parseMethods(@NotNull PsiCFGClass clazz) {
    PsiMethod[] methods = clazz.getPsiClass().getMethods();

    for (PsiMethod curMethod : methods) {
      PsiCFGMethod curCFGMethod = new PsiCFGMethod(curMethod, clazz);
      clazz.addMethod(curCFGMethod);
    }
  }
}
