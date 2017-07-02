package org.jetbrains.android.actions;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.CommonBundle;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidRunSdkToolAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(AndroidRunSdkToolAction.class);

  public AndroidRunSdkToolAction(String text) {
    super(text);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && !ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    doAction(project);
  }

  public void doAction(@NotNull Project project) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      File androidHome = IdeSdks.getInstance().getAndroidSdkPath();
      if (androidHome != null) {
        doRunTool(project, androidHome.getPath());
        return;
      }
    }

    // We don't check Projects.isGradleProject(project) because it may return false if the last sync failed, even if it is a
    // Gradle project.
    try {
      LocalProperties localProperties = new LocalProperties(project);
      File androidSdkPath = localProperties.getAndroidSdkPath();
      if (androidSdkPath != null) {
        doRunTool(project, androidSdkPath.getPath());
        return;
      }
    }
    catch (IOException ignored) {
      LOG.info(String.format("Unable to read local.properties file from project '%1$s'", project.getName()), ignored);
    }

    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    assert !facets.isEmpty();
    Set<String> sdkSet = new HashSet<>();
    for (AndroidFacet facet : facets) {
      AndroidSdkData sdkData = facet.getConfiguration().getAndroidSdk();
      if (sdkData != null) {
        sdkSet.add(sdkData.getLocation().getPath());
      }
    }
    if (sdkSet.isEmpty()) {
      Messages.showErrorDialog(project, AndroidBundle.message("specify.platform.error"), CommonBundle.getErrorTitle());
      return;
    }
    String sdkPath = sdkSet.iterator().next();
    if (sdkSet.size() > 1) {
      String[] sdks = ArrayUtil.toStringArray(sdkSet);
      int index = Messages.showChooseDialog(project, AndroidBundle.message("android.choose.sdk.label"),
                                            AndroidBundle.message("android.choose.sdk.title"),
                                            Messages.getQuestionIcon(), sdks, sdkPath);
      if (index < 0) {
        return;
      }
      sdkPath = sdks[index];
    }
    doRunTool(project, sdkPath);
  }

  protected abstract void doRunTool(@NotNull Project project, @NotNull String sdkPath);
}
