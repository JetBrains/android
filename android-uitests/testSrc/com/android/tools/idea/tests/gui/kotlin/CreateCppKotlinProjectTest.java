/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import static com.android.tools.idea.tests.gui.kotlin.ProjectWithKotlinTestUtil.createKotlinProj;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.StudioRobot;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.utils.Pair;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateCppKotlinProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(6, TimeUnit.MINUTES);

  @Before
  public void enableXwinClipboardWorkaround() {
    CopyPasteManager cpm = CopyPasteManager.getInstance();
    StudioRobot robot = (StudioRobot) guiTest.robot();
    robot.enableXwinClipboardWorkaround(cpm);
  }

  /**
   * <p>
   *   Verifies the IDE can create new projects with Kotlin and C++.
   * </p>
   *
   * <p>
   *   This is run to qualify releases. Please involve the test team in substantial changes.
   * </p>
   *
   * <p>
   *   This test is a part of the test case with identifier
   *   4d4c36b0-23a7-4f16-9293-061e2fb1310f. This test was was too large, so it has
   *   been split into smaller automated tests. Please search for other usages of
   *   4d4c36b0-23a7-4f16-9293-061e2fb1310f to find other test cases that are a part of
   *   this test case.
   * </p>
   *
   * <p>
   *   The other known test cases of this test case are:
   *   <ul>
   *     <li>{@link BuildCppKotlinTest#buildCppKotlinProj}</li>
   *   </ul>
   * </p>
   *
   * <p>
   *   TT ID: 4d4c36b0-23a7-4f16-9293-061e2fb1310f
   * </p>
   *
   * <pre>
   *   Test Steps:
   *   1. Create a basic Kotlin project following the default steps.
   *   2. Select the "include Kotlin" and "C++" support checkbox [verify 1 & 2].
   *   3. Build and run project on an emulator, and verify step 3.
   *   Verify:
   *   1. Check if build is successful.
   *   2. C++ code is created, MainActivity has .kt extension.
   * </pre>
   *
   * <p>
   *   This particular automated test just creates the project and verifies that the
   *   files of the project match the files of the pre-created project "CppKotlin".
   * </p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createCppKotlinProject() throws Exception {
    File precreatedProj = guiTest.copyProjectBeforeOpening("CppKotlin");

    createKotlinProj(true, guiTest);
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    Pair<File, File> unequalFiles = ProjectComparer.buildDefaultComparer().findUnequalFiles(ideFrameFixture.getProjectPath(), precreatedProj);
    if (unequalFiles != null) {
      File f1 = unequalFiles.getFirst();
      File f2 = unequalFiles.getSecond();

      String filename1 = f1 == null ? "null_file1" : f1.getAbsolutePath();
      String filename2 = f2 == null ? "null_file2" : f2.getAbsolutePath();
      Assert.fail(filename1 + " and " + filename2 + " are two unequal files");
    }
  }

  @After
  public void disableXwinClipboardWorkaround() {
    StudioRobot robot = (StudioRobot) guiTest.robot();
    robot.disableXwinClipboardWorkaround();
  }

  private static class ProjectComparer {
    /**
     * Some files need special treatment, like local.properties or gradle-wrapper.properties.
     * These files may contain timestamps. We also need to ignore .iml files.
     */
    @NotNull
    private final Collection<SpecialFileComparer> specialFileComparers;

    /**
     * Ignore files and directories like .idea, .gradle, .gitignore
     */
    @NotNull
    private final Collection<FileIgnorer> fileIgnorers;

    private ProjectComparer(@NotNull Collection<SpecialFileComparer> fileComparers, @NotNull Collection<FileIgnorer> fileIgnorers) {
      this.specialFileComparers = new ArrayList<>(fileComparers);
      this.fileIgnorers = new ArrayList<>(fileIgnorers);
    }

    public static ProjectComparer buildDefaultComparer() {
      FileIgnorer ignoredFiles = new FileIgnorer() {
        Set<String> filenamesToIgnore = new HashSet<>(
          Arrays.asList(
            ".idea",
            ".gradle",
            ".gitignore",
            ".externalNativeBuild",
            /*
             * Cmake Compiler caches are ignored. Builds with and without the cache are checked
             * in CmakeAndroidGradleBuildExtensionsTest. See discussion in b/119871858 for more detail.
             */
            ".cxx", // CMake compiler caches
            "gradle-wrapper.jar",
            "gradlew.bat",
            "gradle",
            "gradlew",
            "local.properties",
            "build.gradle", // whitespace issues, different configurations due to setup. Checked by syncing
            "build", // build directory
            "libs" // empty libs directory. Ignore
          )
        );

        @Override
        public boolean shouldIgnoreFile(@NotNull File file) {
          return filenamesToIgnore.contains(file.getName());
        }
      };

      FileIgnorer imlIgnorer = new FileIgnorer() {
        @Override
        public boolean shouldIgnoreFile(@NotNull File file) {
          return file.getName().endsWith(".iml");
        }
      };

      FileIgnorer mipmapIgnorer = new FileIgnorer() {
        @Override
        public boolean shouldIgnoreFile(@NotNull File file) {
          if (file.isFile()) {
            return true;
          }

          return file.getName().startsWith("mipmap");
        }
      };

      // TODO differences in whitespace and position of attributes can cause a simple file comparison to be difficult
      // Need to use an XML parser to compare?
      FileIgnorer xmlIgnorer = new FileIgnorer() {
        @Override
        public boolean shouldIgnoreFile(@NotNull File file) {
          return file.getName().endsWith(".xml");
        }
      };

      SpecialFileComparer propComparer = new SpecialFileComparer() {
        @Override
        public boolean isFileSpecial(@NotNull File file) {
          return file.getName().endsWith(".properties");
        }

        @Override
        public boolean areFilesEqual(@NotNull File f1, @NotNull File f2) throws IOException {
          return arePropFilesEqual(f1, f2);
        }
      };

      List<FileIgnorer> fileIgnorers = Arrays.asList(ignoredFiles, imlIgnorer, mipmapIgnorer, xmlIgnorer);
      List<SpecialFileComparer> fileComparers = Arrays.asList(propComparer);

      return new ProjectComparer(fileComparers, fileIgnorers);
    }

    private static boolean arePropFilesEqual(@NotNull File propFile1, @NotNull File propFile2) throws IOException {
      Properties properties1 = new Properties();
      Properties properties2 = new Properties();

      try (
        BufferedInputStream input1 = new BufferedInputStream(new FileInputStream(propFile1));
        BufferedInputStream input2 = new BufferedInputStream(new FileInputStream(propFile2))
      ) {
        properties1.load(input1);
        properties2.load(input2);
      }

      for (String prop1 : properties1.stringPropertyNames()) {
        String propVal1 = properties1.getProperty(prop1);
        String propVal2 = properties2.getProperty(prop1);

        propVal1 = propVal1 == null ? "" : propVal1;
        if (!propVal1.equals(propVal2)) {
          return false;
        }

        properties1.remove(prop1);
        properties2.remove(prop1);
      }

      // Finished scanning through properties1. If there are any
      // remaining properties in properties2, the two property
      // files are not equivalent.
      return properties2.isEmpty();
    }

    @Nullable
    public Pair<File, File> findUnequalFiles(@Nullable File file1, @Nullable File file2) throws IOException {
      if (file1 == file2) {
        return null;
      } else if (file1 == null || file2 == null) {
        // Only one of file1 and file2 can be null in this case
        return Pair.of(file1, file2);
      }
      // If we don't follow either of the if-else branch above, then
      // file1 and file2 are both non-null, and they are unequal. We
      // have to do the hard work of actually comparing the directories
      // file-by-file.

      if (file1.isDirectory() && file2.isDirectory()) {
        return findUnequalFilesInDirs(file1, file2);
      } else if (file1.isFile() && file2.isFile()) {
        if (areFilesEqual(file1, file2)) {
          return null;
        } else {
          return Pair.of(file1, file2);
        }
      } else {
        return Pair.of(file1, file2);
      }
    }

    @Nullable
    private Pair<File, File> findUnequalFilesInDirs(@NotNull File dir1, @NotNull File dir2) throws IOException {
      if (!dir1.isDirectory() || !dir2.isDirectory()) {
        return Pair.of(dir1, dir2);
      }

      // TODO: turn dir1Children into a collection rather than a simple array?
      // Both dir1 and dir2 are directories
      File[] dir1Children = dir1.listFiles();
      dir1Children = dir1Children == null ? new File[0] : dir1Children;

      HashMap<String, File> dir2Children = mapNameToFiles(dir2.listFiles());

      for (File child1 : dir1Children) {
        // Check if child1 is an ignored file e.g. .idea, .gradle
        boolean ignoreChild1 = false;
        for (FileIgnorer ignorer : fileIgnorers) {
          if (ignorer.shouldIgnoreFile(child1)) {
            ignoreChild1 = true;
          }
        }

        if (!ignoreChild1) {
          File child2 = dir2Children.remove(child1.getName());
          Pair<File, File> possiblyUnequal = findUnequalFiles(child1, child2);
          if (possiblyUnequal != null) {
            return possiblyUnequal;
          }
        }
      }

      // Check for remaining ignored files, e.g. .idea, .gradle
      List<String> remainingFilesToIgnore = new LinkedList<>();
      for (String remainingFilename : dir2Children.keySet()) {
        for (FileIgnorer ignorer : fileIgnorers) {
          File remainingFile = dir2Children.get(remainingFilename);
          if (ignorer.shouldIgnoreFile(remainingFile)) {
            remainingFilesToIgnore.add(remainingFilename);
            break;
          }
        }
      }

      for (String removeTarget : remainingFilesToIgnore) {
        dir2Children.remove(removeTarget);
      }

      if (!dir2Children.isEmpty()) {
        return Pair.of(dir1, dir2);
      }
      return null;
    }

    private boolean areFilesEqual(@NotNull File file1, @NotNull File file2) throws IOException {
      if (!file1.isFile() || !file2.isFile()) {
        return false;
      }

      // address special cases e.g. gradle-wrapper.properties, local.properties
      for (SpecialFileComparer sfc : specialFileComparers) {
        if (sfc.isFileSpecial(file1) || sfc.isFileSpecial(file2)) {
          return sfc.areFilesEqual(file1, file2);
        }
      }

      // At this point, the files are not special
      // Proceed by reading each file and comparing their contents
      try (
        BufferedInputStream input1 = new BufferedInputStream(new FileInputStream(file1));
        BufferedInputStream input2 = new BufferedInputStream(new FileInputStream(file2));
        ByteArrayOutputStream completeFile1 = new ByteArrayOutputStream();
        ByteArrayOutputStream completeFile2 = new ByteArrayOutputStream()
      ) {
        byte[] buffer = new byte[512];

        int bytesRead = input1.read(buffer);
        while (bytesRead >= 0) {
          completeFile1.write(buffer, 0, bytesRead);
          bytesRead = input1.read(buffer);
        }

        bytesRead = input2.read(buffer);
        while (bytesRead >= 0) {
          completeFile2.write(buffer, 0, bytesRead);
          bytesRead = input2.read(buffer);
        }

        return areByteArraysEqual(completeFile1.toByteArray(), completeFile2.toByteArray());
      }
    }

    private static boolean areByteArraysEqual(@NotNull byte[] array1, @NotNull byte[] array2) {
      if (array1.length != array2.length) {
        return false;
      }

      int size = array1.length;
      for (int index = 0; index < size; index++) {
        if (array1[index] != array2[index]) {
          return false;
        }
      }

      return true;
    }

    /**
     * For each file in {@code files}, map the file's filename to itself.
     */
    @NotNull
    private static HashMap<String, File> mapNameToFiles(@Nullable File[] files) {
      if (files == null) {
        return new HashMap<>();
      }

      HashMap<String, File> namesToFiles = new HashMap<>(files.length);
      for (File f : files) {
        namesToFiles.put(f.getName(), f);
      }
      return namesToFiles;
    }
  }

  private interface SpecialFileComparer {
    boolean isFileSpecial(@NotNull File file);
    boolean areFilesEqual(@NotNull File f1, @NotNull File f2) throws IOException;
  }

  private interface FileIgnorer {
    boolean shouldIgnoreFile(@NotNull File file);
  }
}
