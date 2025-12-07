/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;

/** Static utility methods used for blaze import. */
public class BlazeImportUtil {

  @VisibleForTesting
  static String inferJavaResourcePackage(String blazeRelativePath) {
    // Blaze ensures that all android targets either provide a custom package override, or have
    // blaze package of the form:
    //        //any/path/java/package/name/with/slashes, or
    //        //any/path/javatests/package/name/with/slashes
    // We use this fact to infer package name.

    // Using the separator `/` to ensure we do not accidentally catch things like "/java_src/"
    // or "/somenamejava/"
    String javaPackage = "/" + blazeRelativePath;
    String workingPackage;

    // get everything after `/java/` , or no-op if `/java/` is not present
    workingPackage = StringUtil.substringAfterLast(javaPackage, "/java/");
    javaPackage = workingPackage == null ? javaPackage : "/" + workingPackage;

    // get everything after `/javatests/` , or no-op if `/javatests/` is not present
    workingPackage = StringUtil.substringAfterLast(javaPackage, "/javatests/");
    javaPackage = workingPackage == null ? javaPackage : "/" + workingPackage;

    if (javaPackage.startsWith("/")) {
      javaPackage = javaPackage.substring(1);
    }

    return javaPackage.replace('/', '.');
  }
}
