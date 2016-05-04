/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.*;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.GradleFileMergers.CONFIGURATION_ORDERING;
import static com.android.tools.idea.templates.GradleFileMergers.DEPENDENCIES;

/**
 * Simplified gradle.build merger designed to be used while instantiating android project templates.
 * This merger will not modify the Psi but simply work on the file content.
 * What this merger does:
 * <ul>
 *   <li>Overrides single property values</li>
 *   <li>Makes the union of list property values</li>
 *   <li>Makes the union of dependencies</li>
 *   <li>Attempts to convert dynamic dependencies</li>
 * </ul>
 */
public class GradleFileSimpleMerger {
  public static String mergeGradleFiles(@NotNull String source,
                                        @NotNull String dest,
                                        @Nullable Project project,
                                        @Nullable String supportLibVersionFilter) {
    SimpleGradleBuildFileParser templateParser = new SimpleGradleBuildFileParser(source);
    Ast template = templateParser.parse();
    assert template != null;
    SimpleGradleBuildFileParser existingParser = new SimpleGradleBuildFileParser(dest);
    Ast existing = existingParser.parse();

    PrintContext printContext = new PrintContext();
    if (existing != null) {
      MergeContext mergeContext = new MergeContext(project, supportLibVersionFilter);
      existing.merge(mergeContext, template);

      existing.print(printContext);
    }
    else {
      // If existing file doesn't parse, just return template file
      template.print(printContext);
    }

    return printContext.toString();
  }

  private static Logger getLogger() {
    return Logger.getInstance(GradleFileSimpleMerger.class);
  }

  /**
   * Simple parser for gradle.build files.
   * The file content is parsed into an AST that is recognizing: script blocks and property assignments.
   * All other constructs are kept as large chunks that cannot be merged {@see UnknownAstNode}.
   */
  private static class SimpleGradleBuildFileParser {
    private GroovyLexer myLexer = new GroovyLexer();
    private IElementType myType = null;

    public SimpleGradleBuildFileParser(@NotNull String source) {
      myLexer = new GroovyLexer();
      myLexer.start(source);
      myType = myLexer.getTokenType();
    }

    public void next() {
      nextKeepWhiteSpace();
      while (myType != null && myType == TokenType.WHITE_SPACE) {
        nextKeepWhiteSpace();
      }
    }

    public void nextKeepWhiteSpace() {
      if (myType != null) {
        myLexer.advance();
        myType = myLexer.getTokenType();
      }
    }

    public void skipNewLine() {
      if (myType == GroovyTokenTypes.mNLS) {
        next();
      }
    }

    @Nullable
    public Ast parse() {
      if (myType == null) {
        return null;
      }
      Ast result;
      if (myType == GroovyTokenTypes.mIDENT) {
        result = parseBlockOrAssignment();
      }
      else {
        result = parseUnknown();
      }
      if (myType != GroovyTokenTypes.mRCURLY) {
        // If this is not the end of a script block, parse the next statement in the same block:
        result.myNext = parse();
      }
      return result;
    }

    private Ast parseBlockOrAssignment() {
      assert myType == GroovyTokenTypes.mIDENT;
      LexerPosition pos = myLexer.getCurrentPosition();
      AstNode node = parseIdentifier();
      if (node == null) {
        return restoreAndParseUnknown(pos);
      }
      if (myType == GroovyTokenTypes.mLCURLY) {
        // This must be a script block
        next();
        node.myParam = parse();
        if (myType != GroovyTokenTypes.mRCURLY) {
          return restoreAndParseUnknown(pos);
        }
        next();
        skipNewLine();
      }
      else {
        // Assume this is a property assignment
        node.myParam = parseValue();
      }
      return node;
    }

    private AstNode parseIdentifier() {
      assert myType == GroovyTokenTypes.mIDENT;
      String identifier = myLexer.getTokenText();
      next();
      while (myType == GroovyTokenTypes.mDOT) {
        next();
        if (myType != GroovyTokenTypes.mIDENT) {
          return null;
        }
        identifier += ".";
        identifier += myLexer.getTokenText();
        next();
      }
      return new AstNode(identifier);
    }

