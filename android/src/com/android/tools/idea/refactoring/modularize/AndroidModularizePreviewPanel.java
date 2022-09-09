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

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.nio.charset.Charset;
import java.util.*;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtFile;

public class AndroidModularizePreviewPanel {
  private static final Logger LOGGER = Logger.getInstance(AndroidModularizePreviewPanel.class);

  private JPanel myPanel;
  private JPanel myDependenciesPanel;
  private JBLabel myClassesCount;
  private JBLabel myResourcesCount;
  private JBLabel myMethodsCount;
  private JBLabel mySizeEstimate;

  private final AndroidCodeAndResourcesGraph myGraph;
  private final Map<PsiElement, UsageInfo> myLookupMap;
  private final CheckedTreeNode myRootNode = new CheckedTreeNode();
  private CodeAndResourcesTreeTable myTreeView;
  private final boolean myShouldSelectAllReferences;

  public AndroidModularizePreviewPanel(@NotNull AndroidCodeAndResourcesGraph graph, UsageInfo[] infos, boolean shouldSelectAllReferences) {
    myGraph = graph;
    myLookupMap = Maps.newHashMapWithExpectedSize(infos.length);
    for (UsageInfo info : infos) {
      myLookupMap.put(info.getElement(), info);
    }
    myShouldSelectAllReferences = shouldSelectAllReferences;
  }

