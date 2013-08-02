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
import com.google.common.collect.Iterables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.Arrays;

/**
 * Base class for classes that parse Gradle Groovy files (e.g. settings.gradle, build.gradle). It provides a number of convenience
 * methods for its subclasses to extract interesting pieces from Gradle files.
 *
 * Note that if you do any mutations on the PSI structure you must be inside a write action. See
 * {@link com.intellij.util.ActionRunner#runInsideWriteAction}.
 */
class GradleGroovyFile {
  private static final Logger LOG = Logger.getInstance(GradleGroovyFile.class);
  protected static final GroovyPsiElement[] EMPTY_ELEMENT_ARRAY = new GroovyPsiElement[0];
  protected static final Iterable<GrLiteral> EMPTY_LITERAL_ITERABLE = Arrays.asList(new GrLiteral[0]);

  protected final Project myProject;
  protected final VirtualFile myFile;
  protected GroovyFile myGroovyFile = null;

  public GradleGroovyFile(@NotNull VirtualFile file, @NotNull Project project) {
    myProject = project;
    myFile = file;
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
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
    });
  }

  public VirtualFile getFile() {
    return myFile;
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
  protected static @NotNull GroovyPsiElement[] getArguments(@NotNull GrMethodCall gmc) {
    GrArgumentList argList = gmc.getArgumentList();
    if (argList == null) {
      return EMPTY_ELEMENT_ARRAY;
    }
    return argList.getAllArguments();
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
    return (gmc.getInvokedExpression() != null && gmc.getInvokedExpression().getText() != null) ? gmc.getInvokedExpression().getText() : "";
  }

  /**
   * Returns all arguments in the given argument list that are literals.
   */
  protected static @NotNull Iterable<GrLiteral> getLiteralArguments(@NotNull GrArgumentList args) {
    return Iterables.filter(Arrays.asList(args.getAllArguments()), GrLiteral.class);
  }

  /**
   * Returns all arguments of the given method call that are literals.
   */
  protected static @NotNull Iterable<GrLiteral> getLiteralArguments(@NotNull GrMethodCall gmc) {
    GrArgumentList argumentList = gmc.getArgumentList();
    if (argumentList == null) {
      return EMPTY_LITERAL_ITERABLE;
    }
    return getLiteralArguments(argumentList);
  }

  /**
   * Returns values of all literal-typed arguments of the given method call.
   */
  protected static @NotNull Iterable<Object> getLiteralArgumentValues(@NotNull GrMethodCall gmc) {
    return Iterables.transform(getLiteralArguments(gmc), new Function<GrLiteral, Object>() {
      @Override
      public Object apply(@Nullable GrLiteral input) {
        return (input != null) ? input.getValue() : null;
      }
    });
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
  protected static GrClosableBlock getMethodClosureArgument(@NotNull GrMethodCall methodCall) {
    return Iterables.getFirst(Arrays.asList(methodCall.getClosureArguments()), null);
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
