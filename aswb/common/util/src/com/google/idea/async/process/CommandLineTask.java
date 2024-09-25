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
package com.google.idea.async.process;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.execution.ParametersListUtil;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A helper to run simple processes representing command line utilities. */
public interface CommandLineTask {

  /** A builder for an external task */
  class Builder {
    @VisibleForTesting public final ImmutableList.Builder<String> command = ImmutableList.builder();
    private final File workingDirectory;
    private final Map<String, String> environmentVariables = Maps.newHashMap();
    @VisibleForTesting @Nullable public OutputStream stdout;
    @VisibleForTesting @Nullable public OutputStream stderr;
    private boolean redirectErrorStream = false;
    private Duration timeout;

    private Builder(File workingDirectory) {
      this.workingDirectory = workingDirectory;
    }

    @CanIgnoreReturnValue
    public Builder arg(String arg) {
      command.add(arg);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder args(String... args) {
      command.add(args);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder args(Collection<String> args) {
      command.addAll(args);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder args(Stream<String> args) {
      command.addAll(args.iterator());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder redirectStderr(boolean redirectStderr) {
      this.redirectErrorStream = redirectStderr;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder stdout(@Nullable OutputStream stdout) {
      this.stdout = stdout;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder stderr(@Nullable OutputStream stderr) {
      this.stderr = stderr;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder environmentVar(String key, String value) {
      environmentVariables.put(key, value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder environmentVars(Map<String, String> values) {
      environmentVariables.putAll(values);
      return this;
    }

    public CommandLineTask build() {
      return new Runner(
          command.build(),
          ImmutableMap.copyOf(environmentVariables),
          timeout,
          redirectErrorStream,
          stdout,
          stderr,
          workingDirectory);
    }
  }

  // See GeneralCommandLine#ParentEnvironmentType for an explanation of why we do this.

  private static void initializeEnvironment(Map<String, String> envMap) {
    envMap.clear();
    envMap.putAll(EnvironmentUtil.getEnvironmentMap());
  }

  /**
   * Runs the configured command.
   *
   * @return the exit code
   * @throws IOException related to stdout/stderr streams
   * @throws InterruptedException if interrupted while waiting for completion
   * @throws TimeoutException if the invocation times out
   */
  int run() throws IOException, InterruptedException, TimeoutException;

  /** A representation of a configured CLI command. */
  class Runner implements CommandLineTask {
    private static final Logger logger = Logger.getInstance(CommandLineTask.class);

    private final ImmutableList<String> command;
    private final ImmutableMap<String, String> environmentVariables;
    @Nullable private final Duration timeout;
    private final boolean redirectErrorStream;
    private final OutputStream stdout;
    private final OutputStream stderr;
    private final File workingDirectory;

    public Runner(
        ImmutableList<String> command,
        ImmutableMap<String, String> environmentVariables,
        @Nullable Duration timeout,
        boolean redirectErrorStream,
        OutputStream stdout,
        OutputStream stderr,
        File workingDirectory) {
      this.command = command;
      this.environmentVariables = environmentVariables;
      this.timeout = timeout;
      this.redirectErrorStream = redirectErrorStream;
      this.stdout = stdout != null ? stdout : ByteStreams.nullOutputStream();
      this.stderr = stderr != null ? stderr : ByteStreams.nullOutputStream();
      this.workingDirectory = workingDirectory;
    }

    private static void closeQuietly(OutputStream stream) {
      try {
        stream.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public int run() throws IOException, InterruptedException, TimeoutException {

      String logCommand = ParametersListUtil.join(command);
      if (logCommand.length() > 2000) {
        logCommand = logCommand.substring(0, 2000) + " <truncated>";
      }
      logger.info(
          String.format("Running task:\n  %s\n  with PWD: %s", logCommand, workingDirectory));

      try {
        ProcessBuilder builder =
            new ProcessBuilder().command(command).redirectErrorStream(redirectErrorStream);
        builder.directory(workingDirectory);

        Map<String, String> env = builder.environment();
        initializeEnvironment(env);
        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
          env.put(entry.getKey(), entry.getValue());
        }
        env.put("PWD", workingDirectory.getPath());

        try {
          final Process process = builder.start();
          Thread shutdownHook = new Thread(process::destroy);
          try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            // These tasks are non-interactive, so close the stream connected to the process's
            // input.
            process.getOutputStream().close();
            Thread stdoutThread = ProcessUtil.forwardAsync(process.getInputStream(), stdout);
            Thread stderrThread = null;
            if (!redirectErrorStream) {
              stderrThread = ProcessUtil.forwardAsync(process.getErrorStream(), stderr);
            }
            if (timeout != null) {
              if (!process.waitFor(timeout.toMillis(), MILLISECONDS)) {
                throw new TimeoutException();
              }
            } else {
              process.waitFor();
            }
            stdoutThread.join();
            if (!redirectErrorStream) {
              stderrThread.join();
            }
            return process.exitValue();
          } catch (InterruptedException | TimeoutException e) {
            process.destroy();
            throw e;
          } finally {
            try {
              Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
              // we can't remove a shutdown hook if we are shutting down, do nothing about it
            }
          }
        } catch (IOException e) {
          logger.warn(e);
          throw e;
        }
      } finally {
        closeQuietly(stdout);
        closeQuietly(stderr);
      }
    }
  }

  static Builder builder() {
    return new Builder(new File("/"));
  }

  static Builder builder(File workingDirectory) {
    return new Builder(workingDirectory);
  }
}
