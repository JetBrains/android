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
package com.google.idea.blaze.java.projectview;

import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.GlobSectionParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import javax.annotation.Nullable;

/** Section for excluding libraries. */
@Deprecated
public class ExcludedLibrarySection {
  public static final SectionKey<Glob, ListSection<Glob>> KEY = SectionKey.of("excluded_libraries");
  public static final SectionParser PARSER =
      new GlobSectionParser(KEY) {
        @Override
        public boolean isDeprecated() {
          return true;
        }

        @Nullable
        @Override
        public String getDeprecationMessage() {
          return "excluded_libraries has been deprecated. Please use 'exclude_library' instead.";
        }
      };
}
