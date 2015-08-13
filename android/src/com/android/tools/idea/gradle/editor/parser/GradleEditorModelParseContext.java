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

import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.google.common.collect.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class which stores intermediate information during building {@link GradleEditorEntity} from the <code>build.gradle</code>.
 */
public class GradleEditorModelParseContext {

  /**
   * Used as an {@link Assignment#value} for no-args methods like <code>'jcenter()'</code>.
   */
  public static final String NO_ARGS_METHOD_ASSIGNMENT_VALUE = "()";

  private final Multimap<Variable, Assignment> myAssignmentsByVariable = HashMultimap.create();
  private final Multimap<List<String>, Assignment> myAssignmentsByCodeStructure = HashMultimap.create();

  /**
   * Holds information about nested named code blocks, e.g. it's empty initially, holds 'buildscript' when we start parsing that section,
   * 'dependencies' (for the nested block) etc.
   */
  private final List<String> myCodeStructure = Lists.newArrayList();

  /**
   * There is a possible case that a variable is defined like <code>'a.b.c = 1'</code>. Here variable name is 'c' and it's qualifier
   * is <code>'a.b'</code>. Parsing processing is iterative and we first parse <code>'a'</code> qualifier part,
   * then <code>'b'</code> qualifier part and finally <code>'c'</code> variable.
   * <p/>
   * Current list is assumed to be ['a', 'b'] (at that order) when <code>'c'</code> is being parsed.
   */
  private final List<String> myCachedVariableQualifier = Lists.newArrayList();
  private final Multimap<Variable, Location> myCachedVariables = HashMultimap.create();
  private final List<Value> myCachedValues = Lists.newArrayList();

  @NotNull private final VirtualFile myTargetFile;
  @NotNull private final Project myProject;
  @NotNull private VirtualFile myCurrentFile;

  public GradleEditorModelParseContext(@NotNull VirtualFile targetFile, @NotNull Project project) {
    myTargetFile = targetFile;
    myCurrentFile = targetFile;
    myProject = project;
  }

  /**
   * @return  virtual file for the target <code>build.gradle</code> being parsed
   */
  @NotNull
  public VirtualFile getTargetFile() {
    return myTargetFile;
  }

