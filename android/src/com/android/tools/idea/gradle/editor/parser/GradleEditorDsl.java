/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.parser;

public class GradleEditorDsl {

  public static final String SUB_PROJECT_SECTION = "subprojects";
  public static final String ALL_PROJECTS_SECTION = "allprojects";
  public static final String BUILD_SCRIPT_SECTION = "buildscript";

  public static final String DEPENDENCIES_SECTION = "dependencies";
  public static final String CLASSPATH_CONFIGURATION = "classpath";
  public static final String COMPILE_SDK_VERSION = "compileSdkVersion";
  public static final String BUILD_TOOLS_VERSION = "buildToolsVersion";

  public static final String REPOSITORIES_SECTION = "repositories";
  public static final String MAVEN_CENTRAL = "mavenCentral";
  public static final String JCENTER = "jcenter";
  public static final String MAVEN_REPO = "maven";
  public static final String MAVEN_REPO_URL = "url";

  private GradleEditorDsl() {
  }
}
