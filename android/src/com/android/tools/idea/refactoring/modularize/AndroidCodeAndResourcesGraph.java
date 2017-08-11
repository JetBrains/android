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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.containers.Stack;

import java.util.*;

public class AndroidCodeAndResourcesGraph implements RefactoringUtil.Graph<PsiElement> {

  // Synthetic root for the dominator tree
  private static final PsiElement SENTINEL_ROOT = new FakePsiElement() {
    @Override
    public PsiElement getParent() {
      return null;
    }
  };

  private final Set<PsiElement> myRoots;
  private final Map<PsiElement, Map<PsiElement, Integer>> myGraph;
  private final Map<PsiElement, Set<PsiElement>> myReverseGraph;
  private final Set<PsiElement> myReferencedExternally;

  private final Map<PsiElement, PsiElement> myImmediateDominator; // Dominator tree (in reverse)

  public AndroidCodeAndResourcesGraph(Map<PsiElement, Map<PsiElement, Integer>> graph,
                                      Set<PsiElement> roots,
                                      Set<PsiElement> referencedExternally) {
    myGraph = graph;
    myRoots = roots;
    myReferencedExternally = referencedExternally;
    myReverseGraph = Maps.newHashMapWithExpectedSize(graph.size());
    for (PsiElement node : myGraph.keySet()) {
      for (PsiElement succ : myGraph.get(node).keySet()) {
        Set<PsiElement> predecessors = myReverseGraph.get(succ);
        if (predecessors == null) {
          predecessors = new HashSet<>();
        }
        predecessors.add(node);
        myReverseGraph.put(succ, predecessors);
      }
    }
    myImmediateDominator = Maps.newHashMapWithExpectedSize(myGraph.size());
    for (PsiElement node : roots) {
      myImmediateDominator.put(node, SENTINEL_ROOT);
    }
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

  public Map<PsiElement, PsiElement> computeDominators() {
    List<PsiElement> sorted = computeTopologicalSort();

    boolean changed = true;  // We need to iterate on the dominator computation because the graph may contain cycles.
    while (changed) {
      changed = false;
      for (PsiElement node : sorted) {
        // Root nodes and nodes immediately dominated by the SENTINEL_ROOT are skipped.
        if (myImmediateDominator.get(node) != SENTINEL_ROOT) {
          PsiElement dominator = null;

          for (PsiElement predecessor : myReverseGraph.get(node)) {
            if (myImmediateDominator.get(predecessor) == null) {
              // If we don't have a dominator/approximation for predecessor, skip it
              continue;
            }
            if (dominator == null) {
              dominator = predecessor;
            }
            else {
              PsiElement fingerA = dominator;
              PsiElement fingerB = predecessor;
              while (fingerA != fingerB) {
                if (sorted.indexOf(fingerA) < sorted.indexOf(fingerB)) {
                  fingerB = myImmediateDominator.get(fingerB);
                }
                else {
                  fingerA = myImmediateDominator.get(fingerA);
                }
              }
              dominator = fingerA;
            }
          }

          if (myImmediateDominator.get(node) != dominator) {
            myImmediateDominator.put(node, dominator);
            changed = true;
          }
        }
      }
    }
    return myImmediateDominator;
  }

  private List<PsiElement> computeTopologicalSort() {
    Stack<PsiElement> stack = new Stack<>();
    Stack<PsiElement> head = new Stack<>();
    Set<PsiElement> visited = new HashSet<>();
    List<PsiElement> sorted = new ArrayList<>(myGraph.size());

    for (PsiElement root : myRoots) {
      stack.push(root);
    }
    while (!stack.isEmpty()) {
      PsiElement current = stack.peek();
      if (!head.isEmpty() && current == head.peek()) {
        stack.pop();
        head.pop();
        sorted.add(current);
      }
      else {
        head.push(current);
        for (PsiElement succ : getTargets(current)) {
          if (visited.add(succ)) {
            stack.push(succ);
          }
        }
      }
    }
    return Lists.reverse(sorted);
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
