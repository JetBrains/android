package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.intellij.openapi.project.Project;

public class LocalBazelInvoker extends AbstractLocalInvoker {
  private static final ImmutableSet<Capability> CAPABILITIES = ImmutableSet.of(
    Capability.SUPPORT_CLI, Capability.ATTACH_JAVA_DEBUGGER, Capability.SUPPORT_QUERY_FILE, Capability.RETURN_PROCESS_HANDLER);


  public LocalBazelInvoker(Project project, BuildSystem buildSystem, String binaryPath) {
    super(project, buildSystem, binaryPath);
  }

  @Override
  public ImmutableSet<Capability> getCapabilities() {
    return CAPABILITIES;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public BuildBinaryType getType() {
    return BuildBinaryType.BAZEL;
  }
}
