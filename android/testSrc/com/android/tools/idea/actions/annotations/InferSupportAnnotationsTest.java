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
package com.android.tools.idea.actions.annotations;

import com.google.common.collect.Lists;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.AndroidTestBase;

import java.util.Arrays;
import java.util.List;

public class InferSupportAnnotationsTest extends CodeInsightTestCase {
  private static final String INFER_PATH = "/infer/";

  @Override
  protected String getTestDataPath() {
    return AndroidTestBase.getTestDataPath();
  }

  public void testInferParameterFromUsage() throws Exception {
    doTest(false, "Class InferParameterFromUsage:\n" +
                  "  Method inferParameterFromMethodCall:\n" +
                  "    Parameter int id:\n" +
                  "      @DimenRes because it calls InferParameterFromUsage#getDimensionPixelSize");
  }

  public void testInferResourceFromArgument() throws Exception {
    doTest(false, "Class InferResourceFromArgument:\n" +
                  "  Method inferredParameterFromOutsideCall:\n" +
                  "    Parameter int inferredDimension:\n" +
                  "      @DimenRes because it's passed R.dimen.some_dimension in a call");
  }

  public void testInferMethodAnnotationFromReturnValue() throws Exception {
    doTest(false, "Class InferMethodAnnotationFromReturnValue:\n" +
                  "  Method test:\n" +
                  "      @DrawableRes because it returns R.mipmap.some_image\n" +
                  "      @IdRes because it returns id");
  }

  public void testInferFromInheritance() throws Exception {
    doTest(false, "Class Child:\n" +
                  "  Method foo:\n" +
                  "      @DrawableRes because it extends or is overridden by an annotated method\n" +
                  "  Method something:\n" +
                  "    Parameter int foo:\n" +
                  "      @DrawableRes because it extends a method with that parameter annotated or inferred");
  }

  public void testEnforcePermission() throws Exception {
    doTest(false, "Class EnforcePermission:\n" +
                  "  Method impliedPermission:\n" +
                  "      @RequiresPermission(EnforcePermission.MY_PERMISSION) because it calls enforceCallingOrSelfPermission\n" +
                  "  Method unconditionalPermission:\n" +
                  "      @RequiresPermission(EnforcePermission.MY_PERMISSION) because it calls EnforcePermission#impliedPermission");
  }

  public void testConditionalPermission() throws Exception {
    if (!InferSupportAnnotationsAction.ENABLED) {
      return;
    }

    // None of the permission requirements should transfer; all calls are conditional
    try {
      doTest(false, "Nothing found.");
      fail("Should infer nothing");
    }
    catch (RuntimeException e) {
      if (!Comparing.strEqual(e.getMessage(), "Nothing found to infer")) {
        fail();
      }
    }
  }

  public void testIndirectPermission() throws Exception {
    // Not yet implemented: Expected fail!
    doTest(false, null);
  }

  public void testReflection() throws Exception {
    // TODO: implement
    doTest(false, null);
  }

  public void testThreadFlow() throws Exception {
    // Not yet implemented: Expected fail!
    doTest(false, null);
  }

  public void testMultiplePasses() throws Exception {
    // Not yet implemented: Expected fail!
    doTest(false, "Class A:\n" +
                  "  Method fromA:\n" +
                  "    Parameter int id:\n" +
                  "      @DimenRes because it calls A#something\n" +
                  "\n" +
                  "Class D:\n" +
                  "  Method d:\n" +
                  "    Parameter int id:\n" +
                  "      @DrawableRes because it calls D#something",
           getVirtualFile(INFER_PATH + "A.java"),
           getVirtualFile(INFER_PATH + "B.java"),
           getVirtualFile(INFER_PATH + "C.java"),
           getVirtualFile(INFER_PATH + "D.java"));
  }
  private void doTest(boolean annotateLocalVariables, String summary, VirtualFile... files) throws Exception  {
    if (!InferSupportAnnotationsAction.ENABLED) {
      System.out.println("Ignoring " + this.getClass().getSimpleName() + ": Functionality disabled");
      return;
    }

    String annotationsJar = getTestDataPath() + "/infer/data.jar";
    VirtualFile aLib = LocalFileSystem.getInstance().findFileByPath(annotationsJar);
    if (aLib != null) {
      final VirtualFile file = JarFileSystem.getInstance().getJarRootForLocalFile(aLib);
      if (file != null) {
        ModuleRootModificationUtil.addModuleLibrary(myModule, file.getUrl());
      }
    }

    InferSupportAnnotations inference = new InferSupportAnnotations(annotateLocalVariables, getProject());
    AnalysisScope scope;
    if (files.length > 0) {
      configureByFiles(null, files);
      scope = new AnalysisScope(getProject(), Arrays.asList(files));

      for (int i = 0; i < InferSupportAnnotationsAction.MAX_PASSES; i++) {
        for (VirtualFile virtualFile : files) {
          PsiFile psiFile = getPsiManager().findFile(virtualFile);
          assertNotNull(psiFile);
          inference.collect(psiFile);
        }
      }
    } else {
      configureByFile(INFER_PATH + "before" + getTestName(false) + ".java");
      inference.collect(getFile());
      scope = new AnalysisScope(getFile());
    }

    if (summary != null) {
      List<UsageInfo> infos = Lists.newArrayList();
      inference.collect(infos, scope);
      String s = InferSupportAnnotations.generateReport(infos.toArray(UsageInfo.EMPTY_ARRAY));
      s = StringUtil.trimStart(s, "INFER SUPPORT ANNOTATIONS REPORT\n" +
                                  "================================\n" +
                                  "\n");
      assertEquals(summary, s.trim());
    }

    if (files.length == 0) {
      inference.apply(getProject());
      checkResultByFile(INFER_PATH + "after" + getTestName(false) + ".java");
    }
  }
}