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
package com.android.tools.idea.apk.viewer.dex;

import com.android.annotations.VisibleForTesting;
import com.android.tools.apk.analyzer.dex.DexReferences;
import com.android.tools.apk.analyzer.dex.PackageTreeCreator;
import com.android.tools.apk.analyzer.dex.ProguardMappings;
import com.android.tools.apk.analyzer.dex.tree.*;
import com.android.tools.idea.concurrent.EdtExecutor;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;

public class ShowReferencesAction extends AnAction {
  @NotNull private final Tree myTree;
  @NotNull private final DexFileViewer myDexFileViewer;

  public ShowReferencesAction(@NotNull Tree tree, @NotNull DexFileViewer viewer) {
    super(ProjectBundle.message("find.usages.action.text"), ProjectBundle.message("find.usages.action.text"), AllIcons.Actions.Find);
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(), tree);
    myTree = tree;
    myDexFileViewer = viewer;
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    DexElementNode node = getSelectedNode();
    if (!canShowReferences(node)) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DexElementNode node = getSelectedNode();
    assert node != null; // action should've been disabled in this case
    Project project = getEventProject(e);

    ListenableFuture<DexReferences> references = myDexFileViewer.getDexReferences();
    assert references != null;

    Futures.addCallback(references, new FutureCallback<DexReferences>() {
      @Override
      public void onSuccess(@Nullable DexReferences result) {
        showReferenceTree(e, node, project, result);
      }

      @Override
      public void onFailure(Throwable t) {

      }
    }, EdtExecutor.INSTANCE);
  }

  private void showReferenceTree(AnActionEvent e, DexElementNode node, Project project, DexReferences references) {
    ProguardMappings proguardMappings = myDexFileViewer.getProguardMappings();
    final ProguardMap proguardMap = proguardMappings != null ? proguardMappings.map : null;
    final ProguardSeedsMap seedsMap = proguardMappings != null ? proguardMappings.seeds : null;
    final boolean deobfuscate = myDexFileViewer.isDeobfuscateNames();

    assert node.getReference() != null;
    Tree tree = new Tree(new DefaultTreeModel(references.getReferenceTreeFor(node.getReference(), true)));
    tree.setShowsRootHandles(true);
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        TreePath path = event.getPath();
        if (path.getLastPathComponent() instanceof DexElementNode) {
          DexElementNode node = (DexElementNode) path.getLastPathComponent();
          if (!DexReferences.isAlreadyLoaded(node)){
            node.removeAllChildren();
            assert node.getReference() != null;
            references.addReferencesForNode(node, true);
            node.sort(DexReferences.NODE_COMPARATOR);
          }
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {

      }
    });

    tree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DexElementNode node = (DexElementNode)value;
        Reference ref = node.getReference();

        boolean isSeed = node.isSeed(seedsMap, proguardMap, false);
        SimpleTextAttributes attr = new SimpleTextAttributes(
          isSeed ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN,
          null);

        ProguardMap usedProguardMap = deobfuscate ? proguardMap : null;

        if (ref instanceof TypeReference){
          TypeReference typeRef = (TypeReference)ref;
          append(PackageTreeCreator.decodeClassName(typeRef.getType(), usedProguardMap), attr);
        } else if (ref instanceof MethodReference){
          MethodReference methodRef = (MethodReference)ref;
          append(PackageTreeCreator.decodeClassName(methodRef.getDefiningClass(), usedProguardMap), attr);
          append(": ", attr);
          append(PackageTreeCreator.decodeClassName(methodRef.getReturnType(), usedProguardMap), attr);
          append(" ", attr);
          append(PackageTreeCreator.decodeMethodName(methodRef, usedProguardMap), attr);
          append(PackageTreeCreator.decodeMethodParams(methodRef, usedProguardMap), attr);
        } else if (ref instanceof FieldReference){
          FieldReference fieldRef = (FieldReference)ref;
          append(PackageTreeCreator.decodeClassName(fieldRef.getDefiningClass(), usedProguardMap), attr);
          append(": ", attr);
          append(PackageTreeCreator.decodeClassName(fieldRef.getType(), usedProguardMap), attr);
          append(" ", attr);
          append(PackageTreeCreator.decodeFieldName(fieldRef, usedProguardMap), attr);
        }
        setIcon(DexNodeIcons.forNode(node));
      }
    });

    JBScrollPane pane = new JBScrollPane(tree);
    pane.setPreferredSize(new Dimension(600, 400));
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(pane, null)
      .setProject(project)
      .setDimensionServiceKey(project, ShowReferencesAction.class.getName(), false)
      .setResizable(true)
      .setMovable(true)
      .setTitle("References to " + node.getName())
      .createPopup();
    popup.showInBestPositionFor(e.getDataContext());
  }

  @Nullable
  private DexElementNode getSelectedNode() {
    TreePath path = myTree.getSelectionPath();
    Object component = path.getLastPathComponent();
    return component instanceof DexElementNode ? (DexElementNode)component : null;
  }

  @VisibleForTesting
  static boolean canShowReferences(@Nullable DexElementNode node) {
    if (node == null || node.getReference() == null) {
      return false;
    }

    if (!(node instanceof DexClassNode) && !(node instanceof DexMethodNode) && !(node instanceof DexFieldNode)) {
      return false;
    }

    return true;
  }
}
