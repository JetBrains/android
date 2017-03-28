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
package com.android.tools.idea.experimental.actions;

import com.android.tools.idea.experimental.CodeAnalysisMain;
import com.android.tools.idea.experimental.codeanalysis.PsiCFGScene;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.utils.PsiCFGAnalysisUtil;
import com.google.common.collect.Lists;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Entry class for the interprocedural analysis
 */
public class CodeAnalysisActionWDlg extends BaseAnalysisAction {

  private static final String actionTitle = "Control Flow Analysis";
  private static final String description = "Intraprocedural control flow analysis";
  private static final Logger LOG = Logger.getLogger(CodeAnalysisActionWDlg.class.getName());
  private PsiCFGScene Scene = null;

  public CodeAnalysisActionWDlg() {
    super(actionTitle, description);
    //Temporarily change the log level
    LOG.setLevel(Level.ALL);
  }

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    CodeAnalysisMain analysisMain = CodeAnalysisMain.getInstance(project);
    analysisMain.analyze(scope);
  }
}