  @NotNull
  public UsageInfo[] getSelectedUsages() {
    Set<UsageInfo> result = myTreeView.getCheckedNodes();
    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  public JPanel getPanel() {
    Set<PsiElement> parentElements = new HashSet<>();

    for (PsiElement root : myGraph.getRoots()) {
      UsageInfoTreeNode rootNode = new UsageInfoTreeNode(myLookupMap.get(root), 0);
      myRootNode.add(rootNode);
      parentElements.add(root);
      buildTree(rootNode, root, parentElements, myGraph.getReferencedOutsideScope());
      parentElements.remove(root);
    }

    CheckboxTree.CheckboxTreeCellRenderer renderer = new PreviewRenderer();
    GridConstraints constraints = new GridConstraints();
    constraints.setFill(GridConstraints.FILL_BOTH);

    ColumnInfo[] classesColumns = new ColumnInfo[]{new TreeColumnInfo("")}; // TODO: add method counts
    myTreeView = new CodeAndResourcesTreeTable(myRootNode, renderer, classesColumns);
    myDependenciesPanel.add(new JBScrollPane(myTreeView), constraints);

    myTreeView.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myTreeView.getTree().addTreeSelectionListener(e -> ApplicationManager.getApplication().invokeLater(this::updateCounts));

    updateCounts();
    return myPanel;
  }

  private void updateCounts() {
    int classesCount = 0;
    int methodsCount = 0;
    int resourcesCount = 0;
    long size = 0;

    for (UsageInfo usageInfo : myTreeView.getCheckedNodes()) {
      PsiElement psiElement = usageInfo.getElement();
      if (psiElement instanceof PsiFile) {
        if (psiElement instanceof KtFile) {
          methodsCount += ((KtFile)psiElement).getDeclarations().size();

          PsiClass[] classes = ((KtFile)psiElement).getClasses();
          classesCount += classes.length;
          for (PsiClass clazz : classes) {
            methodsCount += clazz.getMethods().length;
          }
        } else if (psiElement instanceof PsiJavaFile) {
          PsiClass[] classes = ((PsiJavaFile)psiElement).getClasses();
          classesCount += classes.length;
          for (PsiClass clazz : classes) {
            methodsCount += clazz.getMethods().length;
          }
        } else {
          resourcesCount++;
        }
        size += ((PsiFile)psiElement).getVirtualFile().getLength();
      }
      else if (psiElement instanceof XmlTag) {
        size += psiElement.getText().getBytes(Charset.defaultCharset()).length;
      }
      else {
        LOGGER.warn("Couldn't determine contribution for element " + psiElement);
      }
    }

    myClassesCount.setText(Integer.toString(classesCount));
    myMethodsCount.setText(Integer.toString(methodsCount));
    myResourcesCount.setText(Integer.toString(resourcesCount));
    mySizeEstimate.setText("~" + Long.toString(size / 1024) + " KB");
  }

  private void buildTree(CheckedTreeNode parentNode, PsiElement psiElement, Set<PsiElement> parentElements, Set<PsiElement> outsiders) {
    Set<PsiElement> references = myGraph.getTargets(psiElement);
    List<CheckedTreeNode> childrenNodes = new ArrayList<>(references.size());

    Map<ResourceReference, Set<PsiElement>> resourceGroups = new HashMap<>();

    for (PsiElement reference : references) {
      if (parentElements.contains(reference)) {
        continue; // We don't create nodes already present in our current path to the root (back-references).
      }

      // We want to pre-process the resource items in order to group them by resource URLs.
      if (myLookupMap.get(reference) instanceof ResourceXmlUsageInfo) {
        ResourceItem resourceItem = ((ResourceXmlUsageInfo)myLookupMap.get(reference)).getResourceItem();
        ResourceReference resourceReference = resourceItem.getReferenceToSelf();
        Set<PsiElement> otherItems = resourceGroups.computeIfAbsent(resourceReference, k -> new HashSet<>());
        otherItems.add(reference);
        continue; // Postpone node creation until we have all resources mapped out.
      }

      UsageInfoTreeNode childNode = new UsageInfoTreeNode(myLookupMap.get(reference), myGraph.getFrequency(psiElement, reference));
      childrenNodes.add(childNode);
      childNode.setChecked(myShouldSelectAllReferences || !outsiders.contains(reference));

      parentElements.add(reference);
      buildTree(childNode, reference, parentElements, outsiders);
      parentElements.remove(reference);
    }

    for (ResourceReference resourceReference : resourceGroups.keySet()) {
      ResourceUrlTreeNode urlTreeNode = new ResourceUrlTreeNode(resourceReference.getResourceUrl()); // TODO: namespaces
      childrenNodes.add(urlTreeNode);

      boolean checked = true;
      for (PsiElement reference : resourceGroups.get(resourceReference)) {
        if (!myShouldSelectAllReferences && outsiders.contains(reference)) {
          checked = false;
          break;
        }
      }

      for (PsiElement reference : resourceGroups.get(resourceReference)) {
        UsageInfoTreeNode childNode = new UsageInfoTreeNode(myLookupMap.get(reference), myGraph.getFrequency(psiElement, reference));
        childNode.setEnabled(false); // We don't allow selecting only a subset of resource items, either they all move or none of them move.
        childNode.setChecked(checked);
        urlTreeNode.add(childNode);

        parentElements.add(reference);
        buildTree(childNode, reference, parentElements, outsiders);
        parentElements.remove(reference);
      }
      urlTreeNode.setChecked(checked);
    }

    for (CheckedTreeNode node : childrenNodes) {
      parentNode.add(node);
    }
  }

  // TODO: Disabling a node should disable the children in its dominator tree (compute dominator tree in the handler).
  private static class PreviewRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof DependencyTreeNode) {
        ((DependencyTreeNode)value).render(getTextRenderer());
      }
    }
  }

  private static class CodeAndResourcesTreeTable extends TreeTableView {
    private final EventDispatcher<CheckboxTreeListener> myEventDispatcher;

    public CodeAndResourcesTreeTable(CheckedTreeNode root, CheckboxTree.CheckboxTreeCellRenderer renderer, final ColumnInfo[] columns) {
      super(new ListTreeTableModelOnColumns(root, columns));
      final TreeTableTree tree = getTree();
      myEventDispatcher = EventDispatcher.create(CheckboxTreeListener.class);
      CheckboxTreeBase.CheckPolicy selectionPolicy = new CheckboxTreeBase.CheckPolicy(true, true, true, false);
      CheckboxTreeHelper helper = new CheckboxTreeHelper(selectionPolicy, myEventDispatcher);
      helper.initTree(tree, this, renderer);
      tree.setSelectionRow(0);
      for (int i = 0; i < root.getChildCount(); i++) {
        tree.expandPath(new TreePath(((DefaultMutableTreeNode) root.getChildAt(i)).getPath()));
      }
    }

    public void addCheckboxTreeListener(@NotNull CheckboxTreeListener listener) {
      myEventDispatcher.addListener(listener);
    }

    public Set<UsageInfo> getCheckedNodes() {
      final Set<UsageInfo> nodes = new HashSet<>();

      new Object() {
        public void collect(CheckedTreeNode node) {
          if (!node.isChecked()) {
            return;
          }

          Object userObject = node.getUserObject();
          if (userObject instanceof UsageInfo) {
            nodes.add((UsageInfo)userObject);
          }
          if (!node.isLeaf()) {
            for (int i = 0; i < node.getChildCount(); i++) {
              final TreeNode child = node.getChildAt(i);
              if (child instanceof CheckedTreeNode) {
                collect((CheckedTreeNode)child);
              }
            }
          }
        }
      }.collect((CheckedTreeNode)getTree().getModel().getRoot());
      return nodes;
    }
  }
}
