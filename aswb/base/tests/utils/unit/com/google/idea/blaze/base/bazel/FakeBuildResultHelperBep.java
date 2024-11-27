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

import static com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.common.collect.UnmodifiableIterator;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.BuildFinishedId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.BuildStartedId;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.BuildFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public final class FakeBuildResultHelperBep implements BuildResultHelper {
  @Override
  public List<String> getBuildFlags() {
    return ImmutableList.of();
  }


  @Override
  public BuildEventStreamProvider getBepStream(Optional<String> completionBuildId)
    throws GetArtifactsException {
    return new BuildEventStreamProvider() {
      private UnmodifiableIterator<BuildEvent> messages =
        ImmutableList.of(
          BuildEvent.newBuilder()
            .setId(BuildEventId.newBuilder().setStarted(BuildStartedId.getDefaultInstance()))
            .setStarted(BuildStarted.newBuilder().setUuid("buildId"))
            .build(),
          BuildEvent.newBuilder()
            .setId(BuildEventId.newBuilder().setBuildFinished(BuildFinishedId.getDefaultInstance()))
            .setFinished(BuildFinished.newBuilder()).build()
        ).iterator();
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
      public void close() {

      }
    };
  }

  @Override
  public void close() {}
}
