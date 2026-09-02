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
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.execution.process.ProcessHandler;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import kotlin.Unit;

/** Simple implementation of {@link BuildInvoker} for injecting dependencies in test code. */
@AutoValue
public abstract class FakeBuildInvoker implements BuildInvoker {

  public static Builder builder() {
    return new AutoValue_FakeBuildInvoker.Builder()
        .type(BuildBinaryType.NONE)
        .invokeCommand(ImmutableList.of(""))
        .capabilities(ImmutableSet.of())
        .buildSystem(FakeBuildSystem.builder(BuildSystemName.Blaze).build())
        .testResults(null)
        .bepStreamProvider(null);
  }

  @Override
  public abstract BuildBinaryType getType();

  @Override
  public abstract List<String> getInvokeCommand();

  @Override
  public boolean getCanOverrideBinaryPath() {
    return false;
  }

  @Nullable
  public abstract BlazeTestResults getTestResults();

  @Nullable
  public abstract BuildEventStreamProvider getBepStreamProvider();

  @Override
  public <T> T invoke(
      BlazeCommand.Builder blazeCommandBuilder,
      BlazeContext blazeContext,
      BuildSystem.BuildEventStreamConsumer<T> consumer)
      throws BuildException {
    try (BuildEventStreamProvider provider = fakeBuildEventStreamProvider()) {
      return consumer.consume(provider);
    }
  }

  @Override
  public ProcessHandler invokeAsProcessHandler(
      BlazeCommand.Builder blazeCommandBuilder,
      BlazeContext blazeContext,
      BuildSystem.BuildEventStreamConsumer<Unit> consumer) {
    return new FakeProcessHandler();
  }

  @Override
  public InputStream invokeQuery(
      BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext) throws BuildException {
    return InputStream.nullInputStream();
  }

  @Override
  public InputStream invokeInfo(BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext)
      throws BuildException {
    return InputStream.nullInputStream();
  }

  @Override
  @Nullable
  public BlazeInfo getBlazeInfo(BlazeContext blazeContext) {
    return null;
  }

  public BuildEventStreamProvider fakeBuildEventStreamProvider() {
    BuildEventStreamProvider provider = getBepStreamProvider();
    if (provider != null) {
      return provider;
    }
    return new BuildEventStreamProvider() {
      private final UnmodifiableIterator<BuildEventStreamProtos.BuildEvent> messages =
          getBuildEvents().iterator();

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

  private ImmutableList<BuildEventStreamProtos.BuildEvent> getBuildEvents() {
    ImmutableList.Builder<BuildEventStreamProtos.BuildEvent> events = ImmutableList.builder();
    events.add(
        BuildEventStreamProtos.BuildEvent.newBuilder()
            .setId(
                BuildEventStreamProtos.BuildEventId.newBuilder()
                    .setStarted(
                        BuildEventStreamProtos.BuildEventId.BuildStartedId.getDefaultInstance()))
            .setStarted(BuildEventStreamProtos.BuildStarted.newBuilder().setUuid("buildId"))
            .build());

    if (getTestResults() != null) {
      getTestResults()
          .perTargetResults
          .forEach(
              (label, result) ->
                  events.add(
                      BuildEventStreamProtos.BuildEvent.newBuilder()
                          .setId(
                              BuildEventStreamProtos.BuildEventId.newBuilder()
                                  .setTestResult(
                                      BuildEventStreamProtos.BuildEventId.TestResultId.newBuilder()
                                          .setLabel(label.toString())))
                          .setTestResult(
                              BuildEventStreamProtos.TestResult.newBuilder()
                                  .setStatus(getTestStatus(result.getTestStatus())))
                          .build()));
    }

    events.add(
        BuildEventStreamProtos.BuildEvent.newBuilder()
            .setId(
                BuildEventStreamProtos.BuildEventId.newBuilder()
                    .setBuildFinished(
                        BuildEventStreamProtos.BuildEventId.BuildFinishedId.getDefaultInstance()))
            .setFinished(BuildEventStreamProtos.BuildFinished.newBuilder())
            .build());
    return events.build();
  }

  private BuildEventStreamProtos.TestStatus getTestStatus(BlazeTestResult.TestStatus status) {
    return switch (status) {
      case PASSED -> BuildEventStreamProtos.TestStatus.PASSED;
      case FAILED -> BuildEventStreamProtos.TestStatus.FAILED;
      case TIMEOUT -> BuildEventStreamProtos.TestStatus.TIMEOUT;
      case FAILED_TO_BUILD -> BuildEventStreamProtos.TestStatus.FAILED_TO_BUILD;
      default -> BuildEventStreamProtos.TestStatus.NO_STATUS;
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

    public abstract Builder invokeCommand(List<String> invokeCommand);

    public abstract Builder capabilities(com.google.common.collect.ImmutableSet<Capability> value);

    public abstract Builder buildSystem(BuildSystem buildSystem);

    public abstract Builder testResults(@Nullable BlazeTestResults testResults);

    public abstract Builder bepStreamProvider(@Nullable BuildEventStreamProvider value);
  }

  private static class FakeProcessHandler extends ProcessHandler {
    @Override
    protected void destroyProcessImpl() {}

    @Override
    protected void detachProcessImpl() {}

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return null;
    }
  }
}
