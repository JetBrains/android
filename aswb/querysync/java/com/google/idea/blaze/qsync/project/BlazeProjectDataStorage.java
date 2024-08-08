/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

/** Path names used for query sync project data */
public class BlazeProjectDataStorage {
  private BlazeProjectDataStorage() {}

  public static final String BLAZE_DATA_SUBDIRECTORY = ".blaze";
  public static final String WORKSPACE_MODULE_NAME = ".workspace";

  public static final String LIBRARY_DIRECTORY = "libraries";
  public static final String DEPENDENCIES_SOURCES = "libraries_src";
  public static final String RENDER_JARS_DIRECTORY = "renderjars";
  public static final String AAR_DIRECTORY = "aars";
  public static final String GEN_SRC_DIRECTORY = "generated";
  public static final String GEN_HEADERS_DIRECTORY = "hdrs";
  public static final String APP_INSPECTOR_DIRECTORY = "app_inspectors";

  public static final String DEPENDENCIES_LIBRARY = ".dependencies";
}
