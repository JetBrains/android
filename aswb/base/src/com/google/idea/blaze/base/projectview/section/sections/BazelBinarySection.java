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

import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import java.io.File;
import javax.annotation.Nullable;

/** Section for a project-specific bazel binary override. */
public class BazelBinarySection {
  public static final SectionKey<File, ScalarSection<File>> KEY = SectionKey.of("bazel_binary");
  public static final SectionParser PARSER = new BazelBinarySectionParser();

  private static class BazelBinarySectionParser extends ScalarSectionParser<File> {
    BazelBinarySectionParser() {
      super(KEY, ':');
    }

    @Override
    @Nullable
    protected File parseItem(ProjectViewParser parser, ParseContext parseContext, String text) {
      if (text.isEmpty()) {
        parseContext.addError(errorMessage(text, "Specify a file path"));
        return null;
      }
      File file = new File(text);
      if (!file.isAbsolute()) {
        // If it's relative, the path will be rooted in the IntelliJ application directory and
        // not the workspace root. So, relativize the path to workspace root here.
        file = parseContext.getWorkspacePathResolver().resolveToFile(text);
      }
      if (!FileOperationProvider.getInstance().isFile(file)) {
        parseContext.addError(errorMessage(text, "Specified file doesn't exist"));
        return null;
      }
      return file;
    }

    private static String errorMessage(String filePath, String error) {
      return String.format("Invalid bazel binary location: %s\n%s", filePath, error);
    }

    @Override
    protected void printItem(StringBuilder sb, File item) {
      sb.append(item.getPath());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return "A project-specific override for the bazel binary location.";
    }
  }
}
