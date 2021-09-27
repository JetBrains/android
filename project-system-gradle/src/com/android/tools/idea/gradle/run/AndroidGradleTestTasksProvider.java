package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider;
import com.android.utils.StringHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;

public class AndroidGradleTestTasksProvider implements GradleTestTasksProvider {
  @Override
  public @NotNull List<String> getTasks(@NotNull Module module) {
    // TODO(b/174357889): When running 'all Tests' in module, it will run unit tests only.
    AndroidModuleModel androidModel = getAndroidModelIfPossible(module);
    if (androidModel == null) return Collections.emptyList();
    return getTasksFromAndroidModule(module, androidModel);
  }

  @NotNull
  @Override
  public List<String> getTasks(@NotNull Module module, @NotNull VirtualFile source) {
    AndroidModuleModel androidModel = getAndroidModelIfPossible(module);
    if (androidModel == null) return Collections.emptyList();
    // Filter out non-unit tests artifacts, because this task provider is only for unit tests artifacts.
    if (Objects.requireNonNull(GradleTestArtifactSearchScopes.getInstance(module)).isUnitTestSource(source)) {
      return getTasksFromAndroidModule(module, androidModel);
    }
    return Collections.emptyList();
  }

  private List<String> getTasksFromAndroidModule(@NotNull Module module, @NotNull AndroidModuleModel androidModuleModel) {
    final String variant = androidModuleModel.getSelectedVariant().getName();
    String gradlePath = GradleProjectResolverUtil.getGradlePath(module);
    String taskNamePrefix = "";
    if (gradlePath != null) {
      taskNamePrefix = gradlePath.equals(":") ? gradlePath : gradlePath + ":";
    }
    final String testTask = StringHelper.appendCapitalized("test", variant, "unitTest");
    return Collections.singletonList(taskNamePrefix + testTask);
  }

  @Nullable
  private AndroidModuleModel getAndroidModelIfPossible(@NotNull Module module) {
    if (GradleFacet.isAppliedTo(module)) {
      return AndroidModuleModel.get(module);
    }
    return null;
  }
}
