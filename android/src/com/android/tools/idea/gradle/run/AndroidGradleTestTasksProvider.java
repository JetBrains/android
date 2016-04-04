package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 1/28/2016
 */
public class AndroidGradleTestTasksProvider implements GradleTestTasksProvider {
  @NotNull
  @Override
  public List<String> getTasks(@NotNull Module module) {
    if (Projects.isBuildWithGradle(module)) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel != null) {
        final String variant = androidModel.getSelectedVariant().getName();
        final String testTask = "test" + StringUtil.capitalize(variant) + "UnitTest";
        return ContainerUtil.list("clean" + StringUtil.capitalize(testTask), testTask);
      }
    }
    return Collections.emptyList();
  }
}