    private Ast parseValue() {
      if (myType == GroovyTokenTypes.mASSIGN) {
        return parseValueList();
      }
      else if (myType == GroovyTokenTypes.mSTRING_LITERAL ||
               myType == GroovyTokenTypes.mGSTRING_LITERAL ||
               myType == GroovyTokenTypes.mNUM_INT ||
               myType == GroovyTokenTypes.mNUM_BIG_INT ||
               myType == GroovyTokenTypes.mNUM_BIG_DECIMAL ||
               myType == GroovyTokenTypes.mNUM_FLOAT ||
               myType == GroovyTokenTypes.mNUM_DOUBLE ||
               myType == GroovyTokenTypes.kFALSE ||
               myType == GroovyTokenTypes.kTRUE) {
        String value = myLexer.getTokenText();
        next();
        skipNewLine();
        return new ValueAst(value);
      }
      return parseUnknown();
    }

    private Ast parseValueList() {
      LexerPosition pos = myLexer.getCurrentPosition();
      assert myType == GroovyTokenTypes.mASSIGN;
      next();
      if (myType != GroovyTokenTypes.mLBRACK) {
        return restoreAndParseUnknown(pos);
      }
      next();
      List<String> values = Lists.newArrayListWithCapacity(10);
      if (!findStringLiteral(values)) {
        return restoreAndParseUnknown(pos);
      }
      while (myType == GroovyTokenTypes.mCOMMA) {
        next();
        if (!findStringLiteral(values)) {
          return restoreAndParseUnknown(pos);
        }
      }
      if (myType != GroovyTokenTypes.mRBRACK) {
        return restoreAndParseUnknown(pos);
      }
      next();
      skipNewLine();
      return new ValueListAst(values);
    }

    private boolean findStringLiteral(List<String> literals) {
      if (myType != GroovyTokenTypes.mSTRING_LITERAL) {
        return false;
      }
      literals.add(myLexer.getTokenText());
      next();
      return true;
    }

    private Ast restoreAndParseUnknown(LexerPosition pos) {
      myLexer.restore(pos);
      return parseUnknown();
    }

    private Ast parseUnknown() {
      int level = 0;
      StringBuilder builder = new StringBuilder();
      //noinspection WhileLoopSpinsOnField
      while (myType != null) {
        if (myType == GroovyTokenTypes.mLCURLY) {
          level++;
        }
        else if (myType == GroovyTokenTypes.mRCURLY) {
          level--;
          if (level < 0) {
            break;
          }
        }
        else if (level == 0 && myType == GroovyTokenTypes.mNLS) {
          next();
          break;
        }
        else if (level == 0 && myType == GroovyTokenTypes.mSL_COMMENT) {
          builder.append(myLexer.getTokenSequence());
          next();
          break;
        }
        builder.append(myLexer.getTokenSequence());
        nextKeepWhiteSpace();
      }
      return new UnknownAstNode(builder.toString());
    }
  }

  /**
   * A node in an Abstract Syntax Tree for a gradle build file.
   */
  private static abstract class Ast {
    @Nullable
    protected String myId = null;  // The ID of a script block or a property name
    @Nullable
    protected Ast myNext = null;   // The next statement in the statement list

    /**
     * Print the AST into a gradle build file.
     */
    public abstract void print(@NotNull PrintContext context);

    /**
     * Merge the AST with another AST.
     */
    public abstract void merge(@NotNull MergeContext context, @NotNull Ast other);

    /**
     * Return true if this AST should be encapsulated in curly brackets in the build file.
     */
    protected boolean isComplex() {
      return myNext != null;
    }

    /**
     * Find an AST node with the specified ID from the list of ASTs represented by the next chain.
     * Return null if no such node is found.
     */
    @Nullable
    private Ast find(@NotNull String id) {
      Ast node = this;
      while (node != null && !id.equals(node.myId)) {
        node = node.myNext;
      }
      return node;
    }

    /**
     * Remove an AST node from the list of ASTs represented by the next chain.
     * Return the head of the resulting next chain (which may be different if the node is found at the head of the list).
     */
    @Nullable
    Ast remove(@NotNull Ast toRemove) {
      Ast first = this;
      Ast prev = null;
      Ast node = this;
      while (node != null && toRemove != node) {
        prev = node;
        node = node.myNext;
      }
      if (toRemove == node) {
        first = remove(toRemove, first, prev);
      }
      return first;
    }

    /**
     * Remove an AST from a list of ASTS when we know the first and prev node.
     * Return the head of the resulting next chain.
     */
    Ast remove(@NotNull Ast toRemove, @NotNull Ast first, @Nullable Ast prev) {
      Ast newFirst = first;
      if (prev == null) {
        newFirst = toRemove.myNext;
      }
      else {
        prev.myNext = toRemove.myNext;
      }
      toRemove.myNext = null;
      return newFirst;
    }

