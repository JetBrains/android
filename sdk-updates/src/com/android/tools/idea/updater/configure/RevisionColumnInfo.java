/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.updater.configure;

import com.android.repository.Revision;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

/**
 * ColumnInfo that shows the revision number of a package.
 */
class RevisionColumnInfo extends ColumnInfo<UpdaterTreeNode, Revision> {
  RevisionColumnInfo() {
    super("Revision");
  }

  @Nullable
  @Override
  public Revision valueOf(UpdaterTreeNode node) {
    if (node instanceof SummaryTreeNode) {
      node = ((SummaryTreeNode)node).getPrimaryChild();
    }
    if (node instanceof DetailsTreeNode) {
      return ((DetailsTreeNode)node).getPackage().getVersion();
    }
    return null;
  }
}
