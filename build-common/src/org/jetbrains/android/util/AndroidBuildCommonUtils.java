/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.util;

import com.android.SdkConstants;
import com.android.io.FileWrapper;
import com.android.repository.Revision;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.PkgProps;
import com.intellij.util.ArrayUtil;
import java.io.File;
import java.util.Map;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidBuildCommonUtils {
  @NonNls public static final String PROGUARD_CFG_FILE_NAME = "proguard-project.txt";

  @NonNls public static final String CLASSES_JAR_FILE_NAME = "classes.jar";

  @NonNls public static final String PNG_EXTENSION = "png";

  @NonNls public static final String RELEASE_BUILD_OPTION = "RELEASE_BUILD_KEY";
  @NonNls public static final String PROGUARD_CFG_PATHS_OPTION = "ANDROID_PROGUARD_CFG_PATHS";

  private static final String[] TEST_CONFIGURATION_TYPE_IDS =
    {"AndroidJUnit", "JUnit", "TestNG", "ScalaTestRunConfiguration", "SpecsRunConfiguration", "Specs2RunConfiguration"};
  @NonNls public static final String ANNOTATIONS_JAR_RELATIVE_PATH = "/tools/support/annotations.jar";

  /** Android Test Run Configuration Type Id, defined here so as to be accessible to both JPS and Android plugin. */
  @NonNls public static final String ANDROID_TEST_RUN_CONFIGURATION_TYPE = "AndroidTestRunConfigurationType";

  private AndroidBuildCommonUtils() {
  }

  public static boolean isTestConfiguration(@NotNull String typeId) {
    return ArrayUtil.find(TEST_CONFIGURATION_TYPE_IDS, typeId) >= 0;
  }

  public static String platformToolPath(@NotNull String toolFileName) {
    return SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + toolFileName;
  }

  /**
   * Gets the {@link Revision} for the given package in the given SDK from the {@code source.properties} file.
   *
   * @return The {@link Revision}, or {@code null} if the {@code source.properties} file doesn't exist, doesn't contain a revision, or
   * the revision is unparsable.
   */
  @Nullable
  public static Revision parsePackageRevision(@NotNull String sdkDirOsPath, @NotNull String packageDirName) {
    File propFile =
      new File(sdkDirOsPath + File.separatorChar + packageDirName + File.separatorChar + SdkConstants.FN_SOURCE_PROP);
    if (propFile.exists() && propFile.isFile()) {
      Map<String, String> map =
        ProjectProperties.parsePropertyFile(new FileWrapper(propFile), new MessageBuildingSdkLog());
      if (map == null) {
        return null;
      }
      String revision = map.get(PkgProps.PKG_REVISION);

      if (revision != null) {
        return Revision.parseRevision(revision);
      }
    }
    return null;
  }
}
