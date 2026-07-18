/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nullable;

/** Section for miscellaneous project-level experiment and flag overrides. */
public class MiscSection {
  public static final SectionKey<String, ListSection<String>> KEY = SectionKey.of("misc");
  public static final SectionParser PARSER = new MiscSectionParser();

  private static class MiscSectionParser extends ListSectionParser<String> {
    MiscSectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected String parseItem(ProjectViewParser parser, ParseContext parseContext) {
      return parseContext.current().text;
    }

    @Override
    protected void printItem(String item, StringBuilder sb) {
      sb.append(item);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return "Miscellaneous project-level settings and experiment overrides.";
    }
  }

  /** Parses the misc section lines into standard Java properties. */
  public static Properties getProperties(@Nullable ProjectViewSet projectViewSet) {
    Properties properties = new Properties();
    if (projectViewSet != null) {
      List<String> items = projectViewSet.listItems(KEY);
      if (!items.isEmpty()) {
        try {
          properties.load(new StringReader(String.join("\n", items)));
        } catch (IOException ignored) {
        }
      }
    }
    return properties;
  }

  /**
   * Looks up an experiment override string in the project view set's misc section.
   */
  @Nullable
  public static String getOverride(@Nullable ProjectViewSet projectViewSet, String key) {
    if (projectViewSet == null) {
      return null;
    }
    return getProperties(projectViewSet).getProperty(key);
  }

  /**
   * Returns whether the boolean experiment is enabled for the given project view set.
   * If the experiment is defined in the misc section, that value is used.
   * Otherwise, falls back to the application-level experiment value.
   */
  public static boolean isExperimentEnabled(
      @Nullable ProjectViewSet projectViewSet, BoolExperiment experiment) {
    if (projectViewSet != null) {
      String value = getOverride(projectViewSet, experiment.getKey());
      if (value != null) {
        return parseBooleanValue(value);
      }
    }
    return experiment.getValue();
  }

  /**
   * Returns whether the boolean experiment is enabled for the given project.
   */
  public static boolean isExperimentEnabled(
      @Nullable Project project, BoolExperiment experiment) {
    if (project != null) {
      ProjectViewManager manager = ProjectViewManager.getInstance(project);
      if (manager != null) {
        ProjectViewSet projectViewSet = manager.getProjectViewSet();
        return isExperimentEnabled(projectViewSet, experiment);
      }
    }
    return experiment.getValue();
  }

  public static boolean parseBooleanValue(String value) {
    return "1".equals(value) || Boolean.parseBoolean(value);
  }
}
