/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.console;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream.LineProcessor;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;

/** Provides output line processors run by default on blaze console output. */
public interface BlazeConsoleLineProcessorProvider {

  ExtensionPointName<BlazeConsoleLineProcessorProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeConsoleLineProcessorProvider");

  static ImmutableList<LineProcessor> getAllStderrLineProcessors(BlazeContext context) {
    return Arrays.stream(EP_NAME.getExtensions())
        .flatMap(p -> p.getStderrLineProcessors(context).stream())
        .collect(toImmutableList());
  }

  default ImmutableList<LineProcessor> getStderrLineProcessors(BlazeContext context) {
    return ImmutableList.of();
  }

  /** Line processors which should be used for all blaze invocations. */
  class GeneralProvider implements BlazeConsoleLineProcessorProvider {

    @Override
    public ImmutableList<LineProcessor> getStderrLineProcessors(BlazeContext context) {
      return ImmutableList.of(new PrintOutputLineProcessor(context));
    }
  }
}
