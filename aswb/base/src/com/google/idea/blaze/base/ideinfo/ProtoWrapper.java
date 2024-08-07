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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.protobuf.Message;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import javax.annotation.Nullable;

/**
 * Represents a corresponding protobuf {@link Message} from intellij_ide_info.proto or
 * project_data.proto. Provides utility methods to convert to and from a {@link Message} for easy
 * serialization.
 */
public interface ProtoWrapper<P> {
  P toProto();

  static <P, Q, R> R map(
      Iterable<P> iterable, Function<P, Q> function, Collector<Q, ?, R> collector) {
    return Streams.stream(iterable).map(function).collect(collector);
  }

  static <P, Q> ImmutableList<Q> map(Iterable<P> iterable, Function<P, Q> function) {
    return map(iterable, function, ImmutableList.toImmutableList());
  }

  static <P, Q, R, S> ImmutableMap<Q, S> map(
      Map<P, R> map, Function<P, Q> keyFunction, Function<R, S> valueFunction) {
    return map.entrySet().stream()
        .collect(
            ImmutableMap.toImmutableMap(
                e -> keyFunction.apply(e.getKey()), e -> valueFunction.apply(e.getValue())));
  }

  static <P> ImmutableList<P> mapToProtos(Iterable<? extends ProtoWrapper<P>> wrappers) {
    return map(wrappers, ProtoWrapper::toProto);
  }

  static <P, Q> ImmutableMap<P, Q> mapToProtos(
      Map<? extends ProtoWrapper<P>, ? extends ProtoWrapper<Q>> wrappers) {
    return map(wrappers, ProtoWrapper::toProto, ProtoWrapper::toProto);
  }

  static ImmutableList<String> internStrings(Iterable<String> iterable) {
    return map(iterable, ProjectDataInterner::intern);
  }

  static <P> void unwrapAndSetIfNotNull(Consumer<P> setter, @Nullable ProtoWrapper<P> wrapper) {
    if (wrapper != null) {
      setter.accept(wrapper.toProto());
    }
  }

  static <P> void setIfNotNull(Consumer<P> setter, @Nullable P p) {
    if (p != null) {
      setter.accept(p);
    }
  }
}
