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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

/**
 * ColumnInfo that shows the API level of a package, if known.
 */
class ApiLevelColumnInfo extends ColumnInfo<UpdaterTreeNode, String> {
  ApiLevelColumnInfo() {
    super("API Level");
  }

  @Nullable
  @Override
  public String valueOf(UpdaterTreeNode node) {
    AndroidVersion version;
    if (node instanceof DetailsTreeNode) {
      DetailsTypes.ApiDetailsType details = (DetailsTypes.ApiDetailsType)((DetailsTreeNode)node).getPackage().getTypeDetails();
      version = details.getAndroidVersion();
    }
    else if (node instanceof SummaryTreeNode) {
      version = ((SummaryTreeNode)node).getVersion();
    }
    else {
      return "";
    }

    if (version != null) {
      return version.getApiStringWithExtension();
    }
    else {
      return "Unknown";
    }
  }
}
