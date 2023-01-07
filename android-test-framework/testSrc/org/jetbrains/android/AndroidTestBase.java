/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android;

import static org.jetbrains.android.UndisposedAndroidObjectsCheckerRule.checkUndisposedAndroidRelatedObjects;

import com.android.testutils.MockitoThreadLocalsCleaner;
import com.android.testutils.TestUtils;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.testing.Sdks;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.util.ui.UIUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NOTE: If you are writing a new test, consider using JUnit4 with
 * {@link com.android.tools.idea.testing.AndroidProjectRule} instead. This allows you to use
 * features introduced in JUnit4 (such as parameterization) while also providing a more
 * compositional approach - instead of your test class inheriting dozens and dozens of methods you
 * might not be familiar with, those methods will be constrained to the rule.
 */
@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class AndroidTestBase extends UsefulTestCase {

  protected JavaCodeInsightTestFixture myFixture;
  private final MockitoThreadLocalsCleaner mockitoCleaner = new MockitoThreadLocalsCleaner();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockitoCleaner.setup();

    // Compute the workspace root before any IDE code starts messing with user.dir:
    TestUtils.getWorkspaceRoot();
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), FileUtil.toCanonicalPath(getAndroidPluginHome()));
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture = null;
    try {
      super.tearDown();
    } finally {
      // Clean up Mockito refs *after* super.tearDown() because project disposal may trigger new mock interactions.
      mockitoCleaner.cleanupAndTearDown();
    }
    checkUndisposedAndroidRelatedObjects();
  }

  public static void refreshProjectFiles() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      // With IJ14 code base, we run tests with NO_FS_ROOTS_ACCESS_CHECK turned on. I'm not sure if that
      // is the cause of the issue, but not all files inside a project are seen while running unit tests.
      // This explicit refresh of the entire project fix such issues (e.g. AndroidProjectViewTest).
      // This refresh must be synchronous and recursive so it is completed before continuing the test and clean everything so indexes are
      // properly updated. Apparently this solves outdated indexes and stubs problems
      WriteAction.run(() -> LocalFileSystem.getInstance().refresh(false));

      // Run VFS listeners.
      UIUtil.dispatchAllInvocationEvents();
    });
  }

  public static String getTestDataPath() {
    return getAndroidPluginHome() + "/testData";
  }

  public static String getAndroidPluginHome() {
    return getModulePath("android");
  }

  public static String getModulePath(@NotNull String moduleFolder) {
    // Now that the Android plugin is kept in a separate place, we need to look in
    // a relative position instead
    Path adtIdea = TestUtils.resolveWorkspacePath("tools/adt/idea");
    Path adtPath = adtIdea.resolve(moduleFolder).normalize();
    if (Files.exists(adtPath)) {
      return adtPath.toString();
    }
    return PathManagerEx.findFileUnderCommunityHome("android/" + moduleFolder).getPath();
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  public void ensureSdkManagerAvailable() {
    ensureSdkManagerAvailable(getTestRootDisposable());
  }

  public static void ensureSdkManagerAvailable(@NotNull Disposable testRootDisposable) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AndroidSdks androidSdks = AndroidSdks.getInstance();
      AndroidSdkData sdkData = androidSdks.tryToChooseAndroidSdk();
      if (sdkData == null) {
        sdkData = AndroidTestBase.createTestSdkManager(testRootDisposable);
        if (sdkData != null) {
          androidSdks.setSdkData(sdkData);
        }
      }
      assertNotNull(sdkData);
    });
  }

  @Nullable
  public static AndroidSdkData createTestSdkManager(@NotNull Disposable testRootDisposable) {
    VfsRootAccess.allowRootAccess(testRootDisposable, TestUtils.getSdk().toString());
    Sdk androidSdk = Sdks.createLatestAndroidSdk();
    AndroidSdkAdditionalData data = AndroidSdks.getInstance().getAndroidSdkAdditionalData(androidSdk);
    if (data != null) {
      AndroidPlatform androidPlatform = data.getAndroidPlatform();
      if (androidPlatform != null) {
        // Put default platforms in the list before non-default ones so they'll be looked at first.
        return androidPlatform.getSdkData();
      }
      else {
        fail("No getAndroidPlatform() associated with the AndroidSdkAdditionalData: " + data);
      }
    }
    else {
      fail("Could not find data associated with the SDK: " + androidSdk.getName());
    }
    return null;
  }

  /**
   * Returns a description of the given elements, suitable as unit test golden file output
   */
  public static String describeElements(@Nullable PsiElement[] elements) {
    if (elements == null) {
      return "Empty";
    }
    List<PsiElement> sortedElements = Arrays.stream(elements)
      .sorted(Comparator.comparing(PsiElement::getText)).collect(Collectors.toList());
    StringBuilder sb = new StringBuilder();
    for (PsiElement target : sortedElements) {
      appendElementDescription(sb, target);
    }
    return sb.toString();
  }

  /**
   * Appends a description of the given element, suitable as unit test golden file output
   */
  public static void appendElementDescription(@NotNull StringBuilder sb, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    int offset = element.getTextOffset();
    TextRange segment = element.getTextRange();
    appendSourceDescription(sb, file, offset, segment);
  }

  /**
   * Appends a description of the given elements, suitable as unit test golden file output
   */
  public static void appendSourceDescription(@NotNull StringBuilder sb, @Nullable PsiFile file, int offset, @Nullable Segment segment) {
    if (file != null && segment != null) {
      if (IdeResourcesUtil.getFolderType(file) != null) {
        assertNotNull(file.getParent());
        sb.append(file.getParent().getName());
        sb.append("/");
      }
      sb.append(file.getName());
      sb.append(':');
      String text = file.getText();
      int lineNumber = 1;
      for (int i = 0; i < offset; i++) {
        if (text.charAt(i) == '\n') {
          lineNumber++;
        }
      }
      sb.append(lineNumber);
      sb.append(":");
      sb.append('\n');
      int startOffset = segment.getStartOffset();
      int endOffset = segment.getEndOffset();
      assertTrue(offset == -1 || offset >= startOffset);
      assertTrue(offset == -1 || offset <= endOffset);

      int lineStart = startOffset;
      while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
        lineStart--;
      }

      // Skip over leading whitespace
      while (lineStart < startOffset && Character.isWhitespace(text.charAt(lineStart))) {
        lineStart++;
      }

      int lineEnd = startOffset;
      while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') {
        lineEnd++;
      }
      String indent = "  ";
      sb.append(indent);
      sb.append(text, lineStart, lineEnd);
      sb.append('\n');
      sb.append(indent);
      for (int i = lineStart; i < lineEnd; i++) {
        if (i == offset) {
          sb.append('|');
        }
        else if (i >= startOffset && i <= endOffset) {
          sb.append('~');
        }
        else {
          sb.append(' ');
        }
      }
    }
    else {
      sb.append(offset);
      sb.append(":?");
    }
    sb.append('\n');
  }

  protected SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> doGlobalInspectionTest(
    @NotNull GlobalInspectionTool inspection, @NotNull String globalTestDir, @NotNull AnalysisScope scope) {
    return doGlobalInspectionTest(new GlobalInspectionToolWrapper(inspection), globalTestDir, scope);
  }

  /**
   * Given an inspection and a path to a directory that contains an "expected.xml" file, run the
   * inspection on the current test project and verify that its output matches that of the
   * expected file.
   */
  protected SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> doGlobalInspectionTest(
    @NotNull GlobalInspectionToolWrapper wrapper, @NotNull String globalTestDir, @NotNull AnalysisScope scope) {
    myFixture.enableInspections(wrapper.getTool());

    scope.invalidate();

    InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(getProject());
    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), Arrays.<InspectionToolWrapper<?, ?>>asList(wrapper));

    InspectionTestUtil.runTool(wrapper, scope, globalContext);
    InspectionTestUtil.compareToolResults(globalContext, wrapper, false, myFixture.getTestDataPath() + globalTestDir);

    return globalContext.getPresentation(wrapper).getProblemElements();
  }
}
