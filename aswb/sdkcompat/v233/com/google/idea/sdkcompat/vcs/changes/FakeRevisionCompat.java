package com.google.idea.sdkcompat.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.FakeRevision;

/** Compat class for FakeRevision constructor changes. */
public class FakeRevisionCompat extends FakeRevision {
  public FakeRevisionCompat(Project project, FilePath file) {
    super(project, file);
  }
}
