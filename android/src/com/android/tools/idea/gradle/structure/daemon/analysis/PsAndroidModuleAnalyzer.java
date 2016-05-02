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
package com.android.tools.idea.gradle.structure.daemon.analysis;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsIssueCollection;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidLibraryDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyPath;
import com.android.tools.idea.gradle.structure.navigation.PsModulePath;
import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.builder.model.SyncIssue.SEVERITY_WARNING;
import static com.android.tools.idea.gradle.structure.model.PsIssue.Severity.*;
import static com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.xml.util.XmlStringUtil.escapeString;

public class PsAndroidModuleAnalyzer extends PsModelAnalyzer<PsAndroidModule> {
  private static final Pattern URL_PATTERN = Pattern.compile("\\(?http://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]");

  @NotNull private final PsContext myContext;

  public PsAndroidModuleAnalyzer(@NotNull PsContext context) {
    myContext = context;
  }

  @Override
  protected void doAnalyze(@NotNull PsAndroidModule module, @NotNull PsIssueCollection issueCollection) {
    Multimap<String, SyncIssue> issuesByData = ArrayListMultimap.create();
    AndroidGradleModel gradleModel = module.getGradleModel();
    Collection<SyncIssue> syncIssues = gradleModel.getAndroidProject().getSyncIssues();
    for (SyncIssue syncIssue : syncIssues) {
      String data = nullToEmpty(syncIssue.getData());
      issuesByData.put(data, syncIssue);
    }

    PsModulePath modulePath = new PsModulePath(module);
    module.forEachDependency(dependency -> {
      if (dependency instanceof PsAndroidLibraryDependency && dependency.isDeclared()) {
        PsAndroidLibraryDependency libraryDependency = (PsAndroidLibraryDependency)dependency;
        PsNavigationPath path = new PsLibraryDependencyPath(myContext, libraryDependency);

        PsArtifactDependencySpec resolvedSpec = libraryDependency.getResolvedSpec();
        String issueKey = resolvedSpec.group + GRADLE_PATH_SEPARATOR + resolvedSpec.name;
        Collection<SyncIssue> librarySyncIssues = issuesByData.get(issueKey);
        for (SyncIssue syncIssue : librarySyncIssues) {
          PsIssue issue = createIssueFrom(syncIssue, path, modulePath);
          issueCollection.add(issue);
        }

        PsArtifactDependencySpec declaredSpec = libraryDependency.getDeclaredSpec();
        assert declaredSpec != null;
        String declaredVersion = declaredSpec.version;
        if (declaredVersion != null && declaredVersion.endsWith("+")) {
          String message = "Avoid using '+' in version numbers; can lead to unpredictable and unrepeatable builds.";
          PsIssue issue = new PsIssue(message, "", path, PROJECT_ANALYSIS, WARNING);
          issue.setExtraPath(modulePath);
          issueCollection.add(issue);
        }

        if (libraryDependency.hasPromotedVersion()) {
          String message = "Gradle promoted library version from " + declaredVersion + " to " + resolvedSpec.version;
          String description = "To resolve version conflicts, Gradle by default uses the newest version of a dependency. " +
                               "<a href='https://docs.gradle.org/current/userguide/dependency_management.html'>Open Gradle " +
                               "documentation</a>";
          PsIssue issue = new PsIssue(message, description, path, PROJECT_ANALYSIS, INFO);
          issue.setExtraPath(modulePath);
          issueCollection.add(issue);
        }
      }
    });
  }

  @VisibleForTesting
  @NotNull
  static PsIssue createIssueFrom(@NotNull SyncIssue syncIssue, @NotNull PsNavigationPath path, @Nullable PsNavigationPath extraPath) {
    String message = escapeString(syncIssue.getMessage());
    Matcher matcher = URL_PATTERN.matcher(message);
    boolean result = matcher.find();
    // Replace URLs with <a href='url'>url</a>.
    while (result) {
      String url = matcher.group();
      message = message.replace(url, "<a href='" + url + "'>" + url + "</a>");
      result = matcher.find();
    }
    PsIssue issue = new PsIssue(message, path, PROJECT_ANALYSIS, getSeverity(syncIssue));
    issue.setExtraPath(extraPath);
    return issue;
  }

  @NotNull
  private static PsIssue.Severity getSeverity(@NotNull SyncIssue issue) {
    int severity = issue.getSeverity();
    switch (severity) {
      case SEVERITY_ERROR:
        return ERROR;
      case SEVERITY_WARNING:
        return WARNING;
    }
    return INFO;
  }

  @Override
  @NotNull
  public Class<PsAndroidModule> getSupportedModelType() {
    return PsAndroidModule.class;
  }
}
