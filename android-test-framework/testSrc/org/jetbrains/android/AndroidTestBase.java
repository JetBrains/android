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
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.testing.DisposerExplorer;
import com.android.tools.idea.testing.Sdks;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.ui.UIUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.internal.progress.ThreadSafeMockingProgress;

@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class AndroidTestBase extends UsefulTestCase {
  protected JavaCodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    // Compute the workspace root before any IDE code starts messing with user.dir:
    TestUtils.getWorkspaceRoot();
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), FileUtil.toCanonicalPath(getAndroidPluginHome()));
  }

  @Override
  protected void tearDown() throws Exception {
    ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
    Callable<Void> callable = () -> {
      ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
      return null;
    };
    callable.call();
    AppScheduledExecutorService service = (AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService();
    List<Future<Void>> futures = service.invokeAll(Collections.nCopies(service.getBackendPoolExecutorSize(), callable));
    for (Future<Void> future : futures) {
      future.get();
    }
    myFixture = null;
    super.tearDown();
    checkUndisposedAndroidRelatedObjects();
  }

  /**
   * Checks that there are no undisposed Android-related objects.
   */
  private static void checkUndisposedAndroidRelatedObjects() {
    DisposerExplorer.visitTree(disposable -> {
      if (disposable.getClass().getName().equals("com.android.tools.idea.adb.AdbService") ||
          disposable.getClass().getName().equals("com.android.tools.idea.adb.AdbOptionsService") ||
          (disposable instanceof ProjectImpl && (((ProjectImpl)disposable).isDefault() || ((ProjectImpl)disposable).isLight())) ||
          (disposable instanceof Module && ((Module)disposable).getName().equals(LightProjectDescriptor.TEST_MODULE_NAME))) {
        // Ignore application services and light projects and modules that are not disposed by tearDown.
        return DisposerExplorer.VisitResult.SKIP_CHILDREN;
      }
      if (disposable.getClass().getName().startsWith("com.android.")) {
        Disposable root = disposable;
        Disposable parent;
        while ((parent = DisposerExplorer.getParent(root)) != null) {
          root = parent;
        }
        fail("Undisposed object: " + root);
      }
      return DisposerExplorer.VisitResult.CONTINUE;
    });
  }

  public void refreshProjectFiles() {
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
    Path adtPath = Paths.get(PathManager.getHomePath(), "../adt/idea", moduleFolder).normalize();
    if (Files.exists(adtPath)) {
      return adtPath.toString();
    }
    return PathManagerEx.findFileUnderCommunityHome("android/" + moduleFolder).getPath();
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected void ensureSdkManagerAvailable() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AndroidSdks androidSdks = AndroidSdks.getInstance();
      AndroidSdkData sdkData = androidSdks.tryToChooseAndroidSdk();
      if (sdkData == null) {
        sdkData = createTestSdkManager();
        if (sdkData != null) {
          androidSdks.setSdkData(sdkData);
        }
      }
      assertNotNull(sdkData);
    });
  }

  @Nullable
  protected AndroidSdkData createTestSdkManager() {
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), TestUtils.getSdk().toString());
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
    StringBuilder sb = new StringBuilder();
    for (PsiElement target : elements) {
      appendElementDescription(sb, target);
    }
    return sb.toString();
  }

  /**
   * Appends a description of the given element, suitable as unit test golden file output
   */
  public static void appendElementDescription(@NotNull StringBuilder sb, @NotNull PsiElement element) {
    if (element instanceof LazyValueResourceElementWrapper) {
      LazyValueResourceElementWrapper wrapper = (LazyValueResourceElementWrapper)element;
      XmlAttributeValue value = wrapper.computeElement();
      if (value != null) {
        element = value;
      }
    }
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
      if (ResourceHelper.getFolderType(file) != null) {
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
