/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.parser;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntityGroup;
import com.android.tools.idea.gradle.editor.entity.VersionGradleEditorEntity;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.editor.parser.GradleEditorModelParseContext.*;

/**
 * Entry point for functionality of {@link #parse(VirtualFile, Project) building} {@link GradleEditorEntity} objects from
 * the target <code>build.gradle</code> file.
 */
public class GradleEditorModelParserFacade {

  private static final List<GradleEditorModelParser> ourParsers = Lists.<GradleEditorModelParser>newArrayList(
    new GradleEditorModelParserV1()
  );

  private static final Logger LOG = Logger.getInstance(GradleEditorModelParserFacade.class);

  @NotNull
  public List<GradleEditorEntityGroup> parse(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiFile psiFile = psiManager.findFile(virtualFile);
    if (psiFile == null) {
      LOG.warn(String.format("Can't build PSI for the gradle config file '%s'", virtualFile.getCanonicalPath()));
      return Collections.emptyList();
    }
    GradleEditorModelParseContext context = new GradleEditorModelParseContext(virtualFile, project);
    // This a two-steps process:
    //   1. Gradle config's PSI is parsed and the context is filled by assignments data;
    //   2. That data is given for further processing and actual building of model entities;
    fillContext(context, psiFile);
    for (VirtualFile dir = virtualFile.getParent(); dir != null; dir = dir.getParent()) {
      File settingsIoFile = new File(dir.getCanonicalPath(), GradleConstants.SETTINGS_FILE_NAME);
      if (!settingsIoFile.isFile()) {
        // Go up if there is no settings.gradle file in the current dir
        continue;
      }
      if (isParentProject(settingsIoFile, virtualFile)) {
        File parentIoFile = new File(dir.getCanonicalPath(), GradleConstants.DEFAULT_SCRIPT_NAME);
        VirtualFile parentVFile = LocalFileSystem.getInstance().findFileByIoFile(parentIoFile);
        if (parentVFile != null) {
          parentVFile.refresh(false, false);
          PsiFile parentPsiFile = psiManager.findFile(parentVFile);
          if (parentPsiFile != null) {
            context.onChangeFile(parentVFile);
            fillContext(context, parentPsiFile);
          }
        }
      }
      break;
    }
    return buildEntities(context);
  }

  private static boolean isParentProject(@NotNull File settingsFile, @NotNull VirtualFile targetConfigFile) {
    try {
      ImmutableList<String> lines = Files.asCharSource(settingsFile, Charset.forName("UTF-8")).readLines();
      String startLineMarker = "include ";
      for (String line : lines) {
        if (!line.startsWith(startLineMarker)) {
          continue;
        }
        List<String> subProjects = Lists.newArrayList();
        for (String s : Splitter.on(",").trimResults().omitEmptyStrings().split(line.substring(startLineMarker.length()))) {
          // Sub-projects are defined as strings with leading colon, e.g. include ':app'.
          s = GradleEditorModelUtil.unquote(s);
          if (s.startsWith(":")) {
            s = s.substring(1);
          }
          subProjects.add(s);
        }
        List<String> dirs = Lists.newArrayList();
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        VirtualFile rootDir = fileSystem.refreshAndFindFileByIoFile(settingsFile.getParentFile());
        if (rootDir == null) {
          return false;
        }
        for (VirtualFile dir = targetConfigFile.getParent(); dir != null; dir = dir.getParent()) {
          if (rootDir.equals(dir)) {
            break;
          }
          dirs.add(dir.getName());
        }
        Collections.reverse(dirs);
        int i = 0;
        for (String subProject : subProjects) {
          if (i >= dirs.size() || !subProject.equals(dirs.get(i++))) {
            return false;
          }
        }
        return true;
      }
    }
    catch (IOException e) {
      LOG.warn("Unexpected exception occurred on attempt to read contents of file " + settingsFile.getAbsolutePath());
    }
    return false;
  }

  /**
   * Processes given PSI file and fills given context
   * by {@link GradleEditorModelParseContext#getAssignments(Variable) corresponding assignments}.
   *
   * @param context  context to fill
   * @param psiFile  psi file to parse
   */
  private static void fillContext(@NotNull final GradleEditorModelParseContext context, @NotNull PsiFile psiFile) {
    psiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
        Pair<String,TextRange> pair = GradleEditorValueExtractor.extractMethodName(methodCallExpression);
        GrClosableBlock[] closureArguments = methodCallExpression.getClosureArguments();
        if (pair == null || closureArguments.length > 1) {
          super.visitMethodCallExpression(methodCallExpression);
          return;
        }
        if (closureArguments.length == 0) {
          if (methodCallExpression.getArgumentList().getAllArguments().length == 0) {
            // This is a no-args method, so, we just register it for cases like 'mavenCentral()' or 'jcenter()'.
            context.addCachedValue(NO_ARGS_METHOD_ASSIGNMENT_VALUE,
                                   TextRange.create(pair.second.getEndOffset(), methodCallExpression.getTextRange().getEndOffset()));
            context.registerAssignmentFromCachedData(pair.first, pair.second, methodCallExpression);
          }
          return;
        }

        context.onMethodEnter(pair.getFirst());
        try {
          super.visitClosure(closureArguments[0]);
        }
        finally {
          context.onMethodExit();
        }
      }

