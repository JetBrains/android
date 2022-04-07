/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.containers.Stack;

import java.util.*;

public class AndroidCodeAndResourcesGraph implements RefactoringUtil.Graph<PsiElement> {

  private final Set<PsiElement> myRoots;
  private final Map<PsiElement, Map<PsiElement, Integer>> myGraph;
  private final Set<PsiElement> myReferencedExternally;

  public AndroidCodeAndResourcesGraph(Map<PsiElement, Map<PsiElement, Integer>> graph,
                                      Set<PsiElement> roots,
                                      Set<PsiElement> referencedExternally) {
    myGraph = graph;
    myRoots = roots;
    myReferencedExternally = referencedExternally;
  }

  @Override
  public Set<PsiElement> getVertices() {
    return myGraph.keySet();
  }

  @Override
  public Set<PsiElement> getTargets(PsiElement source) {
    Map<PsiElement, Integer> targets = myGraph.get(source);
    return targets == null ? Collections.emptySet() : targets.keySet();
  }

  public Set<PsiElement> getRoots() {
    return myRoots;
  }

  public Set<PsiElement> getReferencedOutsideScope() {
    Stack<PsiElement> stack = new Stack<>();
    Set<PsiElement> visited = new HashSet<>(myGraph.size());

    for (PsiElement root : myReferencedExternally) {
      stack.push(root);
    }
    while (!stack.isEmpty()) {
      PsiElement current = stack.pop();
      if (visited.add(current)) {
        for (PsiElement succ : getTargets(current)) {
          stack.push(succ);
        }
      }
    }

    return visited;
  }

  public int getFrequency(PsiElement source, PsiElement target) {
    Map<PsiElement, Integer> targets = myGraph.get(source);
    if (targets == null) {
      return 0;
    }
    return targets.getOrDefault(target, 0);
  }


  public static class Builder {

    private final Set<PsiElement> myRoots = new HashSet<>();
    private final Map<PsiElement, Map<PsiElement, Integer>> myReferenceGraph = new HashMap<>();
    private final Set<PsiElement> myReferencedExternally = new HashSet<>();

    public void markReference(PsiElement source, PsiElement target) {
      Map<PsiElement, Integer> references = myReferenceGraph.computeIfAbsent(source, k -> new HashMap<>());
      Integer count = references.getOrDefault(target, 0);
      references.put(target, count + 1);
    }

    public void markReferencedOutsideScope(PsiElement elm) {
      if (!myRoots.contains(elm)) {
        myReferencedExternally.add(elm);
      }
    }

    public void addRoot(PsiElement root) {
      myRoots.add(root);
    }

    public AndroidCodeAndResourcesGraph build() {
      return new AndroidCodeAndResourcesGraph(myReferenceGraph, myRoots, myReferencedExternally);
    }
  }
}
