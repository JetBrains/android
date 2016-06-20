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
 * Created by haowei on 6/8/16.
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
    LOG.info("CodeAnalysisActionWDlg Constructor invoked");
  }

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    //Create a mScene for this project.
    //Each project has its own mScene.
    Scene = PsiCFGScene.createFreshInstance(project);
    PsiCFGAnalysisUtil AnalysisUtil = Scene.analysisUtil;

    //Get list of java files available in this project
    int fileCount = scope.getFileCount();
    LOG.info("File count in scope " + fileCount);
    PsiFile[] allFilesInScope = findAllJavaFiles(project, scope);
    LOG.info("File count in visitor " + allFilesInScope.length);
    //outputFileNames(allFilesInScope);

    //Extract all java classes from java files.
    //Consider these classes are application classes.
    //As they are written by developer
    initiateProjectClassesFromPsiFile(allFilesInScope);
    PsiCFGClass[] allClasses = Scene.getAllApplicationClasses();

    //Perform the analysis
    AnalysisUtil.performStage0();
    AnalysisUtil.performStage1();
    AnalysisUtil.performStage2();
    AnalysisUtil.performStage3();
    AnalysisUtil.performStage4();
    AnalysisUtil.summarizeStage();
    AnalysisUtil.performStage5();
    AnalysisUtil.performStage6();
  }

  private void outputFileNames(PsiFile[] filesArray) {
    for (PsiFile f : filesArray) {
      System.out.println(f.getName());
    }
  }

  protected PsiFile[] findAllJavaFiles(@NotNull Project project, @NotNull AnalysisScope scope) {
    final ArrayList<PsiFile> retList = Lists.newArrayList();
    scope.accept(new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile file) {
        if (file instanceof PsiJavaFile) {
          retList.add(file);
        }
      }
    });
    return retList.toArray(PsiFile.EMPTY_ARRAY);
  }

  private void initiateProjectClassesFromPsiFile(@NotNull PsiFile[] files) {

    for (PsiFile pFile : files) {
      if (!(pFile instanceof PsiJavaFile)) {
        continue;
      }
      PsiClass[] curClassesInFile = extractProjectClasses(pFile);
      for (PsiClass curClass : curClassesInFile) {
        Scene.createPsiCFGClass(curClass, pFile, true);
      }
    }
  }

  private PsiClass[] extractProjectClasses(PsiFile pFile) {
    ArrayList<PsiClass> retList = Lists.newArrayList();

    //Look for class declarations in these files
    if (!(pFile instanceof PsiJavaFile)) {
      return retList.toArray(PsiClass.EMPTY_ARRAY);
    }

    PsiJavaFile curJavaFile = (PsiJavaFile)pFile;
    PsiClass[] firstLevelClasses = curJavaFile.getClasses();

    if (firstLevelClasses.length == 0) {
      return retList.toArray(PsiClass.EMPTY_ARRAY);
    }

    for (PsiClass clazz : firstLevelClasses) {
      retrieveClassAndInnerClass(retList, clazz);
    }
    return retList.toArray(PsiClass.EMPTY_ARRAY);
  }

  private void retrieveClassAndInnerClass(@NotNull ArrayList<PsiClass> retList, @NotNull PsiClass psiClass) {
    retList.add(psiClass);
    PsiClass[] innerClasses = psiClass.getInnerClasses();
    if (innerClasses == null || innerClasses.length == 0) {
      return;
    }
    for (PsiClass innerClazz : innerClasses) {
      retrieveClassAndInnerClass(retList, innerClazz);
    }
  }

}
