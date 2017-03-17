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

import com.android.tools.idea.apk.viewer.dex.tree.DexElementNode;
import com.android.tools.idea.apk.viewer.dex.tree.DexFieldNode;
import com.android.tools.idea.apk.viewer.dex.tree.DexMethodNode;

import java.util.function.Predicate;

public class DexViewFilters implements Predicate<DexElementNode> {
  private boolean myShowMethods = true;
  private boolean myShowFields = true;
  private boolean myShowReferencedNodes = true;
  private boolean myShowRemovedNodes = false;

  public void setShowMethods(boolean showMethods) {
    myShowMethods = showMethods;
  }

  public void setShowFields(boolean showFields) {
    myShowFields = showFields;
  }

  public void setShowReferencedNodes(boolean showReferencedNodes) {
    myShowReferencedNodes = showReferencedNodes;
  }

  public void setShowRemovedNodes(boolean showRemovedNodes) {
    myShowRemovedNodes = showRemovedNodes;
  }

  public boolean isShowMethods() {
    return myShowMethods;
  }

  public boolean isShowFields() {
    return myShowFields;
  }

  public boolean isShowReferencedNodes() {
    return myShowReferencedNodes;
  }

  public boolean isShowRemovedNodes() {
    return myShowRemovedNodes;
  }

  @Override
  public boolean test(DexElementNode node) {
    return ((myShowFields || !(node instanceof DexFieldNode))
            && (myShowMethods || !(node instanceof DexMethodNode))
            && (myShowReferencedNodes || node.hasClassDefinition())
            && (myShowRemovedNodes || !node.isRemoved()));
  }
}
