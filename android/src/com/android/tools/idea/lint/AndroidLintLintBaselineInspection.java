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
package com.android.tools.idea.lint;

import com.android.tools.lint.client.api.IssueRegistry;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.util.AndroidBundle;

public class AndroidLintLintBaselineInspection extends AndroidLintInspectionBase {
  public AndroidLintLintBaselineInspection() {
    super(AndroidBundle.message("android.lint.inspections.lint.baseline"), IssueRegistry.BASELINE);
  }

  // Consider adding quickfixes here in the future. Ideas:
  // (1) Update baseline (removes fixed issues)
  // (2) Re-run analysis without baseline
  // (3) (Not quickfix, separate action) Create baseline (updates build.gradle)
}