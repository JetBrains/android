package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider;
import com.android.utils.StringHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;

public class AndroidGradleTestTasksProvider implements GradleTestTasksProvider {
  @Override
  public @NotNull List<String> getTasks(@NotNull Module module) {
    if (GradleFacet.isAppliedTo(module)) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        final String variant = androidModel.getSelectedVariant().getName();
        String gradlePath = GradleProjectResolverUtil.getGradlePath(module);
        String taskNamePrefix = "";
        if (gradlePath != null) {
          taskNamePrefix = gradlePath.equals(":") ? gradlePath : gradlePath + ":";
        }
        final String testTask = "test" + StringUtil.capitalize(variant) + "UnitTest";
        return Arrays.asList(StringHelper.appendCapitalized(taskNamePrefix + "clean", testTask), taskNamePrefix + testTask);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<String> getTasks(@NotNull Module module, @NotNull VirtualFile source) {
    if (Objects.requireNonNull(GradleTestArtifactSearchScopes.getInstance(module)).isUnitTestSource(source)) {
      return getTasks(module);
    }
    return Collections.emptyList();
  }
}
