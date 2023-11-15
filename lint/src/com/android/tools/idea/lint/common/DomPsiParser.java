/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import static com.android.utils.PositionXmlParser.CONTENT_KEY;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourcePosition;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.PositionXmlParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import java.io.File;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Lint parser which reads in a DOM from a given file, by mapping to the underlying XML PSI structure
 */
class DomPsiParser extends XmlParser {
  private final LintIdeClient myClient;

  DomPsiParser(LintIdeClient client) {
    myClient = client;
  }

  @Override
  public int getNodeStartOffset(@NonNull XmlContext context, @NonNull Node node) {
    return getNodeStartOffset(myClient, context.file, node);
  }

  @Override
  public int getNodeStartOffset(@NonNull LintClient client, @NonNull File file, @NonNull Node node) {
    TextRange textRange = DomPsiConverter.getTextRange(node);
    return textRange.getStartOffset();
  }

  @Override
  public int getNodeEndOffset(@NonNull XmlContext context, @NonNull Node node) {
    return getNodeEndOffset(myClient, context.file, node);
  }

  @Override
  public int getNodeEndOffset(@NonNull LintClient client, @NonNull File file, @NonNull Node node) {
    TextRange textRange = DomPsiConverter.getTextRange(node);
    return textRange.getEndOffset();
  }

  @Nullable
  @Override
  public Node findNodeAt(@NonNull XmlContext context, int offset) {
    return DomPsiConverter.findNodeAt(context.document, offset);
  }

  @Nullable
  @Override
  public Node findNodeAt(@NonNull Node node, int offset) {
    return DomPsiConverter.findNodeAt(node, offset);
  }

  @Nullable
  @Override
  public Document parseXml(@NonNull final XmlContext context) {
    // Should only be called from read thread
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    return parse(context);
  }

  @Nullable
  private Document parse(XmlContext context) {
    final PsiFile psiFile = LintIdeUtilsKt.getPsiFile(context);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    XmlFile xmlFile = (XmlFile)psiFile;

    try {
      return DomPsiConverter.convert(xmlFile);
    }
    catch (Throwable t) {
      myClient.log(t, "Failed converting PSI parse tree to DOM for file %1$s",
                   context.file.getPath());
      return null;
    }
  }

  @Override
  @Nullable
  public Document parseXml(@NonNull File file) {
    // Should only be called from read thread
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    Project project = myClient.getIdeProject();
    if (project.isDisposed()) {
      return null;
    }

    VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, false);
    if (virtualFile == null) {
      return null;
    }

    PsiFile psiFile = LintIdeUtilsKt.getPsiFileSafely(virtualFile, project);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    XmlFile xmlFile = (XmlFile)psiFile;

