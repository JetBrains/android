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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.cpp.CppBlazeRules;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builder for a blaze C/C++(CC) toolchain target's IDE info. Defines CC toolchain specific
 * attributes such as built-in includes and compiler options. This class is similar to {@link
 * NbCcTarget} but should not be confused with it. This builder accumulates attributes to a {@link
 * TargetIdeInfo.Builder} which can be used to build {@link TargetMap}.
 *
 * <p>Targets built with {@link NbCcToolchain} always have a {@link CToolchainIdeInfo} attached,
 * even if it's empty.
 */
public class NbCcToolchain extends NbBaseTargetBuilder {
  private final NbTarget target;
  private final CToolchainIdeInfo.Builder cToolchainIdeInfoBuilder;

  public static NbCcToolchain cc_toolchain(String label) {
    return cc_toolchain(label, BlazeInfoData.DEFAULT);
  }

  public static NbCcToolchain cc_toolchain(String label, BlazeInfoData blazeInfoData) {
    return new NbCcToolchain(blazeInfoData, label);
  }

  NbCcToolchain(BlazeInfoData blazeInfoData, String label) {
    super(blazeInfoData);
    target = new NbTarget(blazeInfoData, label, CppBlazeRules.RuleTypes.CC_TOOLCHAIN.getKind());
    cToolchainIdeInfoBuilder = new CToolchainIdeInfo.Builder();
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return target.getIdeInfoBuilder().setCToolchainInfo(cToolchainIdeInfoBuilder);
  }

  public NbCcToolchain src(String... sourceLabels) {
    target.src(sourceLabels);
    return this;
  }

  public NbCcToolchain dep(String... targetLabels) {
    target.dep(targetLabels);
    return this;
  }

  public NbCcToolchain cc_target_name(String targetName) {
    cToolchainIdeInfoBuilder.setTargetName(targetName);
    return this;
  }

  public NbCcToolchain cpp_executable(String pathToExecutable) {
    cToolchainIdeInfoBuilder.setCppExecutable(new ExecutionRootPath(pathToExecutable));
    return this;
  }

  public NbCcToolchain built_in_include_dirs(String... directories) {
    cToolchainIdeInfoBuilder.addBuiltInIncludeDirectories(
        Arrays.stream(directories).map(ExecutionRootPath::new).collect(Collectors.toList()));
    return this;
  }
}
