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
package com.google.idea.blaze.qsync.cc;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcCompilationInfo;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcTargetInfo;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcToolchainInfo;
import java.util.Map;

/**
 * Class encapsulating the info produced by the aspect when building dependencies for cc targets.
 */
@AutoValue
public abstract class CcDependenciesInfo {
  public static final CcDependenciesInfo EMPTY = create(ImmutableMap.of(), ImmutableMap.of());

  public abstract ImmutableMap<Label, CcTargetInfo> targetInfoMap();

  public abstract ImmutableMap<String, CcToolchainInfo> toolchainInfoMap();

  public Builder toBuilder() {
    Builder b = new Builder();
    b.targetInfoMap.putAll(targetInfoMap());
    b.toolchainInfoMap.putAll(toolchainInfoMap());
    return b;
  }

  public static CcDependenciesInfo create(
      ImmutableMap<Label, CcTargetInfo> targetInfoMap,
      ImmutableMap<String, CcToolchainInfo> toolchainInfoMap) {
    return new AutoValue_CcDependenciesInfo(targetInfoMap, toolchainInfoMap);
  }

  public static CcDependenciesInfo create(CcCompilationInfo fromProto) {
    return create(
        Maps.uniqueIndex(fromProto.getTargetsList(), t -> Label.of(t.getLabel())),
        Maps.uniqueIndex(fromProto.getToolchainsList(), t -> t.getId()));
  }

  /**
   * Builder class used to combine several {@link CcCompilationInfo} proto messages into a single
   * object.
   *
   * <p>Note we do not use a standard autovalue builder as the natural way of doing that would rely
   * on {@link ImmutableMap.Builder}, and on the {@code buildKeepingLast} method (since we expect
   * duplicate keys in the case of several sequential dependency builds). We have had issues using
   * that method due to IJ bundling older versions of guava that do not include it (b/255307289).
   */
  public static class Builder {
    private final Map<Label, CcTargetInfo> targetInfoMap = Maps.newHashMap();
    private final Map<String, CcToolchainInfo> toolchainInfoMap = Maps.newHashMap();

    @CanIgnoreReturnValue
    public Builder add(CcCompilationInfo proto) {
      targetInfoMap.putAll(Maps.uniqueIndex(proto.getTargetsList(), t -> Label.of(t.getLabel())));
      toolchainInfoMap.putAll(Maps.uniqueIndex(proto.getToolchainsList(), t -> t.getId()));
      return this;
    }

    public CcDependenciesInfo build() {
      return create(ImmutableMap.copyOf(targetInfoMap), ImmutableMap.copyOf(toolchainInfoMap));
    }
  }
}
