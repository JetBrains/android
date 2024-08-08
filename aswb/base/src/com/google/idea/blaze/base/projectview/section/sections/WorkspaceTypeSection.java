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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import java.util.List;
import javax.annotation.Nullable;

/** The type of your workspace. */
public class WorkspaceTypeSection {
  public static final SectionKey<WorkspaceType, ScalarSection<WorkspaceType>> KEY =
      SectionKey.of("workspace_type");
  public static final SectionParser PARSER = new WorkspaceTypeSectionParser();

  private static class WorkspaceTypeSectionParser extends ScalarSectionParser<WorkspaceType> {
    public WorkspaceTypeSectionParser() {
      super(KEY, ':');
    }

    @Override
    @Nullable
    protected WorkspaceType parseItem(
        ProjectViewParser parser, ParseContext parseContext, String text) {
      List<BlazeValidationError> errors = Lists.newArrayList();
      WorkspaceType workspaceType = WorkspaceType.fromString(text);
      if (workspaceType == null) {
        parseContext.addError("Invalid workspace type: " + text);
      }
      parseContext.addErrors(errors);
      return workspaceType;
    }

    @Override
    protected void printItem(StringBuilder sb, WorkspaceType item) {
      sb.append(item.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }
  }
}
