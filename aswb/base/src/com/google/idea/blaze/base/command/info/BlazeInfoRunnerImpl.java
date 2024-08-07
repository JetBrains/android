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
package com.google.idea.blaze.base.command.info;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

class BlazeInfoRunnerImpl extends BlazeInfoRunner {
  @Override
  public ListenableFuture<byte[]> runBlazeInfoGetBytes(
      Project project,
      BuildInvoker invoker,
      BlazeContext context,
      List<String> blazeFlags,
      String key) {
    return BlazeExecutor.getInstance()
        .submit(
            () -> {
              BlazeCommand.Builder builder = BlazeCommand.builder(invoker, BlazeCommandName.INFO);
              builder.addBlazeFlags(blazeFlags);
              if (key != null) {
                builder.addBlazeFlags(key);
              }
              try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper();
                  InputStream blazeInfoStream =
                      invoker
                          .getCommandRunner()
                          .runBlazeInfo(project, builder, buildResultHelper, context)) {
                return blazeInfoStream.readAllBytes();
              }
            });
  }

  @Override
  public ListenableFuture<String> runBlazeInfo(
      Project project,
      BuildInvoker invoker,
      BlazeContext context,
      List<String> blazeFlags,
      String key) {
    return Futures.transform(
        runBlazeInfoGetBytes(project, invoker, context, blazeFlags, key),
        bytes -> new String(bytes, StandardCharsets.UTF_8).trim(),
        BlazeExecutor.getInstance().getExecutor());
  }

  @Override
  public ListenableFuture<BlazeInfo> runBlazeInfo(
      Project project,
      BuildInvoker invoker,
      BlazeContext context,
      BuildSystemName buildSystemName,
      List<String> blazeFlags) {
    return Futures.transform(
        runBlazeInfoGetBytes(project, invoker, context, blazeFlags, /* key= */ null),
        bytes ->
            BlazeInfo.create(
                buildSystemName,
                parseBlazeInfoResult(new String(bytes, StandardCharsets.UTF_8).trim())),
        BlazeExecutor.getInstance().getExecutor());
  }
  private static ImmutableMap<String, String> parseBlazeInfoResult(String blazeInfoString) {
    ImmutableMap.Builder<String, String> blazeInfoMapBuilder = ImmutableMap.builder();
    String[] blazeInfoLines = blazeInfoString.split("\n");
    for (String blazeInfoLine : blazeInfoLines) {
      // Just split on the first ":".
      String[] keyValue = blazeInfoLine.split(":", 2);
      if (keyValue.length != 2) {
        // ignore any extraneous stdout
        continue;
      }
      String key = keyValue[0].trim();
      String value = keyValue[1].trim();
      blazeInfoMapBuilder.put(key, value);
    }
    return blazeInfoMapBuilder.build();
  }
}
