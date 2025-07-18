/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.execution.process.ProcessHandler;
import java.io.InputStream;
import java.util.Optional;
import javax.annotation.Nullable;

/** Simple implementation of {@link BuildInvoker} for injecting dependencies in test code. */
@AutoValue
public abstract class FakeBuildInvoker implements BuildInvoker {

  public static Builder builder() {
    return new AutoValue_FakeBuildInvoker.Builder()
        .type(BuildBinaryType.NONE)
        .binaryPath("")
        .capabilities(ImmutableSet.of())
        .buildSystem(FakeBuildSystem.builder(BuildSystemName.Blaze).build());
  }

  @Override
  public abstract BuildBinaryType getType();

  @Override
  public abstract String getBinaryPath();

  @Override
  public BuildEventStreamProvider invoke(BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext) {
    return fakeBuildEventStreamProvider();
  }

  @Override
  public ProcessHandler invokeAsProcessHandler(BlazeCommand.Builder blazeCommandBuilder,
                                               BlazeContext blazeContext) {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream invokeQuery(BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext) throws BuildException {
    return InputStream.nullInputStream();
  }

  @Override
  public InputStream invokeInfo(BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext) {
    return InputStream.nullInputStream();
  }

  @Override
  @Nullable
  public BlazeInfo getBlazeInfo(BlazeContext blazeContext) {
    return null;
  }

  private BuildEventStreamProvider fakeBuildEventStreamProvider() {
    return new BuildEventStreamProvider() {
      private UnmodifiableIterator<BuildEventStreamProtos.BuildEvent> messages =
          ImmutableList.of(
                  BuildEventStreamProtos.BuildEvent.newBuilder()
                      .setId(
                          BuildEventStreamProtos.BuildEventId.newBuilder()
                              .setStarted(
                                  BuildEventStreamProtos.BuildEventId.BuildStartedId
                                      .getDefaultInstance()))
                      .setStarted(
                          BuildEventStreamProtos.BuildStarted.newBuilder().setUuid("buildId"))
                      .build(),
                  BuildEventStreamProtos.BuildEvent.newBuilder()
                      .setId(
                          BuildEventStreamProtos.BuildEventId.newBuilder()
                              .setBuildFinished(
                                  BuildEventStreamProtos.BuildEventId.BuildFinishedId
                                      .getDefaultInstance()))
                      .setFinished(BuildEventStreamProtos.BuildFinished.newBuilder())
                      .build())
              .iterator();

      @Override
      public Object getId() {
        return Optional.empty();
      }

      @Nullable
      @Override
      public BuildEventStreamProtos.BuildEvent getNext() {
        if (messages.hasNext()) {
          return messages.next();
        }
        return null;
      }

      @Override
      public long getBytesConsumed() {
        return 0;
      }

      @Override
      public void close() {}
    };
  }

  /**
   * Builder class for instances of {@link com.google.idea.blaze.base.bazel.FakeBuildInvoker}.
   *
   * <p>Use {@link FakeBuildInvoker#builder()} to get an instance.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FakeBuildInvoker build();

    public abstract Builder type(BuildBinaryType type);

    public abstract Builder binaryPath(String binaryPath);

    public abstract Builder capabilities(com.google.common.collect.ImmutableSet<Capability> value);

    public abstract Builder buildSystem(BuildSystem buildSystem);
  }
}
