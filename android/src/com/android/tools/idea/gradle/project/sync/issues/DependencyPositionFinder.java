/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.util.PositionInFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;

import static com.intellij.openapi.util.text.StringUtil.unquoteString;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTRING_LITERAL;

class DependencyPositionFinder {
  @NotNull
  PositionInFile findDependencyPosition(@NotNull String dependency, @NotNull VirtualFile buildFile) {
    int line = -1;
    int column = -1;

    Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(buildFile));
    if (document != null) {
      TextRange textRange = findDependency(dependency, document);
      if (textRange != null) {
        line = document.getLineNumber(textRange.getStartOffset());
        if (line > -1) {
          int lineStartOffset = document.getLineStartOffset(line);
          column = textRange.getStartOffset() - lineStartOffset;
        }
      }
    }
    return new PositionInFile(buildFile, line, column);
  }

  @Nullable
  private static TextRange findDependency(@NotNull String dependency, @NotNull Document buildFile) {
    Function<Pair<String, GroovyLexer>, TextRange> consumer = pair -> {
      GroovyLexer lexer = pair.getSecond();
      return TextRange.create(lexer.getTokenStart() + 1, lexer.getTokenEnd() - 1);
    };
    GroovyLexer lexer = new GroovyLexer();
    lexer.start(buildFile.getText());
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      if (type == mSTRING_LITERAL) {
        String text = unquoteString(lexer.getTokenText());
        if (text.startsWith(dependency)) {
          return consumer.fun(Pair.create(text, lexer));
        }
      }
      lexer.advance();
    }
    return null;
  }
}
