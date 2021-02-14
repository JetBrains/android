// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.util;

public class ApkContentFilter {
  public ApkContentFilter() {
  }

  // TODO: make it consistent with com.android.build.gradle.internal.dsl.PackagingOptions
  public boolean checkEntry(String name) {
    String[] segments = name.split("/");
    if (segments.length == 0) {
      return false;
    } else {
      for(int i = 0; i < segments.length - 1; ++i) {
        if (!checkFolderForPackaging(segments[i])) {
          return false;
        }
      }

      String fileName = segments[segments.length - 1];
      return checkFileForPackaging(fileName);
    }
  }

  public static boolean checkFolderForPackaging(String folderName) {
    return !folderName.equals("CVS") && !folderName.equals(".svn") && !folderName.equals("SCCS") && !folderName.equals("META-INF") && !folderName.startsWith("_");
  }

  public static boolean checkFileForPackaging(String fileName) {
    String[] fileSegments = fileName.split("\\.");
    String fileExt = "";
    if (fileSegments.length > 1) {
      fileExt = fileSegments[fileSegments.length - 1];
    }

    return checkFileForPackaging(fileName, fileExt);
  }

  public static boolean checkFileForPackaging(String fileName, String extension) {
    if (fileName.charAt(0) == '.') {
      return false;
    } else {
      return !"aidl".equalsIgnoreCase(extension) && !"java".equalsIgnoreCase(extension) && !"class".equalsIgnoreCase(extension) && !"scc".equalsIgnoreCase(extension) && !"swp".equalsIgnoreCase(extension) && !"package.html".equalsIgnoreCase(fileName) && !"overview.html".equalsIgnoreCase(fileName) && !".cvsignore".equalsIgnoreCase(fileName) && !".DS_Store".equals(fileName) && fileName.charAt(fileName.length() - 1) != '~';
    }
  }
}
