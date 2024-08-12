/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiFile;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Variant of the built-in position manager which handles files outside the project. */
class ExternalFilePositionManager extends PositionManagerImpl {

  private final Map<String, PsiFile> qualifiedNameToPsiFile = new HashMap<>();

  ExternalFilePositionManager(DebugProcessImpl debugProcess) {
    super(debugProcess);
  }

  @Nullable
  @Override
  protected PsiFile getPsiFileByLocation(Project project, Location location) {
    PsiFile psiFile = super.getPsiFileByLocation(project, location);
    if (psiFile == null || psiFile instanceof PsiCompiledFile) {
      ReferenceType refType = location.declaringType();
      if (refType != null && qualifiedNameToPsiFile.containsKey(refType.name())) {
        return qualifiedNameToPsiFile.get(refType.name());
      }
    }
    return psiFile;
  }

  @Override
  public List<ClassPrepareRequest> createPrepareRequests(
      ClassPrepareRequestor requestor, SourcePosition position) throws NoDataException {
    indexQualifiedClassNames(position.getFile());
    return super.createPrepareRequests(requestor, position);
  }

  @Override
  public List<ReferenceType> getAllClasses(SourcePosition position) throws NoDataException {
    indexQualifiedClassNames(position.getFile());
    return super.getAllClasses(position);
  }

  @Override
  public List<Location> locationsOfLine(ReferenceType type, SourcePosition position)
      throws NoDataException {
    indexQualifiedClassNames(position.getFile());
    return super.locationsOfLine(type, position);
  }

  @Override
  @Nullable
  public Set<? extends FileType> getAcceptedFileTypes() {
    // This position manager should be used on Java Files only.
    return Collections.singleton(JavaFileType.INSTANCE);
  }

  private void indexQualifiedClassNames(PsiFile psiFile) {
    if (!(psiFile instanceof PsiClassOwner)) {
      return;
    }
    ReadAction.run(
        () -> {
          for (PsiClass psiClass : ((PsiClassOwner) psiFile).getClasses()) {
            qualifiedNameToPsiFile.put(psiClass.getQualifiedName(), psiFile);
          }
        });
  }

  static class Factory extends PositionManagerFactory {
    @Nullable
    @Override
    public PositionManager createPositionManager(DebugProcess process) {
      return process instanceof DebugProcessImpl && Blaze.isBlazeProject(process.getProject())
          ? new ExternalFilePositionManager((DebugProcessImpl) process)
          : null;
    }
  }
}