      @Override
      public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
        Pair<String,TextRange> methodName = GradleEditorValueExtractor.extractMethodName(applicationStatement);
        if (methodName == null) {
          return;
        }
        GroovyPsiElement[] allArguments = applicationStatement.getArgumentList().getAllArguments();
        if (allArguments.length == 1) {
          context.resetCaches();
          extractValueOrVariable(allArguments[0], context);
          context.registerAssignmentFromCachedData(methodName.getFirst(), methodName.getSecond(), applicationStatement.getArgumentList());
        }
      }

      @Override
      public void visitAssignmentExpression(GrAssignmentExpression expression) {
        // General idea is to try to extract variable from the given expression and, in case of success, try to extract rvalue and
        // register corresponding assignment with them.
        context.resetCaches();
        extractValueOrVariable(expression.getLValue(), context);
        Multimap<Variable, Location> vars = context.getCachedVariables();
        if (vars.size() != 1) {
          context.resetCaches();
          return;
        }
        Map.Entry<Variable, Location> entry = vars.entries().iterator().next();
        Variable lVariable = entry.getKey();
        Location lVariableLocation = entry.getValue();
        context.resetCaches();

        GrExpression rValue = expression.getRValue();
        if (rValue == null) {
          return;
        }
        extractValueOrVariable(rValue, context);
        if (context.getCachedValues().size() > 1) {
          Value value = new Value("", new Location(context.getCurrentFile(), GradleEditorModelUtil.interestedRange(rValue)));
          context.setCachedValues(Collections.singletonList(value));
        }
        context.registerAssignmentFromCachedData(lVariable, lVariableLocation, rValue);
        context.resetCaches();
      }

      @Override
      public void visitVariable(GrVariable variable) {
        TextRange nameRange = null;
        boolean lookForInitializer = false;
        ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.findSingle(GroovyLanguage.INSTANCE);
        for (PsiElement e = variable.getFirstChild(); e != null; e = e.getNextSibling()) {
          ASTNode node = e.getNode();
          if (node == null) {
            continue;
          }
          if (!lookForInitializer) {
            if (node.getElementType() == GroovyTokenTypes.mIDENT) {
              nameRange = e.getTextRange();
            }
            else if (node.getElementType() == GroovyTokenTypes.mASSIGN) {
              if (nameRange == null) {
                return;
              }
              lookForInitializer = true;
            }
            continue;
          }
          if (node.getElementType() == GroovyTokenTypes.mNLS || node.getElementType() == GroovyTokenTypes.mSEMI) {
            break;
          }
          if (parserDefinition.getWhitespaceTokens().contains(node.getElementType())) {
            continue;
          }
          extractValueOrVariable(e, context);
          if (context.getCachedValues().size() > 1) {
            Value value = new Value("", new Location(context.getCurrentFile(), GradleEditorModelUtil.interestedRange(e)));
            context.setCachedValues(Collections.singletonList(value));
          }
          if (context.registerAssignmentFromCachedData(variable.getName(), nameRange, e)) {
            return;
          }
        }
      }
    }));
  }

    /**
   * @see GradleEditorValueExtractor#extractValueOrVariable(PsiElement)
   */
  private static void extractValueOrVariable(@NotNull PsiElement element, @NotNull final GradleEditorModelParseContext context) {
    new GradleEditorValueExtractor(context).extractValueOrVariable(element);
  }

  @NotNull
  private static List<GradleEditorEntityGroup> buildEntities(@NotNull GradleEditorModelParseContext context) {
    VersionGradleEditorEntity entity = GradleEditorModelParserV1.buildGradlePluginVersion(context);
    GradleCoordinate androidGradlePluginVersion = null;
    if (entity != null) {
      String currentVersion = entity.getCurrentValue();
      if (!currentVersion.isEmpty()) {
        androidGradlePluginVersion = GradleCoordinate.parseVersionOnly(currentVersion);
      }
    }
    if (androidGradlePluginVersion == null) {
      androidGradlePluginVersion = GradleCoordinate.parseVersionOnly("0");
    }
    Comparator<GradleCoordinate> c = GradleCoordinate.COMPARE_PLUS_HIGHER;
    for (GradleEditorModelParser parser : ourParsers) {
      if (c.compare(androidGradlePluginVersion, parser.getMinSupportedAndroidGradlePluginVersion()) >= 0
          && c.compare(androidGradlePluginVersion, parser.getMaxSupportedAndroidGradlePluginVersion()) < 0) {
        return parser.buildEntities(context);
      }
    }
    return Collections.emptyList();
  }
}
