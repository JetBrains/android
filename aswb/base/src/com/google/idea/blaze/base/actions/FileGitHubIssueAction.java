/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.util.MorePlatformUtils;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import javax.annotation.Nullable;

/**
 * An action to open a GitHub issue with pre-filled information like the versions of the plugin,
 * Bazel and the IDE.
 *
 * @see <a
 *     href="https://help.github.com/en/articles/about-automation-for-issues-and-pull-requests-with-query-parameters">GitHub
 *     docs</a> for more information on issue automation.
 */
public final class FileGitHubIssueAction extends BlazeProjectAction {

  private static final Logger logger = Logger.getInstance(FileGitHubIssueAction.class);

  private static final String BASE_URL = "https://github.com/bazelbuild/intellij/issues/new";

  private static final BoolExperiment enabled =
      new BoolExperiment("github.issue.filing.enabled", true);

  /**
   * A rough heuristic to detect if the machine is a Google-corp machine by executing 'glogin
   * -version'. This is wrapped in a LazyValue because it's called from the action update function
   * that runs twice a second, so it cannot be an expensive computation.
   */
  private static final NotNullLazyValue<Boolean> isCorpMachine =
      NotNullLazyValue.createValue(
          () -> {
            int retVal = ExternalTask.builder().args("glogin", "-version").build().run();
            return retVal == 0;
          });

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    // Hide and disable this action for Google-internal usage.
    if (!enabled.getValue()
        || Blaze.defaultBuildSystem() == BuildSystemName.Blaze
        || isCorpMachine.getValue()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    URL url = getGitHubTemplateURL(project);
    if (url == null) {
      // If, for some reason, we can't construct the URL, open the issues page directly.
      BrowserUtil.browse(BASE_URL);
    } else {
      BrowserUtil.browse(url);
    }
  }

  /**
   * Constructs a GitHub URL with query parameters containing pre-filled information about a user's
   * IDE installation and system.
   *
   * @param project The Bazel project instance.
   * @return an URL containing pre-filled instructions and information about a user's system.
   */
  @Nullable
  protected static URL getGitHubTemplateURL(Project project) {
    // q?body=<param value>
    // https://help.github.com/en/articles/about-automation-for-issues-and-pull-requests-with-query-parameters#supported-query-parameters
    StringBuilder bodyParam = new StringBuilder();

    bodyParam.append("#### Description of the issue. Please be specific.\n");
    bodyParam.append("\n");

    bodyParam.append("#### What's the simplest set of steps to reproduce this issue? ");
    bodyParam.append("Please provide an example project, if possible.\n");
    bodyParam.append("\n");

    bodyParam.append("#### Version information\n");

    // Get the IDE version.
    // e.g. IdeaCommunity: 2019.1.2
    bodyParam.append(
        String.format(
            "%s: %s\n",
            MorePlatformUtils.getProductIdForLogs(),
            ApplicationInfo.getInstance().getFullVersion()));

    // Get information about the operating system.
    // e.g. Platform: Linux 4.19.37-amd64
    bodyParam.append(String.format("Platform: %s %s\n", SystemInfo.OS_NAME, SystemInfo.OS_VERSION));

    // Get the plugin version.
    // e.g. Bazel plugin: 2019.07.23.0.3
    IdeaPluginDescriptor plugin =
        PluginManager.getPlugin(
            PluginManager.getPluginByClassName(FileGitHubIssueAction.class.getName()));

    if (plugin != null) {
      bodyParam.append(
          String.format(
              "%s plugin: %s%s\n",
              plugin.getName(), plugin.getVersion(), plugin.isEnabled() ? "" : " (disabled)"));
    }

    // Get the Bazel version.
    // e.g. Bazel: 0.28.1
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData != null) {
      bodyParam.append(String.format("Bazel: %s\n", projectData.getBlazeVersionData()));
    }

    try {
      return new URL(BASE_URL + "?body=" + URLEncoder.encode(bodyParam.toString(), "UTF-8"));
    } catch (UnsupportedEncodingException | MalformedURLException ex) {
      // If we can't manage to parse the body for some reason (e.g. weird SystemInfo
      // OS_NAME or OS_VERSION), just proceed and open up an empty GitHub issue form.
      logger.error(ex);
      return null;
    }
  }
}
