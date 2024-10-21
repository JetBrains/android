/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.intellij.find.impl.FindInProjectUtil.StringUsageTarget;
import static com.intellij.find.impl.FindInProjectUtil.findUsages;
import static com.intellij.find.impl.FindInProjectUtil.setupProcessPresentation;
import static com.intellij.find.impl.FindInProjectUtil.setupViewPresentation;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUsagesSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageSearcher;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.AdapterProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class SearchInBuildFilesHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final String myTextToFind;

  public SearchInBuildFilesHyperlink(@NotNull final String textToFind) {
    this("searchInBuildFiles", "Search in build.gradle files", textToFind);
  }

  public SearchInBuildFilesHyperlink(@NotNull String url,
                                     @NotNull String text,
                                     @NotNull final String textToFind) {
    super(url, text, AndroidStudioEvent.GradleSyncQuickFix.SEARCH_IN_BUILD_FILES_HYPERLINK);
    myTextToFind = textToFind;
  }

  @Override
  protected void execute(@NotNull final Project project) {
    searchInBuildFiles(myTextToFind, project);
  }

  public static void searchInBuildFiles(@NotNull String text, @NotNull final Project project) {
    FindManager findManager = FindManager.getInstance(project);
    UsageViewManager usageViewManager = UsageViewManager.getInstance(project);

    FindModel findModel = findManager.getFindInProjectModel().clone();
    findModel.setStringToFind(text);
    findModel.setReplaceState(false);
    findModel.setOpenInNewTabVisible(true);
    findModel.setOpenInNewTabEnabled(true);
    findModel.setOpenInNewTab(true);
    findModel.setFileFilter(SdkConstants.FN_BUILD_GRADLE + "," + SdkConstants.FN_BUILD_GRADLE_KTS);

    findManager.getFindInProjectModel().copyFrom(findModel);
    final FindModel findModelCopy = findModel.clone();

    UsageViewPresentation presentation = setupViewPresentation(findModel.isOpenInNewTabEnabled(), findModelCopy);

    boolean showPanelIfOnlyOneUsage = !FindUsagesSettings.getInstance().isSkipResultsWithOneUsage();

    FindUsagesProcessPresentation processPresentation = setupProcessPresentation(showPanelIfOnlyOneUsage, presentation);

    UsageTarget usageTarget = new StringUsageTarget(project, findModel);

    usageViewManager.searchAndShowUsages(new UsageTarget[]{usageTarget}, new Factory<UsageSearcher>() {
      @Override
      public UsageSearcher create() {
        return new UsageSearcher() {
          @Override
          public void generate(@NotNull final Processor<? super Usage> processor) {
            AdapterProcessor<UsageInfo, Usage> consumer =
              new AdapterProcessor<UsageInfo, Usage>(processor, UsageInfo2UsageAdapter.CONVERTER);
            findUsages(findModelCopy, project, consumer, processPresentation);
          }
        };
      }
    }, processPresentation, presentation, null);
  }
}
