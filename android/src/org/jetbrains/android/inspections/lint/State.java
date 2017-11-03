package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.Issue;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
* @author Eugene.Kudelevsky
*/
public class State {
  private final Module myModule;
  private final VirtualFile myMainFile;

  private final String myMainFileContent;
  private final List<ProblemData> myProblems = new ArrayList<ProblemData>();
  private final Set<Issue> myIssues;

  private volatile boolean myDirty;

  State(@NotNull Module module,
        @NotNull VirtualFile mainFile,
        @NotNull String mainFileContent,
        @NotNull Set<Issue> issues) {
    myModule = module;
    myMainFile = mainFile;
    myMainFileContent = mainFileContent;
    myIssues = issues;
  }

  @NotNull
  public VirtualFile getMainFile() {
    return myMainFile;
  }

  @NotNull
  public String getMainFileContent() {
    return myMainFileContent;
  }

  public void markDirty() {
    myDirty = true;
  }

  public boolean isDirty() {
    return myDirty;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public List<ProblemData> getProblems() {
    return myProblems;
  }

  @NotNull
  public Set<Issue> getIssues() {
    return myIssues;
  }
}
