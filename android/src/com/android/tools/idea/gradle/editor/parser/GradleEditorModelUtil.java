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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.editor.entity.ExternalDependencyGradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.editor.parser.GradleEditorModelParseContext.*;

public class GradleEditorModelUtil {

  /**
   * Gradle allows to define project-wide properties at 'ext' namespace.
   * <p/>
   * Get more information about that at the
   * <a href="http://www.gradle.org/docs/current/dsl/org.gradle.api.Project.html#org.gradle.api.Project.extraproperties">gradle docs</a>.
   */
  public static final List<String> EXTRA_PROPERTIES_QUALIFIER = Arrays.asList("ext");

  private static final Logger LOG = Logger.getInstance(GradleEditorModelUtil.class);

  private GradleEditorModelUtil() {
  }

  @Nullable
  public static GradleEditorSourceBinding buildSourceBinding(@NotNull Assignment assignment, @NotNull Project project) {
    return buildSourceBinding(new Location(assignment.lValueLocation.file, assignment.assignmentRange), project);
  }

  @Nullable
  public static GradleEditorSourceBinding buildSourceBinding(@NotNull Location location, @NotNull Project project) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getDocument(location.file);
    if (document == null) {
      LOG.warn(String.format("Can't obtain a document for file %s for processing location '%s'",
                             location.file, location));
      return null;
    }
    RangeMarker rangeMarker = document.createRangeMarker(location.range);
    return new GradleEditorSourceBinding(project, location.file, rangeMarker);
  }

  /**
   * Utility method which allows to answer if given text is a variable with the given name, i.e. it covers cases like below:
   * <p/>
   * <table>
   *   <tr>
   *     <th>text</th>
   *     <th>variable name</th>
   *   </tr>
   *   <tr>
   *     <td>a</td>
   *     <td>a</td>
   *   </tr>
   *   <tr>
   *     <td>a</td>
   *     <td>a</td>
   *   </tr>
   *   <tr>
   *     <td>"a"</td>
   *     <td>"$a"</td>
   *   </tr>
   *   <tr>
   *     <td>"a"</td>
   *     <td>"${a}"</td>
   *   </tr>
   * </table>
   *
   * @param targetText    text to check
   * @param variableName  variable name to check
   * @return              <code>true</code> if given text is a variable with the given name; <code>false</code> otherwise
   */
  public static boolean isVariable(@NotNull String targetText, @NotNull String variableName) {
    String targetTextToUse = unquote(targetText);
    if (targetTextToUse.equals(variableName) && targetText.equals(targetTextToUse)) { // Say, var='a' - text 'a' is ok but not '"a"'
      return true;
    }
    if (targetTextToUse.equals("$" + variableName)) {
      return true;
    }
    if (targetTextToUse.equals("${" + variableName + "}")) {
      return true;
    }
    return false;
  }

  @NotNull
  public static String unquote(@NotNull String s) {
    return StringUtil.unquoteString(StringUtil.unquoteString(s, '\''), '"');
  }

  /**
   * Allows to retrieve 'interested range' for the given element.
   * <p/>
   * 'Interested range' here is {@link PsiElement#getTextRange() element's range} if it's not a string or a range which points to the
   * string content otherwise.
   *
   * @param element  element which range we're interested in
   * @return         interested range for the given element
   */
  @NotNull
  public static TextRange interestedRange(@NotNull PsiElement element) {
    String text = unquote(element.getText());
    int shift = element.getText().indexOf(text);
    TextRange result = element.getTextRange();
    if (shift > 0) {
      result = TextRange.create(result.getStartOffset() + shift, result.getStartOffset() + shift + text.length());
    }
    return result;
  }

  /**
   * {@link GradleEditorModelParseContext.Assignment#dependencies Recursively} traverses given context for the given variables
   * collecting information about {@link GradleEditorModelParseContext#getAssignments(Variable) their values}.
   * <p/>
   * Example:
   * <pre>
   *   context assignments:
   *     a = b + c
   *     b = 1
   *     c = 2
   *
   *   argument: 'variables' is 'a'
   *   result: [value: "" (because 'a' depends on 'b' and 'c' and we don't evaluate them in runtime);
   *            source bindings: ['1' ('b' value), '2' ('c' value)] ]
   *
   *   argument: 'variables' is 'b'
   *   result: [value: "1", source bindings: ['1']]
   * </pre>
   *
   * @param variables
   * @param context
   * @param filter
   * @return
   */
  @NotNull
  public static EntityInfo collectInfo(@NotNull Collection<Variable> variables,
                                       @NotNull GradleEditorModelParseContext context,
                                       @Nullable AssignmentFilter filter) {
    Set<Variable> processed = Sets.newHashSet();
    Stack<Variable> toProcess = new Stack<Variable>();
    toProcess.addAll(variables);
    List<GradleEditorSourceBinding> sourceBindings = Lists.newArrayList();
    String value = "";
    boolean skipValue = false;
    List<Assignment> assignments = Lists.newArrayList();
    while (!toProcess.isEmpty()) {
      Variable dependency = toProcess.pop();
      if (!processed.add(dependency)) {
        // Prevent cyclic dependencies.
        continue;
      }
      assignments.clear();
      assignments.addAll(context.getAssignments(dependency));
      if (dependency.qualifier.isEmpty()) {
        assignments.addAll(context.getAssignments(new Variable(dependency.name, EXTRA_PROPERTIES_QUALIFIER)));
      }
      for (Assignment a : assignments) {
        Assignment assignmentToUse = a;
        if (filter != null) {
          assignmentToUse = filter.check(a);
        }
        if (assignmentToUse == null) {
          continue;
        }
        Set<Variable> dependencies = assignmentToUse.dependencies.keySet();
        if (assignmentToUse.value != null) {
          sourceBindings.add(buildSourceBinding(assignmentToUse.value.location, context.getProject()));
          if (value.isEmpty()) {
            if (dependencies.size() > 1
                || (assignmentToUse.rValueString != null
                    && dependencies.size() == 1
                    && !isVariable(assignmentToUse.rValueString, dependencies.iterator().next().name))) {
              // An empty value and non-empty r-value string mean that there is a complex expression at the r-value place,
              // e.g. 'a = b + 1' - here 'b + 1' is a complex expression, that's why resulting data should have an empty value.
              // As an opposite, consider a case like 'a = b' here we continue by de-referencing 'b' variable and using its value
              // in case of success.
              skipValue = true;
              value = "";
            }
            if (!skipValue) {
              value = assignmentToUse.value.value;
            }
          }
          else {
            skipValue = true;
            value = "";
          }
        }
        toProcess.addAll(dependencies);
      }
    }
    return new EntityInfo(sourceBindings, value);
  }

  /**
   * Removes given entity from the underlying source file if possible.
   * <p/>
   * <b>Note:</b> this method tried to preserve code style after removing the entity.
   *
   * @param entity  an entity to remove
   * @return        <code>null</code> as an indication that given entity was successfully removed; an error message otherwise
   */
  @Nullable
  public static String removeEntity(@NotNull GradleEditorEntity entity, boolean commit) {
    GradleEditorSourceBinding location = entity.getEntityLocation();
    RangeMarker marker = location.getRangeMarker();
    if (!marker.isValid()) {
      return "source mapping is outdated for entity " + entity;
    }
    Document document = FileDocumentManager.getInstance().getDocument(location.getFile());
    if (document == null) {
      return "can't find a document for file " + location.getFile();
    }

    int startLine = document.getLineNumber(marker.getStartOffset());
    int endLine = document.getLineNumber(marker.getEndOffset());
    CharSequence text = document.getCharsSequence();
    String ws = " \t";
    int start = CharArrayUtil.shiftBackward(text, document.getLineStartOffset(startLine), marker.getStartOffset() - 1, ws);
    int end = CharArrayUtil.shiftForward(text, marker.getEndOffset(), document.getLineEndOffset(endLine), ws);
    if (start == document.getLineStartOffset(startLine) && startLine > 0) {
      start--; // Remove line feed at the end of the previous line.
    }
    else if (end == document.getLineEndOffset(endLine) && endLine < document.getLineCount() - 1) {
      end++; //Remove trailing line feed.
    }
    document.deleteString(start, end);
    if (commit) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(location.getProject());
      psiDocumentManager.commitDocument(document);
    }
    return null;
  }

  /**
   * Allows to filter {@link Assignment} objects used during processing.
   */
  public interface AssignmentFilter {

    /**
     * Adjusts given assignment for further processing if necessary.
     * <p/>
     * Example: consider a use-case when we need to derive gradle plugin value. It's defined in build.gradle like
     * <code>'classpath "{@value SdkConstants#GRADLE_PLUGIN_NAME}$version"'</code>. This interface allows to solve
     * the following problems:
     * <ul>
     *   <li>
     *     there might be other plugin definitions ({@code 'compile "group:artifact:$version"'}). {@link Assignment} objects
     *     for them hold {@code 'classpath'} as an lvalue, so, it's not possible to filter them by it. Here we provide a filter
     *     which passes only assignments which rvalue starts with <code>'{@value SdkConstants#GRADLE_PLUGIN_NAME}'</code>;
     *   </li>
     *   <li>
     *     when gradle plugin is defined like <code>'classpath "{@value SdkConstants#GRADLE_PLUGIN_NAME}1.0"'</code> we want
     *     to parse value '1.0' out of it, so, the filter receives an assignment which rvalue is
     *     <code>'classpath "{@value SdkConstants#GRADLE_PLUGIN_NAME}1.0"'</code> and returns newly built assignment object
     *     which differs from the given one in a way that it holds '1.0' as a rvalue;
     *   </li>
     * </ul>
     *
     * @param assignment  assignment to process
     * @return            <code>null</code> as an indication that given assignment should not be processed;
     *                    non-null object to use in place of this assignment for further processing
     */
    @Nullable
    Assignment check(@NotNull Assignment assignment);
  }

  /**
   * Holds information about target {@link GradleEditorEntity} or its part (e.g. particular dimension of
   * {@link ExternalDependencyGradleEditorEntity}.
   */
  public static class EntityInfo {
    @NotNull public final List<GradleEditorSourceBinding> sourceBindings;
    @NotNull public final String value;

    public EntityInfo(@NotNull List<GradleEditorSourceBinding> sourceBindings, @NotNull String value) {
      this.sourceBindings = sourceBindings;
      this.value = value;
    }
  }
}
