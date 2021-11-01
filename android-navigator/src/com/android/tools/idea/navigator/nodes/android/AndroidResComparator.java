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
package com.android.tools.idea.navigator.nodes.android;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * A comparator for {@link AndroidResFileNode} and {@link AndroidResGroupNode} objects, used to sort nodes of these two types which could
 * be present as siblings in the Android Project Pane.
 */
class AndroidResComparator implements Comparator<Object> {
  static final AndroidResComparator INSTANCE = new AndroidResComparator();

  @Override
  public int compare(Object o1, Object o2) {
    if (!(o1 instanceof NodeDescriptor) || !(o2 instanceof NodeDescriptor)) {
      return 0;
    }

    // we only support comparing res file nodes and res group nodes
    if (!(o1 instanceof AndroidResFileNode) && !(o1 instanceof AndroidResGroupNode)) {
      return 0;
    }
    if (!(o2 instanceof AndroidResFileNode) && !(o2 instanceof AndroidResGroupNode)) {
      return 0;
    }

    // if one of them is a group node, then we just have to compare them alphabetically
    if (o1 instanceof AndroidResGroupNode || o2 instanceof AndroidResGroupNode) {
      String n1 = getName(o1);
      String n2 = getName(o2);
      return StringUtil.compare(n1, n2, false);
    }

    AndroidResFileNode r1 = (AndroidResFileNode)o1;
    AndroidResFileNode r2 = (AndroidResFileNode)o2;

    // first check file names
    PsiFile file1 = r1.getValue();
    PsiFile file2 = r2.getValue();
    if (file1 != null && file2 != null) {
      int c = StringUtil.compare(file1.getName(), file2.getName(), false);
      if (c != 0) {
        return c;
      }
    }

    // check folder configurations
    FolderConfiguration config1 = r1.getFolderConfiguration();
    FolderConfiguration config2 = r2.getFolderConfiguration();
    if (config1 != null && config2 != null) {
      int c = config1.compareTo(config2);
      if (c != 0) {
        return c;
      }
    }

    // then check qualifiers
    return StringUtil.compare(r1.getQualifier(), r2.getQualifier(), false);
  }

  @Nullable
  private static String getName(@Nullable Object o1) {
    if (o1 instanceof AndroidResGroupNode) {
      return ((AndroidResGroupNode)o1).getResName();
    }
    else if (o1 instanceof AndroidResFileNode) {
      return ((AndroidResFileNode)o1).getResName();
    }
    else {
      return null;
    }
  }
}
