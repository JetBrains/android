/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.model.PsIssue;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class IssuesByTypeAndTextComparator implements Comparator<PsIssue> {
  @NotNull public static final IssuesByTypeAndTextComparator INSTANCE = new IssuesByTypeAndTextComparator();

  @Override
  public int compare(PsIssue i1, PsIssue i2) {
    int compare = i1.getSeverity().getPriority() - i2.getSeverity().getPriority();
    if (compare != 0) {
      return compare;
    }
    compare = i1.getPath().compareTo(i2.getPath());
    if (compare != 0) {
      return compare;
    }
    compare = i1.getText().compareTo(i2.getText());
    return compare;
  }
}
