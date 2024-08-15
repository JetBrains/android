/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.cppimpl;

import com.android.tools.ndk.AndroidSysroot;
import com.android.tools.ndk.configuration.NdkConfigurationValidatorKt;
import com.google.idea.blaze.cpp.CppSupportChecker;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches.Format;
import java.nio.file.Path;

/**
 * Uses the Android Studio API to check if a CPP configuration is supported, i.e. is for NDK
 * development rather than non-Android CPP.
 */
public class NdkCppSupportChecker implements CppSupportChecker {

  @Override
  public boolean supportsCppConfiguration(
      CidrCompilerSwitches compilerSwitches, Path workspaceRoot) {
    return NdkConfigurationValidatorKt.isValidNdkConfiguration(
        compilerSwitches.getList(Format.RAW),
        workspaceRoot,
        AndroidSysroot::isValidAndroidSysroot,
        AndroidSysroot::isValidAndroidSysrootUsrInclude,
        AndroidSysroot::isPotentialNonAndroidSysrootUsrInclude);
  }
}
