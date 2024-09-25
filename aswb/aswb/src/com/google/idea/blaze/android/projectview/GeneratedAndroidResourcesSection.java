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
package com.google.idea.blaze.android.projectview;

import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import com.intellij.util.PathUtil;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Project view section white-listing generated resource directories */
public class GeneratedAndroidResourcesSection {
  public static final SectionKey<GenfilesPath, ListSection<GenfilesPath>> KEY =
      SectionKey.of("generated_android_resource_directories");

  public static final SectionParser PARSER = new Parser();

  private GeneratedAndroidResourcesSection() {}

  private static class Parser extends ListSectionParser<GenfilesPath> {
    Parser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected GenfilesPath parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String canonicalPath = PathUtil.getCanonicalPath(parseContext.current().text);

      List<BlazeValidationError> errors = new ArrayList<>();
      if (!GenfilesPath.validate(canonicalPath, errors)) {
        parseContext.addErrors(errors);
        return null;
      }
      return new GenfilesPath(canonicalPath);
    }

    @Override
    protected void printItem(GenfilesPath item, StringBuilder sb) {
      sb.append(item.relativePath);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.DirectoryItem;
    }
  }
}
