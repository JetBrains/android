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
package com.android.tools.idea.gradle.parser;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.parser.BuildFileKey.escapeLiteralString;

/**
 * Base class for classes that parse Gradle Groovy files (e.g. settings.gradle, build.gradle). It provides a number of convenience
 * methods for its subclasses to extract interesting pieces from Gradle files.
 *
 * Note that if you do any mutations on the PSI structure you must be inside a write action. See
 * {@link com.intellij.util.ActionRunner#runInsideWriteAction}.
 */
class GradleGroovyFile {
  private static final Logger LOG = Logger.getInstance(GradleGroovyFile.class);

  protected final Project myProject;
  protected final VirtualFile myFile;
  protected GroovyFile myGroovyFile = null;

  public GradleGroovyFile(@NotNull VirtualFile file, @NotNull Project project) {
    myProject = project;
    myFile = file;
    reload();
  }

  public Project getProject() {
    return myProject;
  }

  /**
   * Automatically reformats all the Groovy code inside the given closure.
   */
  static void reformatClosure(@NotNull GrStatementOwner closure) {
    new ReformatCodeProcessor(closure.getProject(), closure.getContainingFile(), closure.getParent().getTextRange(), false)
        .runWithoutProgress();

    // Now strip out any blank lines. They tend to accumulate otherwise. To do this, we iterate through our elements and find those that
    // consist only of whitespace, and eliminate all double-newline occurrences.
    for (PsiElement psiElement : closure.getChildren()) {
      if (psiElement instanceof LeafPsiElement) {
        String text = psiElement.getText();
        if (StringUtil.isEmptyOrSpaces(text)) {
          String newText = text;
          while (newText.contains("\n\n")) {
            newText = newText.replaceAll("\n\n", "\n");
          }
          if (!newText.equals(text)) {
            ((LeafPsiElement)psiElement).replaceWithText(newText);
          }
        }
      }
    }
  }

