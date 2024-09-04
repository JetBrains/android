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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.BuildStartedId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.ConfigurationId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TargetCompletedId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildStarted;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.Configuration;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.NamedSetOfFiles;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.OutputGroup;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TargetComplete;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/** Utilities to create build event proto components and stream. */
public final class BepUtils {
  private BepUtils() {}

  public static InputStream asInputStream(Iterable<BuildEvent> events) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (BuildEvent event : events) {
      event.writeDelimitedTo(output);
    }
    return new ByteArrayInputStream(output.toByteArray());
  }

  public static BuildEvent started(UUID uuid) {
    return BuildEvent.newBuilder()
        .setId(BuildEventId.newBuilder().setStarted(BuildStartedId.getDefaultInstance()))
        .setStarted(BuildStarted.newBuilder().setUuid(uuid.toString()).build())
        .build();
  }

  public static BuildEvent targetComplete(
      String label, String configId, List<OutputGroup> outputGroups) {
    return BuildEvent.newBuilder()
        .setId(
            BuildEventId.newBuilder()
                .setTargetCompleted(
                    TargetCompletedId.newBuilder()
                        .setConfiguration(ConfigurationId.newBuilder().setId(configId).build())
                        .setLabel(label)))
        .setCompleted(TargetComplete.newBuilder().addAllOutputGroup(outputGroups))
        .build();
  }

  public static OutputGroup outputGroup(String name, List<String> fileSets) {
    OutputGroup.Builder builder = OutputGroup.newBuilder().setName(name);
    fileSets.forEach(s -> builder.addFileSets(NamedSetOfFilesId.newBuilder().setId(s)));
    return builder.build();
  }

  public static BuildEvent configuration(String name, String mnemonic) {
    return BuildEvent.newBuilder()
        .setId(BuildEventId.newBuilder().setConfiguration(ConfigurationId.newBuilder().setId(name)))
        .setConfiguration(Configuration.newBuilder().setMnemonic(mnemonic))
        .build();
  }

  public static BuildEvent setOfFiles(List<String> filePaths, String id) {
    return setOfFiles(filePaths, id, ImmutableList.of());
  }

  public static BuildEvent setOfFiles(List<String> filePaths, String id, List<String> fileSetDeps) {
    return BuildEvent.newBuilder()
        .setId(BuildEventId.newBuilder().setNamedSet(NamedSetOfFilesId.newBuilder().setId(id)))
        .setNamedSetOfFiles(
            NamedSetOfFiles.newBuilder()
                .addAllFiles(
                    filePaths.stream().map(BepUtils::toFileEvent).collect(toImmutableList()))
                .addAllFileSets(
                    fileSetDeps.stream()
                        .map(dep -> NamedSetOfFilesId.newBuilder().setId(dep).build())
                        .collect(toImmutableList())))
        .build();
  }

  private static BuildEventStreamProtos.File toFileEvent(String filePath) {
    return BuildEventStreamProtos.File.newBuilder()
        .setUri(new File(filePath).toURI().toString())
        .setName(filePath)
        .build();
  }

  public static ParsedBepOutput parsedBep(List<BuildEvent> events)
      throws IOException, BuildEventStreamException {
    return ParsedBepOutput.parseBepArtifacts(asInputStream(events));
  }
}
