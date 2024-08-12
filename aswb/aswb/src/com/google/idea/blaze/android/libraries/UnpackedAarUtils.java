/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.android.libraries;

import static com.android.SdkConstants.FN_LINT_JAR;

import com.android.SdkConstants;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import java.io.File;

/** Utility methods to convert aar to other type that needed by {@link UnpackedAars} */
public final class UnpackedAarUtils {
  /**
   * The name of imported_aar' ijar. It will always be same for any aars due to aar's format (more
   * details http://tools.android.com/tech-docs/new-build-system/aar-format).
   */
  public static final String CLASS_JAR_FILE_NAME = "classes_and_libs_merged.jar";

  /* Converts {@link BlazeArtifact} to the key which is used to create aar directory name */
  public static String getArtifactKey(BlazeArtifact artifact) {
    if (artifact instanceof OutputArtifactWithoutDigest) {
      return ((OutputArtifactWithoutDigest) artifact).getRelativePath();
    }
    if (artifact instanceof SourceArtifact) {
      return ((SourceArtifact) artifact).getFile().getPath();
    }
    throw new RuntimeException("Unhandled BlazeArtifact type: " + artifact.getClass());
  }

  /* Returns aar directory name according to {@link BlazeArtifact} */
  public static String getAarDirName(BlazeArtifact aar) {
    String key = getArtifactKey(aar);
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(key));
    return generateAarDirectoryName(/* name= */ name, /* hashCode= */ key.hashCode())
        + SdkConstants.DOT_AAR;
  }

  /**
   * Returns the source jar name according to {@link BlazeArtifact}. Differen with ijar that share
   * same name accoss different aars, source jars can have different names.
   */
  public static String getSrcJarName(BlazeArtifact srcJar) {
    String key = getArtifactKey(srcJar);
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(key));
    return name + SdkConstants.DOT_SRCJAR;
  }

  /**
   * Returns aar directory name in the format of <name>_<hashcode>. This provides flexibility to
   * caller to generate aar directory name to deal with different cases. But this method cannot
   * guarantee the uniqueness.
   *
   * @param name the file name of aar file without extension.
   * @param hashCode the file name of aar is not guaranteed to be unique, so a hash code is used to
   *     make sure the directory name is unique. Caller needs to make sure the hashcode provided is
   *     unique e.g. hash of path to aar
   */
  public static String generateAarDirectoryName(String name, int hashCode) {
    return name + "_" + Integer.toHexString(hashCode);
  }

  /**
   * Returns path to the jar file in aarDir. It's not guaranteed the jar exists, so caller need to
   * check the existence of aarDir and jar file.
   */
  public static File getJarFile(File aarDir) {
    File jarsDirectory = new File(aarDir, SdkConstants.FD_JARS);
    // At this point, we don't know the name of the original jar, but we must give the cache
    // file a name. Just use a name similar to what bazel currently uses, and that conveys
    // the origin of the jar (merged from classes.jar and libs/*.jar).
    return new File(jarsDirectory, CLASS_JAR_FILE_NAME);
  }

  /**
   * Returns path to the resource folder in aarDir. It's not guaranteed the jar exists, so caller
   * need to check the existence of aarDir and resource folder.
   */
  public static File getResDir(File aarDir) {
    return new File(aarDir, SdkConstants.FD_RES);
  }

  /**
   * Returns path to the lint.jar in aarDir. It's not guaranteed the jar exists, so callers need to
   * check that the existence of the jar.
   */
  public static File getLintRuleJar(File aarDir) {
    return new File(aarDir, FN_LINT_JAR);
  }

  private UnpackedAarUtils() {}
}
