package com.android.tools.idea.rendering.tokens;

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

final class BazelBuildServices implements BuildServices<BazelBuildTargetReference> {
  @Override
  public @NotNull BuildStatus getLastCompileStatus(@NotNull BazelBuildTargetReference target) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void buildArtifacts(@NotNull Collection<? extends @NotNull BazelBuildTargetReference> targets) {
    throw new UnsupportedOperationException();
  }
}
