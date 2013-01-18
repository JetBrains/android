/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import lombok.ast.Node;
import lombok.ast.Position;

import java.io.File;

public class LombokPsiParser implements IJavaParser {
  private final LintClient myClient;
  private AccessToken myLock;

  public LombokPsiParser(LintClient client) {
    myClient = client;
  }

  @Nullable
  @Override
  public Node parseJava(final JavaContext context) {
    assert myLock == null;
    myLock = ApplicationManager.getApplication().acquireReadActionLock();
    Node compilationUnit = parse(context);
    if (compilationUnit == null) {
      myLock.finish();
      myLock = null;
    }
    return compilationUnit;
  }

  @Override
  public void dispose(JavaContext context, @NonNull Node compilationUnit) {
    if (context.compilationUnit != null) {
      myLock.finish();
      myLock = null;
      context.compilationUnit = null;
    }
  }

  @Nullable
  private Node parse(@NonNull JavaContext context) {
    // Should only be called from read thread
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    final PsiFile psiFile = getPsiFile(context);
    if (!(psiFile instanceof PsiJavaFile)) {
      return null;
    }
    PsiJavaFile javaFile = (PsiJavaFile)psiFile;

    try {
        return LombokPsiConverter.convert(javaFile);
    } catch (Throwable t) {
      myClient.log(t, "Failed converting PSI parse tree to Lombok for file %1$s",
                    context.file.getPath());
      return null;
    }
  }

  /**
   * Returns the {@link PsiFile} associated with a given lint {@link com.android.tools.lint.detector.api.Context}
   *
   * @param context the context to look up the file for
   * @return the corresponding {@link PsiFile}, or null
   */
  @Nullable
  public static PsiFile getPsiFile(@NonNull Context context) {
    VirtualFile file = VfsUtil.findFileByIoFile(context.file, false);
    if (file == null) {
      return null;
    }
    LintRequest request = context.getDriver().getRequest();
    Project project = ((IntellijLintRequest)request).getProject();
    if (project == null) {
      return null;
    }
    return PsiManager.getInstance(project).findFile(file);
  }

  @NonNull
  @Override
  public Location getLocation(@NonNull JavaContext context, @NonNull Node node) {
    Position position = node.getPosition();
    if (position == null) {
      myClient.log(Severity.WARNING, null, "No position data found for node %1$s", node);
      return Location.create(context.file);
    }
    return Location.create(context.file, null, position.getStart(), position.getEnd());
  }

  @NonNull
  @Override
  public Location.Handle createLocationHandle(@NonNull JavaContext context, @NonNull Node node) {
    return new LocationHandle(context.file, node);
  }

  @Nullable
  private static PsiElement getPsiElement(@NonNull Node node) {
    Object nativeNode = node.getNativeNode();
    if (nativeNode == null) {
      return null;
    }
    return (PsiElement)nativeNode;
  }

  /* Handle for creating positions cheaply and returning full fledged locations later */
  private class LocationHandle implements Location.Handle {
    private final File myFile;
    private final Node myNode;
    private Object mClientData;

    public LocationHandle(File file, Node node) {
      myFile = file;
      myNode = node;
    }

    @NonNull
    @Override
    public Location resolve() {
      Position pos = myNode.getPosition();
      if (pos == null) {
        myClient.log(Severity.WARNING, null, "No position data found for node %1$s", myNode);
        return Location.create(myFile);
      }
      return Location.create(myFile, null /*contents*/, pos.getStart(), pos.getEnd());
    }

    @Override
    public void setClientData(@Nullable Object clientData) {
      mClientData = clientData;
    }

    @Override
    @Nullable
    public Object getClientData() {
      return mClientData;
    }
  }
}