  /**
   * Creates a new, blank-valued property at the given path.
   */
  @Nullable
  static GrMethodCall createNewValue(@NotNull GrStatementOwner root,
                                     @NotNull BuildFileKey key,
                                     @Nullable Object value,
                                     boolean reformatClosure) {
    // First iterate through the components of the path and make sure all of the nested closures are in place.
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(root.getProject());
    String path = key.getPath();
    String[] parts = path.split("/");
    GrStatementOwner parent = root;
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      GrStatementOwner closure = getMethodClosureArgument(parent, part);
      if (closure == null) {
        parent.addStatementBefore(factory.createStatementFromText(part + " {}"), null);
        closure = getMethodClosureArgument(parent, part);
        if (closure == null) {
          return null;
        }
      }
      parent = closure;
    }
    String name = parts[parts.length - 1];
    String text = name + " " + key.getType().convertValueToExpression(value);
    GrStatement statementBefore = null;
    if (key.shouldInsertAtBeginning()) {
      GrStatement[] parentStatements = parent.getStatements();
      if (parentStatements.length > 0) {
        statementBefore = parentStatements[0];
      }
    }
    parent.addStatementBefore(factory.createStatementFromText(text), statementBefore);
    if (reformatClosure) {
      reformatClosure(parent);
    }
    return getMethodCall(parent, name);
  }

  /**
   * Refreshes its state by rereading the contents from the underlying build file.
   */
  public void reload() {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (!myFile.exists()) {
          LOG.warn("File " + myFile.getPath() + " no longer exists");
          return;
        }
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);
        if (psiFile == null) {
          LOG.warn("Could not find PsiFile for " + myFile.getPath());
          return;
        }
        if (!(psiFile instanceof GroovyFile)) {
          LOG.warn("PsiFile " + psiFile.getName() + " is not a Groovy file");
          return;
        }
        myGroovyFile = (GroovyFile)psiFile;
        onPsiFileAvailable();
      }
    };

    if (myProject.isInitialized()) {
      ApplicationManager.getApplication().runReadAction(runnable);
    }
    else {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(runnable);
    }
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public PsiFile getPsiFile() {
    return myGroovyFile;
  }

  /**
   * @throws IllegalStateException if the instance has not parsed its PSI file yet in a
   * {@link StartupManager#runWhenProjectIsInitialized(Runnable)} callback. To resolve this, wait until {@link #onPsiFileAvailable()}
   * is called before invoking methods.
   */
  protected void checkInitialized() {
    if (myGroovyFile == null) {
      throw new IllegalStateException("PsiFile not parsed for file " + myFile.getPath() +". Wait until onPsiFileAvailable() is called.");
    }
  }

  /**
   * Commits any {@link Document} changes outstanding to the document that underlies the PSI representation. Call this before manipulating
   * PSI to ensure the PSI model of the document is in sync with what's on disk. Must be run inside a write action.
   */
  protected void commitDocumentChanges() {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(myGroovyFile);
    if (document != null) {
      documentManager.commitDocument(document);
    }
  }

  /**
   * Finds a method identified by the given path. It descends into nested closure arguments of method calls to find the leaf method.
   * For example, if you have this code in Groovy:
   *
   * method_a {
   *   method_b {
   *     method_c 'literal'
   *   }
   * }
   *
   * you can find method_c using the path "method_a/method_b/method_c"
   *
   * It returns the first eligible result. If you have code like this:
   *
   * method_a {
   *   method_b 'literal1'
   *   method_b 'literal2'
   * }
   *
   * a search for "method_a/method_b" will only return the first invocation of method_b.
   *
   * It continues searching until it has exhausted all possibilities of finding the path. If you have code like this:
   *
   * method_a {
   *   method_b 'literal1'
   * }
   *
   * method_a {
   *   method_c 'literal2'
   * }
   *
   * a search for "method_a/method_c" will succeed: it will not give up just because it doesn't find it in the first method_a block.
   *
   * @param root the block to use as the root of the path
   * @param path the slash-delimited chain of methods with closure arguments to navigate to find the final leaf.
   * @return the resultant method, or null if it could not be found.
   */
  protected static @Nullable GrMethodCall getMethodCallByPath(@NotNull GrStatementOwner root, @NotNull String path) {
    if (path.isEmpty() || path.endsWith("/")) {
      return null;
    }
    int slash = path.indexOf('/');
    String pathElement = slash == -1 ? path : path.substring(0, slash);
    for (GrMethodCall gmc : getMethodCalls(root, pathElement)) {
      if (slash == -1) {
        return gmc;
      }
      if (gmc == null) {
        return null;
      }
      GrClosableBlock[] blocks = gmc.getClosureArguments();
      if (blocks.length != 1) {
        return null;
      }
      GrMethodCall subresult = getMethodCallByPath(blocks[0], path.substring(slash + 1));
      if (subresult != null) {
        return subresult;
      }
    }
    return null;
  }

  /**
   * Returns an array of arguments for the given method call. Note that it returns only regular arguments (via the
   * {@link org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall#getArgumentList()} call), not closure arguments
   * (via the {@link org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall#getClosureArguments()} call).
   *
   * @return the array of arguments. This method never returns null; even in the case of a nonexistent argument list or error, it will
   * return an empty array.
   */
  protected static @NotNull GroovyPsiElement[] getArguments(@NotNull GrCall gmc) {
    GrArgumentList argList = gmc.getArgumentList();
    if (argList == null) {
      return GroovyPsiElement.EMPTY_ARRAY;
    }
    return argList.getAllArguments();
  }

  /**
   * Returns the first argument for the given method call (which can be a literal, expression, or closure), or null if the method has
   * no arguments.
   */
  protected static @Nullable GroovyPsiElement getFirstArgument(@NotNull GrCall gmc) {
    GroovyPsiElement[] arguments = getArguments(gmc);
    return arguments.length > 0 ? arguments[0] : null;
  }

  /**
   * Ensures that argument list has only one argument and that the argument value is a string constant.
   *
   * @return a string value of the argument or <code>null</code> if such a value cannot be deduced
   */
  @Nullable
  protected static String getSingleStringArgumentValue(@NotNull GrCall methodCall) {
    GroovyPsiElement argument = getFirstArgument(methodCall);
    if (argument instanceof GrLiteral) {
      Object value = ((GrLiteral)argument).getValue();
      return value instanceof String ? (String)value : null;
    }
    else {
      return null;
    }
  }

  /**
   * Returns the first method call of the given method name in the given parent statement block, or null if one could not be found.
   */
  protected static @Nullable GrMethodCall getMethodCall(@NotNull GrStatementOwner parent, @NotNull String methodName) {
    return Iterables.getFirst(getMethodCalls(parent, methodName), null);
  }

  /**
   * Returns all statements in the parent statement block that are method calls.
   */
  protected static @NotNull Iterable<GrMethodCall> getMethodCalls(@NotNull GrStatementOwner parent) {
    return Iterables.filter(Arrays.asList(parent.getStatements()), GrMethodCall.class);
  }

  /**
   * Returns all statements in the parent statement block that are method calls with the given method name
   */
  protected static @NotNull Iterable<GrMethodCall> getMethodCalls(@NotNull GrStatementOwner parent, @NotNull final String methodName) {
    return Iterables.filter(getMethodCalls(parent), new Predicate<GrMethodCall>() {
      @Override
      public boolean apply(@Nullable GrMethodCall input) {
        return input != null && methodName.equals(getMethodCallName(input));
      }
    });
  }

  /**
   * Returns the name of the given method call
   */
  protected static @NotNull String getMethodCallName(@NotNull GrMethodCall gmc) {
    GrExpression expression = gmc.getInvokedExpression();
    return expression.getText() != null ? expression.getText() : "";
  }

  /**
   * Returns all arguments in the given argument list that are of the given type.
   */
  protected static @NotNull <E> Iterable<E> getTypedArguments(@NotNull GrArgumentList args, @NotNull Class<E> clazz) {
    return Iterables.filter(Arrays.asList(args.getAllArguments()), clazz);
  }

  /**
   * Returns all arguments of the given method call that are literals.
   */
  protected static @NotNull Iterable<GrLiteral> getLiteralArguments(@NotNull GrMethodCall gmc) {
    GrArgumentList argumentList = gmc.getArgumentList();
    return getTypedArguments(argumentList, GrLiteral.class);
  }

  /**
   * Returns values of all literal-typed arguments of the given method call.
   */
  protected static @NotNull Iterable<Object> getLiteralArgumentValues(@NotNull GrMethodCall gmc) {
    return Iterables.filter(Iterables.transform(getLiteralArguments(gmc), new Function<GrLiteral, Object>() {
      @Override
      public Object apply(@Nullable GrLiteral input) {
        return (input != null) ? input.getValue() : null;
      }
    }), Predicates.notNull());
  }

  /**
   * If the given method takes named arguments, returns those arguments as a name:value map. Returns an empty map otherwise.
   */
  protected static @NotNull Map<String, Object> getNamedArgumentValues(@NotNull GrMethodCall gmc) {
    GrArgumentList argumentList = gmc.getArgumentList();
    Map<String, Object> values = Maps.newHashMap();
    for (GrNamedArgument grNamedArgument : getTypedArguments(argumentList, GrNamedArgument.class)) {
      values.put(grNamedArgument.getLabelName(), parseValueExpression(grNamedArgument.getExpression()));
    }
    return values;
  }

  /**
   * Given a Groovy expression, parses it as if it's literal or list type, and returns the corresponding literal value or List
   * type. Returns null if the expression cannot be evaluated as a literal or list type.
   */
  protected static @Nullable Object parseValueExpression(@Nullable GrExpression gre) {
    if (gre instanceof GrLiteral) {
      return ((GrLiteral)gre).getValue();
    } else if (gre instanceof GrListOrMap) {
      GrListOrMap grLom = (GrListOrMap)gre;
      if (grLom.isMap()) {
        return null;
      }
      List<Object> values = Lists.newArrayList();
      for (GrExpression subexpression : grLom.getInitializers()) {
        Object subValue = parseValueExpression(subexpression);
        if (subValue != null) {
          values.add(subValue);
        }
      }
      return values;
    } else {
      return null;
    }
  }

  /**
   * Returns a text string with the Groovy expression that will represent the given map as a named argument list suitable for use in
   * a method call.
   */
  protected static @NotNull String convertMapToGroovySource(@NotNull Map<String, Object> map) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(entry.getKey());
      sb.append(": ");
      sb.append(convertValueToGroovySource(entry.getValue()));
    }
    return sb.toString();
  }

  /**
   * Returns a text string with the Groovy expression that will represent the given object. It can be a literal type or a list of
   * literals or sub-lists.
   */
  protected static @NotNull String convertValueToGroovySource(@NotNull Object value) {
    if (value instanceof List) {
      StringBuilder sb = new StringBuilder();
      sb.append('[');
      for (Object v : ((List)value)) {
        if (sb.length() > 1) {
          sb.append(", ");
        }
        sb.append(convertValueToGroovySource(v));
      }
      sb.append(']');
      return sb.toString();
    } else if (value instanceof Number || value instanceof Boolean) {
      return value.toString();
    } else {
      return "'" + escapeLiteralString(value.toString()) + "'";
    }
  }
  /**
   * Returns the value of the first literal argument in the given method call's argument list.
   */
  protected static @Nullable Object getFirstLiteralArgumentValue(@NotNull GrMethodCall gmc) {
    GrLiteral lit = getFirstLiteralArgument(gmc);
    return lit != null ? lit.getValue() : null;
  }

  /**
   * Returns the first literal argument in the given method call's argument list.
   */
  protected static @Nullable GrLiteral getFirstLiteralArgument(@NotNull GrMethodCall gmc) {
    return Iterables.getFirst(getLiteralArguments(gmc), null);
  }

  /**
   * Returns the first argument of the method call with the given name in the given parent that is a closure, or null if the method
   * or its closure could not be found.
   */
  protected static @Nullable GrClosableBlock getMethodClosureArgument(@NotNull GrStatementOwner parent, @NotNull String methodName) {
    GrMethodCall methodCall = getMethodCall(parent, methodName);
    if (methodCall == null) {
      return null;
    }
    return getMethodClosureArgument(methodCall);
  }

  /**
   * Returns the first argument of the given method call that is a closure, or null if the closure could not be found.
   */
  public static @Nullable GrClosableBlock getMethodClosureArgument(@NotNull GrMethodCall methodCall) {
    return Iterables.getFirst(Arrays.asList(methodCall.getClosureArguments()), null);
  }

  /**
   * Returns the value in the file for the given key, or null if not present.
   */
  static @Nullable Object getValueStatic(@NotNull GrStatementOwner root, @NotNull BuildFileKey key) {
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method == null) {
      return null;
    }
    GroovyPsiElement arg = key.getType() == BuildFileKeyType.CLOSURE ? getMethodClosureArgument(method) : getFirstArgument(method);
    if (arg == null) {
      return null;
    }
    return key.getValue(arg);
  }

  /**
   * Sets the value for the given key
   */
  static void setValueStatic(@NotNull GrStatementOwner root, @NotNull BuildFileKey key, @NotNull Object value, boolean reformatClosure,
                             @Nullable ValueFactory.KeyFilter filter) {
    if (value == GradleBuildFile.UNRECOGNIZED_VALUE) {
      return;
    }
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method == null) {
      method = createNewValue(root, key, value, reformatClosure);
      if (key.getType() != BuildFileKeyType.CLOSURE) {
        return;
      }
    }
    if (method != null) {
      GroovyPsiElement arg = key.getType() == BuildFileKeyType.CLOSURE  ? getMethodClosureArgument(method) : getFirstArgument(method);
      if (arg == null) {
        return;
      }
      key.setValue(arg, value, filter);
    }
  }

  /**
   * Removes the build file value identified by the given key
   */
  static void removeValueStatic(@NotNull GrStatementOwner root, @NotNull BuildFileKey key) {
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method != null) {
      method.delete();
    }
  }

  /**
   * Override this method if you wish to be called when the underlying PSI file is available and you would like to parse it.
   */
  protected void onPsiFileAvailable() {
  }

  @Override
  public @NotNull String toString() {
    if (myGroovyFile == null) {
      return "<uninitialized>";
    } else {
      ToStringPsiVisitor visitor = new ToStringPsiVisitor();
      myGroovyFile.accept(visitor);
      return myFile.getPath() + ":\n" + visitor.toString();
    }
  }

  private static class ToStringPsiVisitor extends PsiRecursiveElementVisitor {
    private StringBuilder myString = new StringBuilder();
    @Override
    public void visitElement(final @NotNull PsiElement element) {
      PsiElement e = element;
      while (e.getParent() != null) {
        myString.append("  ");
        e = e.getParent();
      }
      myString.append(element.getClass().getName());
      myString.append(": ");
      myString.append(element.toString());
      if (element instanceof LeafPsiElement) {
        myString.append(" ");
        myString.append(element.getText());
      }
      myString.append("\n");
      super.visitElement(element);
    }

    public @NotNull String toString() {
      return myString.toString();
    }
  }
}
