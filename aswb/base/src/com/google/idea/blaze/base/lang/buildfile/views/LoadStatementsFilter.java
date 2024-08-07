/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.lang.buildfile.views;

import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.util.PlatformIcons;

/** Creates a Structure view filter for load statements in BUILD/WORKSPACE/bzl files. */
public class LoadStatementsFilter implements Filter {
  public static final String ID = "SHOW_LOAD_STATEMENTS";

  @Override
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof BuildStructureViewElement) {
      BuildStructureViewElement buildElement = (BuildStructureViewElement) treeNode;
      return !(buildElement.getElement() instanceof LoadStatement);
    }
    return true;
  }

  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(
        /*text=*/ "Show Load Statements",
        /*description=*/ null,
        /*icon=*/ PlatformIcons.IMPORT_ICON);
  }

  @Override
  public String getName() {
    return ID;
  }

  @Override
  public boolean isReverted() {
    return true;
  }
}
