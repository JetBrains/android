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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader;
import com.android.tools.idea.gradle.project.sync.compatibility.version.VersionRange;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.google.common.base.Splitter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker.VERSION_COMPATIBILITY_ISSUE_GROUP;
import static com.intellij.util.ArrayUtil.toStringArray;

class VersionIncompatibility {
  @NotNull private final Module myModule;
  @NotNull private final CompatibilityCheck myCompatibilityCheck;
  @NotNull private final Pair<ComponentVersionReader, String> myReaderAndVersion;
  @NotNull private final Component myRequirement;
  @NotNull private final ComponentVersionReader myRequirementVersionReader;

  @NotNull private final List<String> myMessages = new ArrayList<>();

  VersionIncompatibility(@NotNull Module module,
                         @NotNull CompatibilityCheck compatibilityCheck,
                         @NotNull Pair<ComponentVersionReader, String> readerAndVersion,
                         @NotNull Component requirement,
                         @NotNull ComponentVersionReader requirementVersionReader) {
    myModule = module;
    myCompatibilityCheck = compatibilityCheck;
    myReaderAndVersion = readerAndVersion;
    myRequirement = requirement;
    myRequirementVersionReader = requirementVersionReader;
  }

  boolean hasMessages() {
    return !myMessages.isEmpty();
  }

  void addMessage(@NotNull String message) {
    myMessages.add(message);
  }

  void reportMessages(@NotNull Project project) {
    ComponentVersionReader reader = myReaderAndVersion.getFirst();
    String componentName = reader.getComponentName();
    String version = myReaderAndVersion.getSecond();

    String requirementComponentName = myRequirementVersionReader.getComponentName();

    StringBuilder msg = new StringBuilder();
    msg.append(componentName).append(" ").append(version);

    PositionInFile position = reader.getVersionSource(myModule);
    if (!reader.isProjectLevel() && position == null) {
      msg.append(", in module '").append(myModule.getName()).append(",'");
    }
    msg.append(" requires ").append(requirementComponentName).append(" ");

    VersionRange requirementVersionRange = myRequirement.getVersionRange();
    msg.append(requirementVersionRange.getDescription());

    int messageCount = myMessages.size();
    if (messageCount == 1) {
      msg.append(" ").append(myMessages.get(0));
    }
    else if (messageCount > 1) {
      msg.append("<ul>");
      for (String message : myMessages) {
        msg.append("<li>").append(message).append("</li>");
      }
      msg.append("</ul>");
    }

    MessageType messageType = myCompatibilityCheck.getType();
    SyncMessage message;

    List<String> textLines = new ArrayList<>();
    textLines.add(msg.toString());
    String failureMsg = myRequirement.getFailureMessage();
    if (failureMsg != null) {
      List<String> lines = Splitter.on("\\n").omitEmptyStrings().splitToList(failureMsg);
      textLines.addAll(lines);
    }
    String[] text = toStringArray(textLines);

    if (position != null) {
      message = new SyncMessage(project, VERSION_COMPATIBILITY_ISSUE_GROUP, messageType, position, text);
    }
    else {
      message = new SyncMessage(VERSION_COMPATIBILITY_ISSUE_GROUP, messageType, text);
    }

    message.add(myRequirementVersionReader.getQuickFixes(myModule, requirementVersionRange, position));

    GradleSyncMessages.getInstance(project).report(message);
  }

  @NotNull
  MessageType getType() {
    return myCompatibilityCheck.getType();
  }
}