    /**
     * Return the last node in the next chain.
     */
    Ast findLast() {
      Ast node = this;
      while (node.myNext != null) {
        node = node.myNext;
      }
      return node;
    }
  }

  /**
   * An AST node representing a simple possibly nested script block or a property assignment.
   * <code>
   *   script.block.example {
   *     property1.name 'string-value'
   *   }
   * </code>
   */
  private static class AstNode extends Ast {
    @Nullable
    protected Ast myParam;  // list of statements

    public AstNode(@NotNull String id) {
      myId = id;
    }

    @Override
    public void print(@NotNull PrintContext context) {
      assert myId != null;
      context.append(myId).append(" ");
      if (myParam != null) {
        if (myParam.isComplex()) {
          context.increaseIndent();
          context.append("{\n");
          myParam.print(context);
          context.decreaseIndent();
          context.append("}\n");
        }
        else {
          myParam.print(context);
        }
      }
      if (myNext != null) {
        context.appendNewLineIfNoIndent();
        myNext.print(context);
      }
    }

    @Override
    public void merge(@NotNull MergeContext context, @NotNull Ast other) {
      assert myId != null;

      Ast first = other;
      Ast similar = other.find(myId);
      if (similar instanceof AstNode) {
        first = other.remove(similar);
        AstNode node = (AstNode) similar;
        if (myParam == null) {
          myParam = node.myParam;
        }
        else if (myId.equals(DEPENDENCIES)) {
          mergeDependencies(context, node);
        }
        else if (node.myParam != null) {
          myParam.merge(context, node.myParam);
        }
      }
      else if (similar != null) {
        getLogger().warn("Cannot merge AstNode with a non AstNode");
      }
      if (myNext != null && first != null) {
        myNext.merge(context, first);
      }
      else if (first != null) {
        myNext = first;
      }
    }

    @Override
    public boolean isComplex() {
      return myNext != null || myParam != null;
    }

    private void mergeDependencies(@NotNull MergeContext context, @NotNull AstNode other) {
      Map<String, Multimap<String, GradleCoordinate>> dependencies = Maps.newHashMap();
      List<Ast> unparseableDependencies = Lists.newArrayListWithCapacity(10);
      pullDependenciesIntoMap(dependencies, null);
      other.pullDependenciesIntoMap(dependencies, unparseableDependencies);
      RepositoryUrlManager urlManager = RepositoryUrlManager.get();
      ImmutableList<String> configurations = CONFIGURATION_ORDERING.immutableSortedCopy(dependencies.keySet());

      Ast prev = null;
      for (String configuration : configurations) {
        List<GradleCoordinate> resolved = urlManager.resolveDynamicDependencies(dependencies.get(configuration), context.getFilter());

        // Add the resolved dependencies:
        prev = myParam != null ? myParam.findLast() : null;
        for (GradleCoordinate coordinate : resolved) {
          AstNode compile = new AstNode(configuration);
          compile.myParam = new ValueAst("'" + coordinate + "'");
          if (prev == null) {
            myParam = compile;
          }
          else {
            prev.myNext = compile;
          }
          prev = compile;
        }
      }

      // Add the dependencies we could not parse (steal the AST nodes from the other parse tree):
      for (Ast node : unparseableDependencies) {
        if (prev == null) {
          myParam = node;
        }
        else {
          prev.myNext = node;
        }
        prev = node;
        node.myNext = null;
      }
    }

