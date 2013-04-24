/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.compiler;

import com.android.SdkConstants;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.compiler.server.CompileServerPathProvider;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * This class is a temporary workaround for the current issue that JPS cannot see 3rd-party jars because we have 2 'android' modules. It
 * allows us to specify Gradle in the classpath of the Android/Gradle JPS builder. This workaround will no longer be necessary once we have
 * a single 'android' module.
 */
public class AndroidCompileServerPathProvider implements CompileServerPathProvider {
  private List<String> classpath;

  @NotNull
  private static List<String> getGradleClassPath() {
    String gradleLibDirPath = null;
    String gradleToolingApiJarPath = PathUtil.getJarPathForClass(ProjectConnection.class);
    if (!Strings.isNullOrEmpty(gradleToolingApiJarPath)) {
      gradleLibDirPath = PathUtil.getParentPath(gradleToolingApiJarPath);
    }
    if (Strings.isNullOrEmpty(gradleLibDirPath)) {
      return ImmutableList.of();
    }
    List<String> classpath = Lists.newArrayList();
    File gradleLibDir = new File(gradleLibDirPath);
    if (!gradleLibDir.isDirectory()) {
      return ImmutableList.of();
    }
    File[] files = gradleLibDir.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isFile() && f.getName().endsWith(SdkConstants.DOT_JAR)) {
          classpath.add(PathUtil.getCanonicalPath(f.getAbsolutePath()));
        }
      }
    }
    return classpath;
  }

  @NotNull
  @Override
  public List<String> getClassPath() {
    if (classpath == null) {
      classpath = getGradleClassPath();
    }
    return classpath;
  }
}
