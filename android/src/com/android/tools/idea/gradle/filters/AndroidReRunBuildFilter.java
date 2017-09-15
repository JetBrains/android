package com.android.tools.idea.gradle.filters;

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.intellij.execution.filters.HyperlinkInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.filters.GradleReRunBuildFilter;

import java.io.File;
import java.util.List;

public class AndroidReRunBuildFilter extends GradleReRunBuildFilter {

  public AndroidReRunBuildFilter(String buildWorkingDir) {
    super(buildWorkingDir);
  }

  @NotNull
  @Override
  protected HyperlinkInfo getHyperLinkInfo(List<String> options) {
    return (project) -> GradleBuildInvoker.getInstance(project).rebuildWithTempOptions(new File(myBuildWorkingDir), options);
  }
}
