package org.jetbrains.android.compiler;

import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactProperties;
import org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactType;
import org.jetbrains.android.compiler.artifact.AndroidArtifactPropertiesProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;

import java.util.*;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildTargetScopeProvider extends BuildTargetScopeProvider {

  private static boolean isProGuardUsed(@NotNull Project project, @NotNull CompileScope scope) {
    for (Module module : scope.getAffectedModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet != null && facet.getProperties().RUN_PROGUARD) {
        return true;
      }
    }
    final String proguardCfgPathsStr = scope.getUserData(AndroidCompileUtil.PROGUARD_CFG_PATHS_KEY);
    if (proguardCfgPathsStr != null && !proguardCfgPathsStr.isEmpty()) {
      return true;
    }
    final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, scope, false);

    for (Artifact artifact : artifacts) {
      if (artifact.getArtifactType() instanceof AndroidApplicationArtifactType) {
        final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());

        if (properties instanceof AndroidApplicationArtifactProperties) {
          final AndroidApplicationArtifactProperties p = (AndroidApplicationArtifactProperties)properties;

          if (p.isRunProGuard()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope, @NotNull CompilerFilter filter,
                                                         @NotNull Project project, boolean forceBuild) {
    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID) ||
        AndroidProjectInfo.getInstance(project).requiresAndroidModel()) {
      return Collections.emptyList();
    }
    final List<String> appTargetIds = new ArrayList<>();
    final List<String> libTargetIds = new ArrayList<>();
    final List<String> allTargetIds = new ArrayList<>();
    final List<String> manifestMergingTargetIds = new ArrayList<>();
    final boolean fullBuild = AndroidCompileUtil.isFullBuild(baseScope);

    for (Module module : baseScope.getAffectedModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        continue;
      }
      allTargetIds.add(module.getName());

      if (fullBuild) {
        if (facet.getConfiguration().canBeDependency()) {
          libTargetIds.add(module.getName());
        }
        else {
          if (facet.getProperties().ENABLE_MANIFEST_MERGING) {
            manifestMergingTargetIds.add(module.getName());
          }
          appTargetIds.add(module.getName());
        }
      }
    }
    final List<TargetTypeBuildScope> result = new ArrayList<>();
    result.add(CmdlineProtoUtil.createTargetsScope(
      AndroidCommonUtils.MANIFEST_MERGING_BUILD_TARGET_TYPE_ID, manifestMergingTargetIds, forceBuild));

    if (fullBuild && !isProGuardUsed(project, baseScope)) {
      result.add(CmdlineProtoUtil.createTargetsScope(
        AndroidCommonUtils.PRE_DEX_BUILD_TARGET_TYPE_ID, Collections.singletonList("only"), forceBuild));
    }
    result.addAll(Arrays.asList(
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.AAR_DEPS_BUILD_TARGET_TYPE_ID, appTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID, appTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.RESOURCE_CACHING_BUILD_TARGET_ID, allTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.RESOURCE_PACKAGING_BUILD_TARGET_ID, appTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID, appTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.LIBRARY_PACKAGING_BUILD_TARGET_ID, libTargetIds, forceBuild)));
    return result;
  }
}
