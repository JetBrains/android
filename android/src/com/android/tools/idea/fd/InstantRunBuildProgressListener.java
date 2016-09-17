/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.fd;

import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskSkippedResult;
import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InstantRunBuildProgressListener implements ProgressListener {
  private final List<ProgressEvent> myEvents = new ArrayList<>(512);

  @Override
  public void statusChanged(ProgressEvent event) {
    myEvents.add(event);
  }

  public void serializeTo(@NotNull Writer writer) throws IOException {
    if (myEvents.isEmpty()) {
      writer.append("No events");
      return;
    }

    long startTime = myEvents.get(0).getEventTime();

    for (ProgressEvent event : myEvents) {
      writer.append(String.format(Locale.US, "%10d", (event.getEventTime() - startTime)));
      writer.append(' ');

      writer.append(event.toString());
      writer.append('\n');
    }
  }
}