    private void pullDependenciesIntoMap(@NotNull Map<String, Multimap<String, GradleCoordinate>> dependencies,
                                         @Nullable List<Ast> unparseableDependencies) {
      Ast node = myParam;
      Ast prev = null;
      while (node != null) {
        assert myParam != null;
        boolean parsed = false;
        if (node instanceof AstNode) {
          final String configuration = node.myId;
          AstNode compile = (AstNode) node;
          if (compile.myParam instanceof ValueAst) {
            ValueAst value = (ValueAst)compile.myParam;
            String coordinateText = StringUtil.unquoteString(value.myValue);
            GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateText);
            if (coordinate != null) {
              parsed = true;
              Multimap<String, GradleCoordinate> map = dependencies.get(configuration);
              if (map == null) {
                map = LinkedHashMultimap.create();
                dependencies.put(configuration, map);
              }

              if (!map.get(coordinate.getId()).contains(coordinate)) {
                map.put(coordinate.getId(), coordinate);

                // Delete the current node:
                Ast toDelete = node;
                node = node.myNext;
                myParam = remove(toDelete, myParam, prev);
                continue;
              }
            }
          }
        }
        if (!parsed && unparseableDependencies != null) {
          unparseableDependencies.add(node);
        }
        prev = node;
        node = node.myNext;
      }
    }
  }

  /**
   * An AST representing a single value in a property assignment.
   */
  private static class ValueAst extends Ast {
    private String myValue;

    public ValueAst(@NotNull String value) {
      myValue = value;
    }

    @Override
    public void print(@NotNull PrintContext context) {
      context.append(myValue).append("\n");
    }

    @Override
    public void merge(@NotNull MergeContext context, @NotNull Ast other) {
      if (other instanceof ValueAst) {
        myValue = ((ValueAst) other).myValue;
      }
    }
  }

  /**
   * An AST representing a list of string values in a property assignment.
   */
  private static class ValueListAst extends Ast {
    private Set<String> myValues;

    public ValueListAst(@NotNull List<String> values) {
      myValues = Sets.newLinkedHashSetWithExpectedSize(values.size() + 5);
      myValues.addAll(values);
    }

    @Override
    public void print(@NotNull PrintContext context) {
      context.append("= [");
      String separator = "";
      for (String value : myValues) {
        context.append(separator);
        context.append(value);
        separator = ", ";
      }
      context.append("]\n");
    }

    @Override
    public void merge(@NotNull MergeContext context, @NotNull Ast other) {
      if (other instanceof ValueListAst) {
        ValueListAst list = (ValueListAst) other;
        myValues.addAll(list.myValues);
      }
      else {
        getLogger().warn("Cannot merge value list with a different construct");
      }
    }
  }

  /**
   * An AST node representing a part of a gradle build file where we do not understand the details.
   * We simply keep the raw text from the build file and print it as it was.
   */
  private static class UnknownAstNode extends Ast {
    @NotNull
    private String myText;

    public UnknownAstNode(@NotNull String text) {
      myText = text;
    }

    @Override
    public void print(@NotNull PrintContext context) {
      // The only 2 things that can be in front of this unknown Groovy construction is
      // white space or an identifier with a single space. Remove the space after the
      // previous identifier if possible.
      if (!myText.isEmpty() && !Character.isJavaIdentifierPart(myText.charAt(0))) {
        context.removePreviousSingleSpaceChar();
      }

      context.append(myText).append("\n");
      if (myNext != null) {
        context.appendNewLineIfNoIndent();
        myNext.print(context);
      }
    }

    @Override
    public void merge(@NotNull MergeContext context, @NotNull Ast other) {
      if (myNext != null) {
        myNext.merge(context, other);
      }
      else {
        myNext = other;
      }
    }

    @Override
    protected boolean isComplex() {
      return myNext != null;
    }
  }

  /**
   * Context for printing an AST into a gradle.build file.
   * Keeping track of indents.
   */
  private static class PrintContext {
    private int myIndent;
    private StringBuilder myBuilder;

    public PrintContext() {
      myBuilder = new StringBuilder();
      myIndent = 0;
    }

    public PrintContext append(@NotNull String value) {
      if (atStartOfLine() && !value.startsWith("\n")) {
        appendIndent();
      }
      myBuilder.append(value);
      return this;
    }

    void increaseIndent() {
      myIndent++;
    }

    void decreaseIndent() {
      myIndent--;
    }

    void removePreviousSingleSpaceChar() {
      int len = myBuilder.length();
      if (len > 1 && myBuilder.charAt(len - 1) == ' ' && Character.isJavaIdentifierPart(len - 2)) {
        myBuilder.setLength(myBuilder.length() - 1);
      }
    }

    void appendNewLineIfNoIndent() {
      if (myIndent == 0) {
        myBuilder.append("\n");
      }
    }

    private boolean atStartOfLine() {
      if (myBuilder.length() == 0) {
        return true;
      }
      return myBuilder.charAt(myBuilder.length() - 1) == '\n';
    }

    private void appendIndent() {
      for (int index = 0; index < myIndent; index++) {
        myBuilder.append("    ");
      }
    }

    @Override
    public String toString() {
      return myBuilder.toString();
    }
  }

  /**
   * Context for merging AST nodes.
   * The context is only used for merging dependencies.
   */
  private static class MergeContext {
    private final Project myProject;
    private final String myFilter;

    public MergeContext(@Nullable Project project, @Nullable String supportLibVersionFilter) {
      myProject = project;
      myFilter = supportLibVersionFilter;
    }

    String getFilter() {
      return myFilter;
    }

    Project getProject() {
      return myProject;
    }
  }
}
