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
package com.android.tools.idea.experimental;

import com.android.tools.idea.experimental.actions.PermissionUsageQuickFix;
import com.android.tools.idea.experimental.codeanalysis.PsiCFGScene;
import com.android.tools.idea.experimental.codeanalysis.callgraph.Callgraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGPartialMethodSignature;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGPartialMethodSignatureBuilder;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.AssignStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.Stmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.android.tools.lint.detector.api.Category;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PermissionUsageInspection extends GlobalInspectionTool {


  private static final String LOCATION_MANAGER_CLASS_NAME = "android.location.LocationManager";
  private static final String GOOGLE_MAPS_API_CLASS = "com.google.android.maps.MyLocationOverlay";

  private static final String PROBLEM_DESC = "Permission not checked for statement :";

  private PsiCFGClass LocationManagerCFGClass;
  private PsiCFGClass GoogleMapsAPIClass;

  private List<PsiCFGMethod> targetMethodList;
  private Project mProject;
  private PsiCFGScene mScene;
  private Callgraph mCG;

  private List<PsiCFGMethod> longestMethodStack;
  private List<GraphNode> longestNodeStack;

  private List<Pair<PsiCFGMethod, PsiElement>> invocationSiteCollection;

  private Map<PsiMethod, PsiElement> taggedMethodsWithElement;

  public final String DISPLAY_NAME = "Permission Check for Location APIs";
  public final String SHORT_NAME = "PermissionUsageInspection";



  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.lint.inspections.group.name");
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return ArrayUtil.mergeArrays(new String[]{
      AndroidBundle.message("android.inspections.group.name"),
      AndroidBundle.message("android.lint.inspections.subgroup.name"),
      Category.CORRECTNESS.getName()
    });
  }

  @Override
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public boolean isGraphNeeded() {
    //TODO: Reference resolve is not necessary. However, it is not known if
    //the output would stay the same if Reference Graph is not created.
    return true;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope, @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {

    mProject = globalContext.getProject();
    CodeAnalysisMain analysisMain = CodeAnalysisMain.getInstance(mProject);
    analysisMain.analyze(scope);
    mScene = PsiCFGScene.getInstance(mProject);
    mCG = mScene.getCallGraph();

    targetMethodList = Lists.newArrayList();
    LocationManagerCFGClass = null;
    GoogleMapsAPIClass = null;
    invocationSiteCollection = Lists.newArrayList();
    taggedMethodsWithElement = Maps.newHashMap();

    runAnalysis();

    super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);

  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {

    if (refEntity instanceof RefMethod) {
      PsiModifierListOwner methodRefRAW = ((RefMethod)refEntity).getElement();
      if (methodRefRAW != null && (methodRefRAW instanceof PsiMethod)) {
        PsiMethod methodRef = (PsiMethod) methodRefRAW;
        return checkPsiMethod(methodRef, manager);
      } else {
        //System.out.println("RefMethod for " + refEntity.getName() + " Not resolved to PsiMethod");
      }
    }
    return null;
  }

  private CommonProblemDescriptor[] checkPsiMethod(PsiMethod method, InspectionManager manager) {
    if (!taggedMethodsWithElement.containsKey(method)) {
      return null;
    }

    List<CommonProblemDescriptor> retList = Lists.newArrayList();
    PsiElement invocationStmt = taggedMethodsWithElement.get(method);
    ProblemDescriptor desc = manager.createProblemDescriptor(invocationStmt,
                                    PROBLEM_DESC,
                                    true,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING ,
                                    false,
                                    new PermissionUsageQuickFix(invocationStmt));
    retList.add(desc);
    return retList.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
  }



  public void runAnalysis() {
    getTargetMethodsList();

    if (targetMethodList.isEmpty()) {
      //System.out.println("No Location API used in this project.");
      return;
    }

    for (PsiCFGMethod currentTarget : targetMethodList) {
      resolveInitialCaller(currentTarget);
    }
    //outputInvocationSiteInfos();
    tagTheResult();
  }

  public void outputInvocationSiteInfos() {
    for (Pair<PsiCFGMethod, PsiElement> singleInvoke : invocationSiteCollection) {
      PsiCFGMethod currentMethod = singleInvoke.getFirst();
      PsiElement currentElement = singleInvoke.getSecond();
      if (currentElement != null) {
        //System.out.println(String.format("In %s method, Invoke Element %s", currentMethod.getName(), currentElement.getText()));
      }
    }
  }

  private void resolveInitialCaller(PsiCFGMethod method) {
    Set<PsiCFGMethod> calledMethods = Sets.newHashSet();
    //Stack<PsiCFGMethod> callStack = new Stack<>();
    Stack<GraphNode> nodeStack = new Stack<>();
    Stack<PsiCFGMethod> methodStack = new Stack<>();
    longestMethodStack = Lists.newArrayList();
    longestNodeStack = Lists.newArrayList();

    if ((!mCG.calleeMethodToCallerMethodReturnMap.containsKey(method)) &&
        (!mCG.callerMethodToCalleeMethodMap.containsKey(method))) {
      return;
    }
    dfsFindCallChain(nodeStack,
                     methodStack,
                     null,
                     method);



    for (int i = 0; i < longestMethodStack.size(); i++) {
      GraphNode currentNode = longestNodeStack.get(i);
      PsiCFGMethod currentMethod = longestMethodStack.get(i);
    }
    PsiCFGMethod topMethod = longestMethodStack.get(longestMethodStack.size() - 1);
    GraphNode topNode = longestNodeStack.get(longestNodeStack.size() - 1);
    PsiElement psiRef = extractPsiElement(topNode);
    invocationSiteCollection.add(new Pair<>(topMethod, psiRef));
  }

  private PsiElement extractPsiElement(GraphNode node) {
    Stmt invocationStatement = node.getStatements()[0];
    if (invocationStatement instanceof AssignStmt) {
      Value rOP = ((AssignStmt) invocationStatement).getROp();
      return rOP.getPsiRef();
    }
    return null;
  }

  private void dfsFindCallChain(
    Stack<GraphNode> nodeStack,
    Stack<PsiCFGMethod> methodStack,
    GraphNode node,
    PsiCFGMethod target) {


    if ((methodStack.size() > 5) || methodStack.contains(target)) {
      if (longestMethodStack.size() < methodStack.size()) {
        longestNodeStack = Lists.newArrayList(nodeStack);
        longestMethodStack = Lists.newArrayList(methodStack);
      }
      return;
    }

    methodStack.push(target);
    nodeStack.push(node);

    if (mCG.calleeMethodToCallerGraphNodeMap.containsKey(target)) {
      Collection<GraphNode> invocationSites = mCG.calleeMethodToCallerGraphNodeMap.get(target);
      for (GraphNode nextTarget: invocationSites) {
        PsiCFGMethod targetMethod = mCG.getNodesParentMethod(nextTarget);
        if (targetMethod != null) {
          dfsFindCallChain(nodeStack, methodStack, nextTarget, targetMethod);
        }
      }
    } else {
      //Top
      if (longestMethodStack.size() < methodStack.size()) {
        longestNodeStack = Lists.newArrayList(nodeStack);
        longestMethodStack = Lists.newArrayList(methodStack);
      }
    }
  }

  private void getTargetMethodsListFromPsiClass(@NotNull PsiClass clazz) {
    PsiMethod[] methodsArray = clazz.getMethods();
    methodsArray = removeMethodsRequireNoPermission(methodsArray);
    PsiCFGClass cfgClazz = mScene.getPsiCFGClass(clazz);
    if (cfgClazz == null) {
      return;
    }

    for (PsiMethod currentMethod : methodsArray) {
      PsiCFGPartialMethodSignature signature =
        PsiCFGPartialMethodSignatureBuilder.buildFromPsiMethod(currentMethod);
      PsiCFGMethod cfgMethod = cfgClazz.getMethod(signature);
      if (cfgMethod != null) {
        targetMethodList.add(cfgMethod);
      }
    }
  }

  private void getTargetMethodsList() {
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(mProject);

    PsiClass locationClass = javaPsiFacade.findClass(
      LOCATION_MANAGER_CLASS_NAME, GlobalSearchScope.allScope(mProject));
    PsiClass googleMapsClass = javaPsiFacade.findClass(
      GOOGLE_MAPS_API_CLASS, GlobalSearchScope.allScope(mProject)
    );

    if (locationClass == null && googleMapsClass == null) {
      return;
    }

    if (locationClass != null) {
      getTargetMethodsListFromPsiClass(locationClass);
    }

    if (googleMapsClass != null) {
      getTargetMethodsListFromPsiClass(googleMapsClass);
    }

  }

  @Nullable
  @Override
  public String getStaticDescription() {
    return "Description is under construction";
  }

  //TODO: Add annotaction check
  private PsiMethod[] removeMethodsRequireNoPermission(PsiMethod[] methodsArray) {
    return methodsArray;
  }

  private void tagTheResult() {
    //InspectionManager iManager = InspectionManager.getInstance(mProject);
    for (Pair<PsiCFGMethod, PsiElement> invocationSite : invocationSiteCollection) {
      PsiElement element = invocationSite.getSecond();
      if (element instanceof PsiMethodCallExpression) {
        PsiElement methodRefRAW = invocationSite.getFirst().getMethodRef();
        if (methodRefRAW != null && (methodRefRAW instanceof PsiMethod)) {
          PsiMethod methodRef = (PsiMethod) methodRefRAW;
          taggedMethodsWithElement.put(methodRef, element);
        }
      }
    }
  }
}