  /**
   * There is a possible case that we parse more than one <code>build.gradle</code> file - e.g. consider a multi-project. We parse
   * either parent or child <code>build.gradle</code> files then (e.g. parent file might declare external dependencies at
   * <code>'subprojects'</code> section which effectively modify child's configuration).
   * <p/>
   * Target file is exposed via {@link #getTargetFile()} and current file being parsed is exposed by this method.
   *
   * @return    current gradle config file being parsed
   */
  @NotNull
  public VirtualFile getCurrentFile() {
    return myCurrentFile;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * There is a notion of 'cached values' hold within the context - their values are accumulated during complex expressions
   * parsing and are later flushed during {@link #registerAssignmentFromCachedData(Variable, Location, PsiElement)}.
   * <p/>
   * This property exposes values set previously via {@link #setCachedValues(List)} or {@link #addCachedValue(String, TextRange)}.
   *
   * @return   cached values registered within the current context
   */
  @NotNull
  public List<Value> getCachedValues() {
    return myCachedValues;
  }

  /**
   * Registers a cached value within the current context.
   *
   * @param value  a value's text to register within the current context
   * @param range  target value's range
   * @see #getCachedValues()
   */
  public void addCachedValue(@NotNull String value, @NotNull TextRange range) {
    addCachedValue(new Value(value, new Location(myCurrentFile, range)));
  }

  /**
   * @param value   a value to register within the current context
   * @see #getCachedValues()
   */
  public void addCachedValue(@Nullable Value value) {
    myCachedValues.add(value);
  }

  /**
   * @param values  values to use within the current context
   * @see #getCachedValues()
   */
  public void setCachedValues(@NotNull List<Value> values) {
    myCachedValues.clear();
    myCachedValues.addAll(values);
  }

  /**
   * @return    similar to {@link #getCachedValues()} but not for variables but values registered within the curent context
   * @see #getCachedValues()
   */
  @NotNull
  public Multimap<Variable, Location> getCachedVariables() {
    return myCachedVariables;
  }

  /**
   * @param variableName  name of the variable to register within the current context
   *                      ({@link #myCachedVariableQualifier cached qualifier} is used for the variable)
   * @param range         target variable's range
   */
  public void addCachedVariable(@NotNull String variableName, @NotNull TextRange range) {
    addCachedVariable(new Variable(variableName, myCachedVariableQualifier), range);
  }

  /**
   * @param variable  target variable to register within the current context
   * @param range     target variable's range
   */
  public void addCachedVariable(@Nullable Variable variable, @NotNull TextRange range) {
    myCachedVariables.put(variable, new Location(myCurrentFile, range));
  }

  /**
   * @param variable  target variable
   * @return          assignments registered for the given variable within the current context
   */
  @NotNull
  public Collection<Assignment> getAssignments(@NotNull Variable variable) {
    Collection<Assignment> result = myAssignmentsByVariable.get(variable);
    return result == null ? Collections.<Assignment>emptyList() : result;
  }

  /**
   * @param codeStructure  target code structure (see {@link #myCodeStructure})
   * @return               assignments registered for the target code structure. E.g. one might call this method with ['dependencies']
   *                       argument in order to get all assignments parsed from the <code>'dependencies {...}'</code> section of the
   *                       target <code>build.gradle</code> file
   */
  @NotNull
  public Collection<Assignment> getAssignments(@NotNull List<String> codeStructure) {
    Collection<Assignment> result = myAssignmentsByCodeStructure.get(codeStructure);
    return result == null ? Collections.<Assignment>emptyList() : result;
  }

  /**
   * Notifies current context that new file (given) is being parsed.
   * <p/>
   * Given argument is returned from subsequent {@link #getTargetFile()} calls.
   *
   * @param file  new file being parsed
   */
  public void onChangeFile(@NotNull VirtualFile file) {
    myCurrentFile = file;
  }

  /**
   * Notifies current context that new section is being parsed. E.g. when <code>'dependencies {...}'</code> is about to be parsed,
   * this method is called with <code>'dependencies'</code> argument.
   *
   * @param methodName
   */
  public void onMethodEnter(@NotNull String methodName) {
    myCodeStructure.add(methodName);
  }

  public void onMethodExit() {
    if (!myCodeStructure.isEmpty()) {
      myCodeStructure.remove(myCodeStructure.size() - 1);
    }
  }

  public boolean registerAssignmentFromCachedData(@NonNull String lValueVariableName,
                                                  @NotNull TextRange range,
                                                  @NotNull PsiElement valueElement) {
    return registerAssignmentFromCachedData(new Variable(lValueVariableName, myCachedVariableQualifier), range, valueElement);
  }

  /**
   * Delegates to the {@link #registerAssignmentFromCachedData(Variable, Location, PsiElement)} with location build from the given
   * range and {@link #getCurrentFile() current file}.
   *
   * @param lValue        lvalue to use for the target assignment
   * @param lValueRange   lvalue range
   * @param valueElement  PSI element for the rvalue of the whole assignment
   * @return              <code>true</code> if an assignment is registered for the given lvalue and cached rvalue;
   *                      <code>false</code> otherwise
   */
  public boolean registerAssignmentFromCachedData(@NonNull Variable lValue,
                                                  @NotNull TextRange lValueRange,
                                                  @NotNull PsiElement valueElement) {
    return registerAssignmentFromCachedData(lValue, new Location(myCurrentFile, lValueRange), valueElement);
  }

  /**
   * Registers assignment using given lvalue and cached {@link #getCachedVariables() variables}/{@link #getCachedValues() values} as
   * a rvalue if possible.
   *
   * @param lValue          lvalue to use for the target assignment
   * @param lValueLocation  lvalue location
   * @param valueElement    PSI element for the rvalue of the whole assignment
   * @return                <code>true</code> if an assignment is registered for the given lvalue and cached rvalue;
   *                        <code>false</code> otherwise
   */
  public boolean registerAssignmentFromCachedData(@NonNull Variable lValue,
                                                  @NotNull Location lValueLocation,
                                                  @NotNull PsiElement valueElement) {
    if (myCachedValues.isEmpty() && myCachedVariables.isEmpty()) {
      return false;
    }
    String wholeElementText = GradleEditorModelUtil.unquote(valueElement.getText());
    TextRange wholeElementRange = GradleEditorModelUtil.interestedRange(valueElement);

    // There is a possible case that we processed an expression like 'a = b' - i.e. 'a' here just references 'b', so, we don't want
    // to register a value. However, we do want to register a value for an assignment like 'a = b + 1'.
    final Value valueToUse;
    if (myCachedVariables.isEmpty()) {
      switch (myCachedValues.size()) {
        case 0: valueToUse = null; break;
        case 1: valueToUse = myCachedValues.get(0); break;
        // a = 1 + 2 - we don't evaluate '1 + 2' but keep it only as a source binding instead.
        default: valueToUse = new Value(wholeElementText, new Location(myCurrentFile, wholeElementRange));
      }
    }
    else if (myCachedVariables.size() == 1
             && GradleEditorModelUtil.isVariable(valueElement.getText(), myCachedVariables.keys().iterator().next().name)) {
      valueToUse = null;
    }
    else {
      valueToUse = new Value("", new Location(myCurrentFile, wholeElementRange));
    }

    TextRange assignmentRange = TextRange.create(lValueLocation.range.getStartOffset(), valueElement.getTextRange().getEndOffset());
    onAssignment(assignmentRange, lValue, lValueLocation, new Location(myCurrentFile, wholeElementRange), valueToUse, wholeElementText,
                 myCachedVariables);
    resetCaches();
    return true;
  }

  /**
   * Delegates to {@link #onAssignment(Assignment)} with an assignment built from the given data.
   *
   * @param assignmentRange assignment's range
   * @param lValue          assignment's lvalue
   * @param lValueLocation  lvalue location
   * @param rValueLocation  rvalue location
   * @param rValue          assignment's rvalue
   * @param rValueString    rvalue string
   * @param dependencies    dependencies used at the given assignment, e.g. <code>'a = b + 1'</code> is an assignment with
   *                        <code>'a'</code> as a lvalue, <code>null</code> as rvalue (because we don't evaluate it in runtime)
   *                        and <code>'b'</code> as a dependency
   */
  public void onAssignment(@NotNull TextRange assignmentRange,
                           @NonNull Variable lValue,
                           @NotNull Location lValueLocation,
                           @NotNull Location rValueLocation,
                           @Nullable Value rValue,
                           @Nullable String rValueString,
                           @NotNull Multimap<Variable, Location> dependencies) {
    onAssignment(
      new Assignment(assignmentRange, lValue, lValueLocation, rValueLocation, myCodeStructure, rValue, rValueString, dependencies)
    );
  }

  /**
   * Registers given assignment within the current context.
   * <p/>
   * It's exposed via {@link #getAssignments(List)} or {@link #getAssignments(Variable)} later.
   *
   * @param assignment  an assignment to register within the current context
   */
  public void onAssignment(@NotNull Assignment assignment) {
    myAssignmentsByVariable.put(assignment.lValue, assignment);
    myAssignmentsByCodeStructure.put(assignment.codeStructure, assignment);
  }

  /**
   * Populates {@link #myCachedVariableQualifier cached variable qualifier} by the given value, e.g. considering expression like
   * <code>'a.b.c = 1'</code> - this method is expected to be called with <code>'a'</code> argument, then <code>'b'</code>.
   *
   * @param qualifier  a qualifier to register within the current context
   */
  public void rememberVariableQualifier(@NotNull String qualifier) {
    if (!qualifier.isEmpty()) {
      myCachedVariableQualifier.add(qualifier);
    }
  }

  /**
   * Resets cached {@link #getCachedValues() values}, {@link #getCachedVariables() variables} and
   * {@link #rememberVariableQualifier(String) variable qualifier} registered within the current context (if any).
   */
  public void resetCaches() {
    myCachedVariableQualifier.clear();
    myCachedVariables.clear();
    myCachedValues.clear();
  }

  /**
   * Object representation of a a variable, might serve either as a lvalue or rvalue.
   */
  public static final class Variable {
    @NotNull public final String name;
    /**
     * Variable qualifier, e.g. it's <code>['a', 'b']</code> for definition like <code>'a.b.c = 1'</code>.
     */
    @NotNull public final List<String> qualifier;

    public Variable(@NotNull String name, @NotNull List<String> qualifier) {
      this.name = name;
      this.qualifier = ImmutableList.copyOf(qualifier);
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      return 31 * result + qualifier.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Variable variable = (Variable)o;

      return name.equals(variable.name) && qualifier.equals(variable.qualifier);
    }

    @Override
    public String toString() {
      String q = qualifier.isEmpty() ? "" : StringUtil.join(qualifier, ".") + ".";
      return q + name;
    }
  }

  public static class Assignment {
    @NotNull public final TextRange assignmentRange;
    @NotNull public final Variable lValue;
    @NotNull public final Location lValueLocation;
    @NotNull public final Location rValueLocation;
    @NotNull public final List<String> codeStructure;
    @Nullable public final Value value;
    @Nullable public final String rValueString;
    @NotNull public final Multimap<Variable, Location> dependencies;

    public Assignment(@NotNull TextRange assignmentRange,
                      @NotNull Variable lValue,
                      @NotNull Location lValueLocation,
                      @NotNull Location rValueLocation,
                      @NotNull List<String> codeStructure,
                      @Nullable Value value,
                      @Nullable String rValueString,
                      @NotNull Multimap<Variable, Location> dependencies) {
      this.assignmentRange = assignmentRange;
      this.lValue = lValue;
      this.lValueLocation = lValueLocation;
      this.rValueLocation = rValueLocation;
      this.codeStructure = codeStructure.isEmpty() ? Collections.<String>emptyList() : ImmutableList.copyOf(codeStructure);
      this.value = value;
      this.rValueString = rValueString;
      this.dependencies = dependencies.isEmpty() ? ImmutableMultimap.<Variable, Location>of() : ImmutableMultimap.copyOf(dependencies);
    }

    @Override
    public String toString() {
      StringBuilder buffer = new StringBuilder();
      buffer.append(StringUtil.join(codeStructure, "->"));
      if (buffer.length() > 0) {
        buffer.append(": ");
      }
      buffer.append(lValue);
      if (rValueString != null) {
        buffer.append(" = ").append(rValueString);
      }
      else if (value != null) {
        buffer.append(" = ").append(value);
      }
      if (!dependencies.isEmpty()) {
        buffer.append(" depends on ").append(StringUtil.join(dependencies.keySet(), ","));
      }
      return buffer.toString();
    }
  }

  public static class Value {
    @NotNull public final String value;
    @NotNull public final Location location;

    public Value(@NotNull String value, @NotNull Location location) {
      this.value = value;
      this.location = location;
    }

    @NotNull
    public Value shrink(@NotNull TextRange range) {
      if (range.equals(location.range)) {
        return this;
      }
      if (range.getStartOffset() < location.range.getStartOffset()
          || range.getStartOffset() >= location.range.getEndOffset()
          || range.getEndOffset() > location.range.getEndOffset()) {
        throw new IllegalArgumentException(String.format("Can't shrink value '%s' to range %s. Reason - given range is not inside "
                                                         + "the current one (%s)", this, range, location.range));
      }
      int newValueStartOffset = range.getStartOffset() - location.range.getStartOffset();
      int newValueEndOffset = value.length() - (location.range.getEndOffset() - range.getEndOffset());
      return new Value(value.substring(newValueStartOffset, newValueEndOffset), new Location(location.file, range));
    }

    @Override
    public String toString() {
      return String.format("'%s' at %s", value, location);
    }
  }

  public static class Location {
    @NotNull public final VirtualFile file;
    @NotNull public final TextRange range;

    public Location(@NotNull VirtualFile file, @NotNull TextRange range) {
      this.file = file;
      this.range = range;
    }

    @Override
    public int hashCode() {
      int result = file.hashCode();
      result = 31 * result + range.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Location location = (Location)o;

      return file.equals(location.file) && range.equals(location.range);
    }

    @Override
    public String toString() {
      return String.format("%s [%d;%d)", file.getName(), range.getStartOffset(), range.getEndOffset());
    }
  }
}
