package org.jetbrains.android.converter;

import com.intellij.conversion.ProjectConversionTestUtil;
import com.intellij.conversion.impl.ProjectConversionUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidConversionTest extends PlatformTestCase {
  public void testConvert() throws IOException {
    final String testDataPath = AndroidTestCase.getTestDataPath() + "/conversion/proguardOptions";
    final File testData = new File(testDataPath, "before");
    final File tempDir = FileUtil.createTempDirectory("project", null);
    FileUtil.copyDir(testData, tempDir);
    ProjectConversionTestUtil.convert(tempDir.getAbsolutePath());
    final File expectedDataDir = new File(testDataPath, "after");
    PlatformTestUtil.assertDirectoriesEqual(
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(expectedDataDir),
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir),
      new VirtualFileFilter() {
        @Override
        public boolean accept(VirtualFile file) {
          return !file.getName().startsWith(ProjectConversionUtil.PROJECT_FILES_BACKUP);
        }
      });
  }
}
