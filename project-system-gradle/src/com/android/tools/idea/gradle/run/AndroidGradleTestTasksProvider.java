package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes;
import com.android.utils.StringHelper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider;

import java.util.Collections;
import java.util.List;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;

@Deprecated
// TODO(b/205694878): Review this class and delete if no longer needed after next platform merge.
public class AndroidGradleTestTasksProvider implements GradleTestTasksProvider {
  @Override
  public @NotNull List<String> getTasks(@NotNull Module module) {
    // TODO(b/174357889): When running 'all Tests' in module, it will run unit tests only.
    GradleAndroidModel androidModel = getAndroidModelIfPossible(module);
    if (androidModel == null) return Collections.emptyList();
    return getTasksFromAndroidModule(module, androidModel);
  }

  @NotNull
  @Override
  public List<String> getTasks(@NotNull Module module, @NotNull VirtualFile source) {
    GradleAndroidModel androidModel = getAndroidModelIfPossible(module);
    if (androidModel == null) return Collections.emptyList();
    // Filter out non-unit tests artifacts, because this task provider is only for unit tests artifacts.
    //also, filter out directories test sources as these are handled in a special way by the AllInDirectoryGradleConfigurationProducer,
    // and for which, we do need to inject some TestData that the Gradle producers handle properly when generating test tasks.
    if (Objects.requireNonNull(GradleTestArtifactSearchScopes.getInstance(module)).isUnitTestSource(source) && !source.isDirectory()) {
      return getTasksFromAndroidModule(module, androidModel);
    }
    return Collections.emptyList();
  }

  private List<String> getTasksFromAndroidModule(@NotNull Module module, @NotNull GradleAndroidModel androidModuleModel) {
    final String variant = androidModuleModel.getSelectedVariant().getName();
    String gradlePath = GradleProjectResolverUtil.getGradlePath(module);
    String taskNamePrefix = "";
    if (gradlePath != null) {
      taskNamePrefix = gradlePath.equals(":") ? gradlePath : gradlePath + ":";
    }
    return Collections.singletonList(taskNamePrefix + getUnitTestTask(variant));
  }

  /**
   * Get test tasks for a given android model.
   * @return the test task for the module. This does not include the full task path, but only the task name.
   * The full task path will be configured later at the execution level in the Gradle producers.
   */
  public static String getTasksFromAndroidModuleData(@NotNull GradleAndroidModel androidModuleModel) {
    final String variant = androidModuleModel.getSelectedVariant().getName();
    return getUnitTestTask(variant);
  }

  static private String getUnitTestTask(@NotNull String variantName) {
    return StringHelper.appendCapitalized("test", variantName, "unitTest");
  }

  @Nullable
  private GradleAndroidModel getAndroidModelIfPossible(@NotNull Module module) {
    if (GradleFacet.isAppliedTo(module)) {
      return GradleAndroidModel.get(module);
    }
    return null;
  }
}
