/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import java.util.Arrays;
import java.util.List;
import javax.annotation.concurrent.Immutable;

/** A command to issue to Blaze/Bazel on the command line. */
@Immutable
public final class BlazeCommand {

  private final List<String> invokeCommand;
  private final BlazeCommandName name;
  private final ImmutableList<String> blazeCmdlineFlags;
  private final ImmutableList<String> blazeStartupFlags;

  private BlazeCommand(
      List<String> invokeCommand,
      BlazeCommandName name,
      ImmutableList<String> blazeStartupFlags,
      ImmutableList<String> blazeCmdlineFlags) {
    this.invokeCommand = invokeCommand;
    this.name = name;
    this.blazeCmdlineFlags = blazeCmdlineFlags;
    this.blazeStartupFlags = blazeStartupFlags;
  }

  public BlazeCommandName getName() {
    return name;
  }

  public ImmutableList<String> toArgumentList() {
    return ImmutableList.<String>builder()
        .addAll(blazeStartupFlags)
        .add(name.toString())
        .addAll(blazeCmdlineFlags)
        .build();
  }

  public ImmutableList<String> toList() {
    return ImmutableList.<String>builder()
        .addAll(invokeCommand)
        .addAll(blazeStartupFlags)
        .add(name.toString())
        .addAll(blazeCmdlineFlags)
        .build();
  }

  @Override
  public String toString() {
    return Joiner.on(' ').join(toList());
  }

  public static Builder builder(BuildInvoker invoker, BlazeCommandName name) {
    return new Builder(invoker.getInvokeCommand(), name);
  }


  /**
   * Returns a [BlazeCommand.Builder] with the given blaze binary path.
   * <p>
   * This Builder is used by run configs where the user specifies a single blazeBinaryPath which is used instead of the invoker's
   * default binary path (if the invoker supports path overrides, i.e. canOverrideBinaryPath).
   */
  public static Builder builder(BuildInvoker invoker, BlazeCommandName name, String blazeBinaryPath) {
    return new Builder(invoker.getInvokeCommandForBinaryPath(blazeBinaryPath), name);
  }

  /** Builder for a blaze command */
  public static class Builder {
    private final List<String> invokeCommand;
    private final BlazeCommandName name;
    private boolean invokeParallel;
    private final ImmutableList.Builder<String> blazeStartupFlags = ImmutableList.builder();
    private final ImmutableList.Builder<String> targets = ImmutableList.builder();
    private final ImmutableList.Builder<String> blazeCmdlineFlags = ImmutableList.builder();
    private final ImmutableList.Builder<String> exeFlags = ImmutableList.builder();

    public Builder(List<String> invokeCommand, BlazeCommandName name) {
      this.invokeCommand = invokeCommand;
      this.name = name;
      this.invokeParallel = false;
      // Tell forge what tool we used to call blaze so we can track usage.
      addBlazeFlags(BlazeFlags.getToolTagFlag());
    }

    private ImmutableList<String> getArguments() {
      ImmutableList.Builder<String> arguments = ImmutableList.builder();
      arguments.addAll(blazeCmdlineFlags.build());

      // Need to add '--' before targets, to support subtracted/excluded targets.
      arguments.add("--");

      // Trust the user's ordering of the targets since order matters to blaze
      arguments.addAll(targets.build());

      arguments.addAll(exeFlags.build());
      return arguments.build();
    }

    public BlazeCommand build() {
      return new BlazeCommand(invokeCommand, name, blazeStartupFlags.build(), getArguments());
    }

    public boolean isInvokeParallel() {
      return invokeParallel;
    }

    @CanIgnoreReturnValue
    public Builder setInvokeParallel(boolean invokeParallel) {
      this.invokeParallel = invokeParallel;
      return this;
    }


    @CanIgnoreReturnValue
    public Builder addTargetStrings(String... targets) {
      return addTargetStrings(Arrays.asList(targets));
    }

    @CanIgnoreReturnValue
    public Builder addTargetStrings(List<? extends String> targets) {
      this.targets.addAll(targets);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addExeFlags(String... flags) {
      return addExeFlags(Arrays.asList(flags));
    }

    @CanIgnoreReturnValue
    public Builder addExeFlags(List<String> flags) {
      this.exeFlags.addAll(flags);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addBlazeFlags(String... flags) {
      return addBlazeFlags(Arrays.asList(flags));
    }

    @CanIgnoreReturnValue
    public Builder addBlazeFlags(List<String> flags) {
      this.blazeCmdlineFlags.addAll(flags);
      return this;
    }

    /**
     * This function is designed to add start up flags to blaze build command only when blazerc file
     * is not accessible (e.g. build api). If there is an already running Blaze server and the
     * startup options do not match, it will be restarted. So do not use this function unless you
     * cannot update blazerc used by blaze and you are sure new flags will not break running blaze
     * server.
     */
    @CanIgnoreReturnValue
    public BlazeCommand.Builder addBlazeStartupFlags(List<String> flags) {
      this.blazeStartupFlags.addAll(flags);
      return this;
    }
  }
}