    try {
      return DomPsiConverter.convert(xmlFile);
    }
    catch (Throwable t) {
      myClient.log(t, null);
      return null;
    }
  }

  @Nullable
  @Override
  public Document parseXml(@NonNull CharSequence xml, @Nullable File file) {
    // Should only be called from read thread
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    if (file != null) {
      return parseXml(file);
    }
    try {
      return PositionXmlParser.parse(xml.toString());
    }
    catch (Exception ignore) {
      return null;
    }
  }

  @NonNull
  @Override
  public Location getLocation(@NonNull XmlContext context, @NonNull Node node) {
    return getLocation(context.file, node).withSource(node);
  }

  @NonNull
  @Override
  public Location getLocation(@NonNull LintClient client,
                              @NonNull File file, @NonNull Node node) {
    return getLocation(file, node);
  }

  @NonNull
  @Override
  public Location getLocation(@NonNull File file, @NonNull Node node) {
    TextRange textRange = DomPsiConverter.getTextRange(node);

    if (node.getNodeType() == Node.TEXT_NODE) {
      // Match the PositionXmlParser which skips whitespace prefixes and suffixes such
      // that the IDE and the build system offer consistent positioning.
      textRange = DomPsiConverter.trim(node, textRange);
    }

    Position start = new LintXmlPosition(node, textRange.getStartOffset());
    Position end = new LintXmlPosition(node, textRange.getEndOffset());
    return Location.create(file, start, end).withSource(node);
  }

  @NonNull
  @Override
  public Location getLocation(@NonNull XmlContext context, @NonNull Node node, int startDelta, int endDelta) {
    TextRange textRange = DomPsiConverter.getTextRange(node);
    Position start = new LintXmlPosition(node, textRange.getStartOffset() + startDelta);
    Position end = new LintXmlPosition(node, textRange.getStartOffset() + endDelta);
    return Location.create(context.file, start, end).withSource(node);
  }

  @NonNull
  @Override
  public Location getNameLocation(@NonNull XmlContext context, @NonNull Node node) {
    return getNameLocation(myClient, context.file, node);
  }

  @NonNull
  @Override
  public Location getNameLocation(@NonNull LintClient client, @NonNull File file, @NonNull Node node) {
    TextRange textRange = DomPsiConverter.getTextNameRange(node);
    Position start = new LintXmlPosition(node, textRange.getStartOffset());
    Position end = new LintXmlPosition(node, textRange.getEndOffset());
    return Location.create(file, start, end).withSource(node);
  }

  @NonNull
  @Override
  public Location getValueLocation(@NonNull XmlContext context, @NonNull Attr node) {
    return getValueLocation(myClient, context.file, node);
  }

  @NonNull
  @Override
  public Location getValueLocation(@NonNull LintClient client, @NonNull File file, @NonNull Attr node) {
    TextRange textRange = DomPsiConverter.getTextValueRange(node);
    Position start = new LintXmlPosition(node, textRange.getStartOffset());
    Position end = new LintXmlPosition(node, textRange.getEndOffset());
    return Location.create(file, start, end).withSource(node);
  }

  @NonNull
  @Override
  public Location.Handle createLocationHandle(@NonNull XmlContext context, @NonNull Node node) {
    return new LocationHandle(context.file, node);
  }

  private static class LintXmlPosition extends LintIdePosition {
    private @NonNull final Node myNode;

    LintXmlPosition(@NonNull Node node, int offset) {
      super(offset);
      myNode = node;
    }

    @Override
    protected void initializeLineColumn() {
      XmlElement element = DomPsiConverter.getPsiElement(myNode);
      if (element != null) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        PsiFile file = element.getContainingFile();
        if (file != null && file.isValid()) {
          String contents = file.getText();
          initializeFromText(contents);
        }
      } else {
        // Not an IDE XML document; probably read from merged manifest
        Document document = myNode.getOwnerDocument();
        String text = (String)document.getUserData(CONTENT_KEY);
        if (text != null) {
          initializeFromText(text);
        } else {
          File file = (File)document.getUserData(File.class.getName());
          if (file != null) {
            String contents = FilesKt.readText(file, Charsets.UTF_8);
            initializeFromText(contents);
          }
          else {
            PsiFile psiFile = (PsiFile)document.getUserData(PsiFile.class.getName());
            if (psiFile != null) {
              String contents = psiFile.getText();
              initializeFromText(contents);
            }
          }
        }
      }
    }
  }

  private static class LocationHandle implements Location.Handle {
    private final File myFile;
    private final Node myNode;
    private Object myClientData;

    LocationHandle(File file, Node node) {
      myFile = file;
      myNode = node;
    }

    @NonNull
    @Override
    public Location resolve() {
      if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ApplicationManager.getApplication().runReadAction((Computable<Location>)this::resolve);
      }
      TextRange textRange = DomPsiConverter.getTextRange(myNode);
      Position start = new LintXmlPosition(myNode, textRange.getStartOffset());
      Position end = new LintXmlPosition(myNode, textRange.getEndOffset());
      return Location.create(myFile, start, end).withSource(myNode);
    }

    @Override
    public void setClientData(@Nullable Object clientData) {
      myClientData = clientData;
    }

    @Override
    @Nullable
    public Object getClientData() {
      return myClientData;
    }
  }
}
