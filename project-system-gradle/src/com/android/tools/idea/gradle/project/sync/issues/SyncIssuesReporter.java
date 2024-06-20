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
package com.android.tools.idea.gradle.project.sync.issues;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.build.output.ExplainBuildErrorFilterKt;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.studiobot.StudioBot;
import com.android.tools.idea.studiobot.StudioBotBundle;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.NonInjectable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

public class SyncIssuesReporter {
  @NotNull private final Map<Integer, BaseSyncIssuesReporter> myStrategies = new HashMap<>(12);
  @NotNull private final BaseSyncIssuesReporter myDefaultMessageFactory;

  public static String consoleLinkUnderlinedText = ">> " + StudioBotBundle.message("studiobot.ask.text");
  public static String consoleLinkWithSeparatorText = consoleLinkUnderlinedText + " ";

  @NotNull
  public static SyncIssuesReporter getInstance() {
    return ApplicationManager.getApplication().getService(SyncIssuesReporter.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public SyncIssuesReporter() {
    this(
      new UnresolvedDependenciesReporter(),
      new UnsupportedGradleReporter(),
      new BuildToolsTooLowReporter(),
      new MissingSdkPackageSyncIssuesReporter(),
      new MinSdkInManifestIssuesReporter(),
      new TargetSdkInManifestIssuesReporter(),
      new DeprecatedConfigurationReporter(),
      new MissingSdkIssueReporter(),
      new OutOfDateThirdPartyPluginIssueReporter(),
      new CxxConfigurationIssuesReporter(),
      new AndroidXUsedReporter(),
      new JcenterDeprecatedReporter(),
      new AgpUsedJavaTooLowReporter(),
      new ExceptionSyncIssuesReporter(),
      new CompileSdkVersionTooHighReporter(),
      new MissingComposeCompilerGradlePluginReporter()
    );
  }

  @NonInjectable
  @VisibleForTesting
  SyncIssuesReporter(@NotNull BaseSyncIssuesReporter... strategies) {
    for (BaseSyncIssuesReporter strategy : strategies) {
      int issueType = strategy.getSupportedIssueType();
      myStrategies.put(issueType, strategy);
    }
    myDefaultMessageFactory = new UnhandledIssuesReporter();
  }

  /**
   * Reports all sync errors for the provided collection of modules.
   */
  public void report(@NotNull Map<Module, List<IdeSyncIssue>> issuesByModules, @SystemIndependent String rootProjectPath) {
    if (issuesByModules.isEmpty()) {
      return;
    }

    Map<Integer, List<IdeSyncIssue>> syncIssues = new LinkedHashMap<>();
    // Note: Since the SyncIssues don't store the module they come from their hashes will be the same.
    // As such we use an IdentityHashMap to ensure different issues get hashed to different values.
    Map<IdeSyncIssue, Module> moduleMap = new IdentityHashMap<>();
    Map<Module, VirtualFile> buildFileMap = new LinkedHashMap<>();

    Project project = null;
    // Go through all the issue, grouping them by their type. In doing so we also populate
    // the module and buildFile maps which will be used by each reporter.
    for (Module module : issuesByModules.keySet()) {
      project = module.getProject();
      buildFileMap.put(module, getGradleBuildFile(module));

      issuesByModules.get(module).forEach(issue -> {
        if (issue != null) {
          syncIssues.computeIfAbsent(issue.getType(), (type) -> new ArrayList<>()).add(issue);
          moduleMap.put(issue, module);
        }
      });
    }

    // Make sure errors are reported before warnings, note this assumes that each type of issue will be the same type.
    // Otherwise errors and warnings may be interleaved.
    Map<Integer, List<IdeSyncIssue>> sortedSyncIssues = syncIssues.entrySet().stream().sorted(
      Collections.reverseOrder(Map.Entry.comparingByValue(Comparator.comparing(issues -> issues.get(0).getSeverity())))).collect(
      Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldVal, newVal) -> oldVal, LinkedHashMap::new));
    SyncIssueUsageReporter syncIssueUsageReporter = SyncIssueUsageReporter.Companion.getInstance(project);
    final var syncMessages = new ArrayList<SyncMessage>();
    for (Map.Entry<Integer, List<IdeSyncIssue>> entry : sortedSyncIssues.entrySet()) {
      BaseSyncIssuesReporter strategy = myStrategies.get(entry.getKey());
      if (strategy == null) {
        strategy = myDefaultMessageFactory;
      }
      List<? extends SyncMessage> messages = strategy.reportAll(entry.getValue(), moduleMap, buildFileMap);

      SyncIssueUsageReporterUtils.collect(syncIssueUsageReporter, entry.getKey(), messages);

      syncMessages.addAll(messages);
    }
    final var gradleSyncMessages = GradleSyncMessages.getInstance(project);

    StudioBot studioBot = StudioBot.Companion.getInstance();
    if (studioBot.isAvailable()) {
      // this only covers sync warning, but sync errors are handled by AndroidGradleExecutionConsoleManager
      addIssueExplanationLinks(studioBot, syncMessages);
    }

    for (SyncMessage syncMessage : syncMessages) {
      gradleSyncMessages.report(syncMessage);
    }
    Project finalProject = project;
    Runnable reportTask = () -> {
      SyncIssueUsageReporter.Companion.getInstance(finalProject).reportToUsageTracker(rootProjectPath);
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      reportTask.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(reportTask);
    }
  }

  private static void addIssueExplanationLinks(@NotNull StudioBot studioBot, @NotNull List<SyncMessage> syncMessages) {
    for (SyncMessage syncMessage : syncMessages) {
      final var message = syncMessage.getText();
      syncMessage.add(new SyncIssueNotificationHyperlink(
        "explain.issue",
        consoleLinkUnderlinedText,
        AndroidStudioEvent.GradleSyncQuickFix.UNKNOWN_GRADLE_SYNC_QUICK_FIX
      ) {
        @Override
        protected void execute(@NotNull Project project) {
          ExplainBuildErrorFilterKt.sendChatQueryIfContextAllowed(studioBot, project,
                                                                  "Explain gradle sync issue: " + message,
                                                                  StudioBot.RequestSource.SYNC);
        }
      });
    }
  }

  @VisibleForTesting
  @NotNull
  Map<Integer, BaseSyncIssuesReporter> getStrategies() {
    return myStrategies;
  }

  @VisibleForTesting
  @NotNull
  BaseSyncIssuesReporter getDefaultMessageFactory() {
    return myDefaultMessageFactory;
  }
}
