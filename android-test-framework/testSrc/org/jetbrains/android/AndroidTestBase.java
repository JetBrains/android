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

import com.android.testutils.TestUtils;
import com.android.tools.idea.mockito.MockitoThreadLocalsCleaner;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.testing.DisposerExplorer;
import com.android.tools.idea.testing.Sdks;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
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
  protected MockitoThreadLocalsCleaner mockitoCleaner = new MockitoThreadLocalsCleaner();

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
    // super.tearDown will dispose testRootDisposable, which may invoke methods on mocked objects => project may leak
    // super.tearDown will also clean all the local fields. Make a copy of mockitoCleaner to invoke cleanupAndTearDown() after super.
    MockitoThreadLocalsCleaner cleaner = mockitoCleaner;
    try {
      super.tearDown();
    }
    finally {
      cleaner.cleanupAndTearDown();
    }
    checkUndisposedAndroidRelatedObjects();
  }

  // Keep track of each leaked disposable so that we can fail just the *first* test that leaks it.
  private static final Set<Disposable> allLeakedDisposables = ContainerUtil.createWeakSet();

  /**
   * Checks that there are no undisposed Android-related objects.
   */
  public static void checkUndisposedAndroidRelatedObjects() {
    Ref<Disposable> firstLeak = new Ref<>();
    DisposerExplorer.visitTree(disposable -> {
      if (allLeakedDisposables.contains(disposable) ||
          disposable.getClass().getName().startsWith("com.android.tools.analytics.HighlightingStats") ||disposable.getClass().getName().equals("com.android.tools.idea.adb.AdbService") ||
          (disposable instanceof ProjectEx && (((ProjectEx)disposable).isDefault() || ((ProjectEx)disposable).isLight())) ||
          disposable.toString().startsWith("services of com.intellij.openapi.project.impl.ProjectImpl") ||
          disposable.toString().startsWith("services of com.intellij.openapi.project.impl.ProjectExImpl") ||
          disposable.toString().startsWith("services of " + ApplicationImpl.class.getName()) ||
          disposable instanceof Application ||
          (disposable instanceof Module && ((Module)disposable).getName().equals(LightProjectDescriptor.TEST_MODULE_NAME)) ||
          disposable instanceof PsiReferenceContributor) {
        // Ignore application services and light projects and modules that are not disposed by tearDown.
        return DisposerExplorer.VisitResult.SKIP_CHILDREN;
      }
      if (disposable.getClass().getName().startsWith("com.android.") ||
          disposable.getClass().getName().startsWith("org.jetbrains.android.")) {
        firstLeak.setIfNull(disposable);
        allLeakedDisposables.add(disposable);
      }
      return DisposerExplorer.VisitResult.CONTINUE;
    });
    if (!firstLeak.isNull()) {
        Disposable root = firstLeak.get();
        StringBuilder disposerChain = new StringBuilder(root.toString());
        Disposable parent;
        while ((parent = DisposerExplorer.getParent(root)) != null) {
          root = parent;
          disposerChain.append(" <- ").append(root);
        }
      String baseMsg = "Undisposed object of type " + root.getClass().getName() + ": " + disposerChain.append(" (root)") + "'";
      if (DisposerExplorer.getParent(firstLeak.get()) == null) {
        throw new RuntimeException(
          baseMsg + ", registered as a root disposable (see cause for creation trace)",
          DisposerExplorer.getTrace(disposable));
      } else {
        throw new RuntimeException(baseMsg + ", with parent '" + parent + "' of type '" + parent.getClass().getName() + "'");
      }
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

  public static String getModulePath(String moduleFolder) {
    // Now that the Android plugin is kept in a separate place, we need to look in
    // a relative position instead
    File adtIdea = TestUtils.getWorkspaceFile("tools/adt/idea");
    Path adtPath = Paths.get(adtIdea.getAbsolutePath(), moduleFolder).normalize();
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
    List<PsiElement> sortedElements = Arrays.stream(elements).map((element) -> {
      if (element instanceof LazyValueResourceElementWrapper) {
        LazyValueResourceElementWrapper wrapper = (LazyValueResourceElementWrapper)element;
        XmlAttributeValue value = wrapper.computeElement();
        if (value != null) {
          return value;
        }
      }
      return element;
    }).sorted(Comparator.comparing(PsiElement::getText)).collect(Collectors.toList());
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
}
