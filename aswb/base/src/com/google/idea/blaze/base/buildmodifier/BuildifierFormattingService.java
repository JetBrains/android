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
package com.google.idea.blaze.base.buildmodifier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.psi.PsiFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Formatting support for BUILD/bzl sources, delegating to an external 'buildifier' binary. */
public final class BuildifierFormattingService extends AsyncDocumentFormattingService {

  static final FeatureRolloutExperiment useNewBuildifierFormattingService =
      new FeatureRolloutExperiment("formatter.api.buildifier");

  static final Supplier<Optional<String>> binaryPath = Suppliers.memoize(() -> getBinary());

  @Override
  @Nullable
  protected FormattingTask createFormattingTask(AsyncFormattingRequest request) {
    return binaryPath
        .get()
        .map(binary -> getCommandLineArgs(request, binary))
        .map(args -> new BuildifierFormattingTask(request, args))
        .orElse(null);
  }

  @Override
  protected String getNotificationGroupId() {
    return "CodeFormatter";
  }

  @Override
  protected String getName() {
    return "buildifier";
  }

  @Override
  public ImmutableSet<Feature> getFeatures() {
    // Although buildifier does NOT support range formatting, we assume it does and then just format
    // the whole file
    return ImmutableSet.of(Feature.FORMAT_FRAGMENTS);
  }

  @Override
  public boolean canFormat(PsiFile file) {
    return useNewBuildifierFormattingService.isEnabled()
        && file instanceof BuildFile
        && binaryPath.get().isPresent();
  }

  private static Optional<String> getBinary() {
    for (BuildifierBinaryProvider provider : BuildifierBinaryProvider.EP_NAME.getExtensions()) {
      String path = provider.getBuildifierBinaryPath();
      if (!Strings.isNullOrEmpty(path)) {
        return Optional.of(path);
      }
    }
    return Optional.empty();
  }

  private static ImmutableList<String> getCommandLineArgs(
      AsyncFormattingRequest request, String binary) {
    ImmutableList.Builder<String> cmd = ImmutableList.builder();
    cmd.add(binary);
    BlazeFileType type = ((BuildFile) request.getContext().getContainingFile()).getBlazeFileType();
    return cmd.add(fileTypeArg(type)).build();
  }

  private static String fileTypeArg(BlazeFileType fileType) {
    return fileType == BlazeFileType.SkylarkExtension ? "--type=bzl" : "--type=build";
  }

  private static final class BuildifierFormattingTask implements FormattingTask {
    private final AsyncFormattingRequest request;
    private final ImmutableList<String> args;
    private Process process;

    public BuildifierFormattingTask(AsyncFormattingRequest request, ImmutableList<String> args) {
      this.request = request;
      this.args = args;
    }

    @Override
    public void run() {
      try {
        process = new ProcessBuilder(args).start();
        process.getOutputStream().write(request.getDocumentText().getBytes(UTF_8));
        process.getOutputStream().close();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));
        String formattedText = CharStreams.toString(reader);
        boolean exited = process.waitFor(10, SECONDS);

        if (!exited) {
          process.destroyForcibly();
          request.onError("Error running buildifier", "process timed out.");
        } else if (process.exitValue() == 0) {
          request.onTextReady(formattedText);
        } else {
          request.onError(
              "Please fix syntax errors",
              "buildifier failed. Does "
                  + request.getContext().getContainingFile().getName()
                  + " have syntax errors?");
        }
      } catch (InterruptedException e) {
        process.destroy();
        Thread.currentThread().interrupt();
      } catch (IOException e) {
        request.onError("Error running buildifier", e.getMessage());
      }
    }

    @Override
    public boolean cancel() {
      if (process != null) {
        process.destroy();
      }
      return true;
    }

    @Override
    public boolean isRunUnderProgress() {
      return true;
    }
  }
}
