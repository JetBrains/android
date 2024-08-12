/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.google.idea.blaze.skylark.debugger.SkylarkDebuggingUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

class SkylarkLineBreakpointType extends XLineBreakpointTypeBase {

  protected SkylarkLineBreakpointType() {
    super("Starlark", "Starlark Line Breakpoints", new SkylarkDebuggerEditorsProvider());
  }

  @Override
  public boolean canPutAt(VirtualFile file, int line, Project project) {
    if (!SkylarkDebuggingUtils.debuggingEnabled(project)) {
      return false;
    }
    // TODO(brendandouglas): disallow breakpoints where they can't be triggered (e.g. on empty
    // lines)
    return BuildFileType.INSTANCE.equals(file.getFileType());
  }

  // SkylarkLineBreakpointType extends from XLineBreakpointTypeBase which uses raw
  // XBreakpointProperties. The raw use of XBreakpointProperties needs to propagate to all affected
  // classes. Check XLineBreakpointTypeBase again after #api212.
  @SuppressWarnings("rawtypes")
  @Override
  public String getDisplayText(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    return getText(breakpoint);
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  @Override
  public String getShortText(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    return getText(breakpoint);
  }

  /** UI-friendly description for a skylark breakpoint. */
  private static String getText(XLineBreakpoint<?> breakpoint) {
    int lineNumber = breakpoint.getLine() + 1;
    String pathDescription = shortenPathString(getPath(breakpoint));
    return pathDescription + ":" + lineNumber;
  }

  @Nullable
  private static Project getProject(XLineBreakpoint<?> breakpoint) {
    return breakpoint instanceof XBreakpointBase
        ? ((XBreakpointBase) breakpoint).getProject()
        : null;
  }

  /**
   * Tries to resolve a workspace-relative path, falling back to returning the full absolute path.
   */
  private static String getPath(XLineBreakpoint<?> breakpoint) {
    String absolutePath = breakpoint.getPresentableFilePath();
    Project project = getProject(breakpoint);
    if (project == null) {
      return absolutePath;
    }
    WorkspacePathResolver pathResolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (pathResolver == null) {
      return absolutePath;
    }
    WorkspacePath path = pathResolver.getWorkspacePath(new File(absolutePath));
    return path != null ? path.relativePath() : absolutePath;
  }

  /**
   * If the path is already sufficiently short, return it unchanged. Otherwise return the last two
   * path components.
   */
  private static String shortenPathString(String path) {
    if (path.length() < 40) {
      return path;
    }
    List<String> list = Splitter.on('/').splitToList(path);
    int cutoff = Math.max(0, list.size() - 2);
    return Joiner.on("/").join(list.subList(cutoff, list.size()));
  }
}
