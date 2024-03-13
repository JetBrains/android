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

import com.android.tools.apk.analyzer.dex.KeepRuleBuilder;
import com.android.tools.apk.analyzer.dex.tree.DexClassNode;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import com.android.tools.apk.analyzer.dex.tree.DexFieldNode;
import com.android.tools.apk.analyzer.dex.tree.DexMethodNode;
import com.android.tools.apk.analyzer.dex.tree.DexPackageNode;
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.Tree;
import javax.swing.JComponent;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenerateProguardKeepRuleAction extends AnAction {
  @NotNull private final Tree myTree;


  public GenerateProguardKeepRuleAction(@NotNull Tree tree) {
    super("Generate Proguard keep rule", "Generates Proguard keep rule", AllIcons.Actions.Download);
    myTree = tree;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }


  @NotNull
  @VisibleForTesting
  static String getKeepRule(@NotNull DexElementNode node) {
    StringBuilder keepRule = new StringBuilder();
    keepRule.append(KeepRuleBuilder.KEEP_PREAMBLE);

    if (node instanceof DexPackageNode) {
      KeepRuleBuilder ruleBuilder = new KeepRuleBuilder();
      ruleBuilder.setPackage(((DexPackageNode)node).getPackageName());
      keepRule.append("# keep everything in this package from being removed or renamed\n");
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEP));
      keepRule.append("\n\n");
      keepRule.append("# keep everything in this package from being renamed only\n");
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEPNAMES));
    }
    else if (node instanceof DexMethodNode || node instanceof DexFieldNode) {
      DexClassNode classNode = (DexClassNode)node.getParent();
      DexPackageNode packageNode = (DexPackageNode)classNode.getParent();
      KeepRuleBuilder ruleBuilder = new KeepRuleBuilder();
      ruleBuilder.setPackage(packageNode.getPackageName() == null ? "" : packageNode.getPackageName())
        .setClass(classNode.getName())
        .setMember(node.getName());
      keepRule.append(KeepRuleBuilder.KEEP_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEP));
      keepRule.append("\n\n");
      keepRule.append(KeepRuleBuilder.KEEPCLASSMEMBERS_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEPCLASSMEMBERS));
      keepRule.append("\n\n");
      keepRule.append(KeepRuleBuilder.KEEPNAMES_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEPNAMES));
      keepRule.append("\n\n");
      keepRule.append(KeepRuleBuilder.KEEPCLASSMEMBERNAMES_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEPCLASSMEMBERNAMES));
      keepRule.append("\n\n");
    }
    else if (node instanceof DexClassNode) {
      DexPackageNode packageNode = (DexPackageNode)node.getParent();
      KeepRuleBuilder ruleBuilder = new KeepRuleBuilder();
      ruleBuilder.setPackage(packageNode.getPackageName() == null ? "" : packageNode.getPackageName())
        .setClass(node.getName());
      keepRule.append(KeepRuleBuilder.KEEP_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEP));
      keepRule.append("\n\n");
      keepRule.append(KeepRuleBuilder.KEEPCLASSMEMBERS_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEPCLASSMEMBERS));
      keepRule.append("\n\n");
      keepRule.append(KeepRuleBuilder.KEEPNAMES_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEPNAMES));
      keepRule.append("\n\n");
      keepRule.append(KeepRuleBuilder.KEEPCLASSMEMBERNAMES_RULE);
      keepRule.append(ruleBuilder.build(KeepRuleBuilder.KeepType.KEEPCLASSMEMBERNAMES));
      keepRule.append("\n\n");
    }
    return keepRule.toString();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    DexElementNode node = getSelectedNode();

    if (!canGenerateRule(node)) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }

  @VisibleForTesting
  static boolean canGenerateRule(DexElementNode node) {
    if (!(node instanceof DexPackageNode
          || node instanceof DexClassNode
          || node instanceof DexMethodNode
          || node instanceof DexFieldNode)) {
      return false;
    }

    if (node instanceof DexPackageNode && ((DexPackageNode)node).getPackageName() == null) {
      return false;
    }

    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DexElementNode node = getSelectedNode();
    assert node != null; // action should've been disabled in this case
    Project project = getEventProject(e);

    String keepRule = getKeepRule(node);

    final EditorFactory factory = EditorFactory.getInstance();
    final Document doc = ((EditorFactoryImpl)factory).createDocument(keepRule, true, false);
    doc.setReadOnly(true);
    Editor editor = factory.createEditor(doc, project);

    EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
    final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ProguardR8FileType.INSTANCE, project, null);
    ((EditorEx)editor).setHighlighter(
      editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme()));
    ((EditorEx)editor).setCaretVisible(true);

    final EditorSettings settings = editor.getSettings();
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);

    editor.setBorder(null);
    JComponent editorComponent = editor.getComponent();

    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(editorComponent, null)
      .setProject(project)
      .setDimensionServiceKey(project, GenerateProguardKeepRuleAction.class.getName(), false)
      .setResizable(true)
      .setMovable(true)
      .setTitle("Proguard keep rules for " + node.getName())
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup();
    Disposer.register(popup, () -> EditorFactory.getInstance().releaseEditor(editor));

    popup.showInBestPositionFor(e.getDataContext());
  }

  @Nullable
  private DexElementNode getSelectedNode() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    Object component = path.getLastPathComponent();
    return component instanceof DexElementNode ? (DexElementNode)component : null;
  }
}
