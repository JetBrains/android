package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AndroidGradleTestTasksProvider implements GradleTestTasksProvider {
  @NotNull
  @Override
  public List<String> getTasks(@NotNull Module module) {
    if (GradleFacet.isAppliedTo(module)) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        final String variant = androidModel.getSelectedVariant().getName();
        final String testTask = "test" + StringUtil.capitalize(variant) + "UnitTest";
        return Arrays.asList("clean" + StringUtil.capitalize(testTask), testTask);
      }
    }
    return Collections.emptyList();
  }
}
