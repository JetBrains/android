package com.android.tools.idea.gradle.stubs.gradle;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 5/20/13 8:09 PM
 */
public class GradleProjectStub implements GradleProject {

  @NotNull private final String myName;

  public GradleProjectStub(@NotNull String name) {
    myName = name;
  }

  @Override
  public DomainObjectSet<? extends GradleTask> getTasks() {
    return null;
  }

  @Override
  public GradleProject getParent() {
    return null;
  }

  @Override
  public DomainObjectSet<? extends GradleProject> getChildren() {
    return null;
  }

  @Override
  public String getPath() {
    return null;
  }

  @Override
  public GradleProject findByPath(String path) {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getDescription() {
    return null;
  }
}
