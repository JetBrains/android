// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.util;

import com.android.testutils.TestUtils;
import com.intellij.openapi.application.PathManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * <b>There is no single root path (e.g. $WORKSPACE) in Idea</b>. This class provides convenient way to get sub-paths like (tools/adt/idea)
 * or (tools/external), or anything else, in a uniform way across different IDEs: AndroidStudio, Idea Ultimate, Idea Community.
 * <p><br>tl; dr;<p><br>
 * This class hides all the differences in directory layout in AOSP/IU/IC projects. It is different from
 * {@link TestUtils#getWorkspaceFile(java.lang.String)} in the following way: consider two paths:
 * <ul>
 * <li> "tools/adt/idea/android/testData" and
 * <li> "tools/external/gradle"
 * </ul> In AOSP world these two path can be obtained with
 * <ul>
 *   <li> {@code TestUtils.getWorkspaceFile("tools/adt/idea/android/testData")}
 *   <p> => "$WORKSPACE/tools/adt/idea/android/testData"
 *   <li> {@code TestUtils.getWorkspaceFile("tools/external/gradle")}
 *   <p> => "$WORKSPACE/tools/external/gradle"
 * </ul>
 * <p>
 * In Idea (ultimate/community) this will resolution not work as there is no notion of workspace:
 * <ul>
 *   <li> {@code TestUtils.getWorkspaceFile("tools/adt/idea/android/testData")}
 *   <p> => "$IU/community/android/android/testData"
 *   <p> => "$IC/android/android/testData"
 *   <li> {@code TestUtils.getWorkspaceFile("tools/external/gradle")}
 *   <p> => "$IU/community/build/.../tools/external"
 *   <p> => "$IC/build/.../tools/external"
 * </ul>
 * <p>
 * Note how "tools/external" resolves to "build/.../tools/external" and "tools/adt/idea" resolves to "android" paths.
 * <p>
 * Note: it is possible to move this class' methods to {@link TestUtils}. {@link TestUtils} would need to parse prefixes and generate
 * different absolute paths for AOSP/IU/IC. However "tools/base" classes do not participate in IC/IU builds, so editing these files when
 * working on Idea sources is not convenient.
 */
public class AndroidTestPaths {
  /**
   * @return absolute path to "tools/adt/idea"
   */
  public static Path adtSources() {
    List<String> alternatives = Arrays.asList(
      "../../tools/adt/idea", // AOSP
      "community/android", // IU
      "android" // IC
    );

    return selectExisting(alternatives, "Could not find path for ADT sources (tools/adt/idea)");
  }

  /**
   * @return absolute path to "tools/external"
   */
  public static Path toolsExternal() {
    List<String> alternatives = Arrays.asList(
      "../../tools/external", // AOSP
      "community/build/dependencies/build/android-sdk/tools/external", // IU
      "build/dependencies/build/android-sdk/tools/external" // IC
    );

    return selectExisting(alternatives, "Could not find path for tools/external");
  }

  /**
   * Resolve alternatives (relative to {@code PathManager.getHomePath()}) and return absolute path of the first one which resolves to
   * existing file or directory
   *
   * @param alternatives   alternative relative paths to check (relative to ${code PathManager.getHomePath()})
   * @param failureMessage exception message
   * @return first alternative which resolves to existing path
   * @throws AssertionError with {@code message:=failureMessage} when neither of the alternatives resolves to existing directory
   */
  @NotNull
  public static Path selectExisting(List<String> alternatives, String failureMessage) {
    Path home = Paths.get(PathManager.getHomePath());
    for (String alternative : alternatives) {
      Path altPath = home.resolve(alternative);
      if (Files.exists(altPath)) {
        return altPath;
      }
    }
    throw new AssertionError(failureMessage);
  }
}
