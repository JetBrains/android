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
import com.android.tools.apk.analyzer.dex.DexDisassembler;
import com.android.tools.apk.analyzer.dex.DexFiles;
import com.android.tools.apk.analyzer.dex.tree.DexClassNode;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import com.android.tools.apk.analyzer.dex.tree.DexMethodNode;
import com.android.tools.idea.ddms.EdtExecutor;
import com.google.common.util.concurrent.*;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.util.ReferenceUtil;

import javax.swing.tree.TreePath;
import java.io.IOException;
import java.nio.file.Path;

public class ShowDisassemblyAction extends AnAction implements DumbAware {
  private final Tree myTree;

  public ShowDisassemblyAction(@NotNull Tree tree) {
    super("Show Bytecode", "Show Bytecode", AllIcons.Toolwindows.Documentation);
    myTree = tree;
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    DexElementNode node = getSelectedNode();
    if (!canDisassemble(node)) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }

  @VisibleForTesting
  static boolean canDisassemble(@Nullable DexElementNode node) {
    if (node == null) {
      return false;
    }

    if (!(node instanceof DexClassNode) && !(node instanceof DexMethodNode)) {
      return false;
    }

    if (!node.isDefined()) {
      return false;
    }

    if (!(node.getUserObject() instanceof Path)){
      return false;
    }

    return true;
  }

  @Nullable
  private DexElementNode getSelectedNode() {
    TreePath path = myTree.getSelectionPath();
    Object component = path.getLastPathComponent();
    return component instanceof DexElementNode ? (DexElementNode)component : null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DexElementNode node = getSelectedNode();
    assert node != null; // action should've been disabled in this case

    Project project = getEventProject(e);
    assert project != null;
    ListeningExecutorService pooledThreadExecutor = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);
    Path dexPath = (Path)node.getUserObject();
    ListenableFuture<DexBackedDexFile> dexFileFuture = pooledThreadExecutor.submit(() -> DexFiles.getDexFile(dexPath));
    Futures.addCallback(dexFileFuture, new FutureCallback<DexBackedDexFile>() {
      @Override
      public void onSuccess(@Nullable DexBackedDexFile dexBackedDexFile) {
        assert dexBackedDexFile != null;

        String byteCode;
        try {
          byteCode = getByteCode(dexBackedDexFile, node);
        } catch (Exception ex) {
          Messages.showErrorDialog(project, "Unable to get byte code: " + ex.getMessage(), "View Dex Bytecode");
          return;
        }

        DexCodeViewer component = new DexCodeViewer(project, byteCode);

        JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
          .setProject(project)
          .setDimensionServiceKey(project, ShowDisassemblyAction.class.getName(), false)
          .setResizable(true)
          .setMovable(true)
          .setTitle("DEX Byte Code for " + node.getName())
          .createPopup();
        Disposer.register(popup, component);

        popup.showInBestPositionFor(e.getDataContext());
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        Messages.showErrorDialog("Error constructing dex file: " + t, "View Dex Bytecode");
      }
    }, EdtExecutor.INSTANCE);

  }

  @VisibleForTesting
  @NotNull
  static String getByteCode(@NotNull DexBackedDexFile dex, @NotNull DexElementNode node) {
    if (node instanceof DexMethodNode) {
      return getByteCodeForMethod(dex, (DexMethodNode)node);
    } else if (node instanceof DexClassNode) {
      return getByteCodeForClass(dex, (DexClassNode)node);
    }
    throw new RuntimeException("Disassembly only available for methods and classes defined in this dex file");
  }

  @NotNull
  private static String getByteCodeForMethod(@NotNull DexBackedDexFile dex, @NotNull DexMethodNode node) {
    MethodReference desc = node.getReference();
    if (desc == null) {
      throw new RuntimeException("Unable to identify method descriptor for " + node.getName());
    }

    try {
      DexClassNode parent = (DexClassNode) node.getParent();
      assert parent != null : "Method node must have a parent class";
      return new DexDisassembler(dex).disassembleMethod(DebuggerUtilsEx.signatureToName(parent.getReference().getType()),
                                                        ReferenceUtil.getMethodDescriptor(desc));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static String getByteCodeForClass(@NotNull DexBackedDexFile dex, @NotNull DexClassNode node) {
    String fqcn = DebuggerUtilsEx.signatureToName(node.getReference().getType());
    if (fqcn == null) {
      throw new RuntimeException("Unable to get the fully qualified class name for " + node);
    }

    try {
      return new DexDisassembler(dex).disassembleClass(fqcn);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
