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
package com.android.tools.idea.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tools.idea.testing.DisposerExplorer.VisitResult;
import com.android.tools.idea.testing.DisposerExplorer.Visitor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

/** Tests for the {@link DisposerExplorer} class. */
public class DisposerExplorerTest {
  private final Set<Disposable> liveDisposables = new LinkedHashSet<>();

  @After
  public void checkEverythingWasDisposed() {
    try {
      // If this check fails, then there may be leftover disposables in the disposer tree which may affect other tests.
      assertWithMessage("Some test disposables were not disposed").that(liveDisposables).isEmpty();
    } finally {
      new ArrayList<>(liveDisposables).forEach(Disposer::dispose);
    }
  }

  @Test
  public void testBasicMethods() {
    TestDisposable root1 = new TestDisposable("root1");
    TestDisposable a = new TestDisposable("root1.a");
    TestDisposable a1 = new TestDisposable("root1.a.1");
    TestDisposable a2 = new TestDisposable("root1.a.2");
    TestDisposable b = new TestDisposable("root1.b");
    TestDisposable b1 = new TestDisposable("root1.b.1");
    TestDisposable b2 = new TestDisposable("root1.b.2");
    TestDisposable root2 = new TestDisposable("root2");
    TestDisposable c = new TestDisposable("root2.c");
    Disposer.register(root1, a);
    Disposer.register(a, a1);
    Disposer.register(a, a2);
    Disposer.register(root1, b);
    Disposer.register(b, b1);
    Disposer.register(b, b2);
    Disposer.register(root2, c);
    Disposer.dispose(c); // Dispose c to create an orphan root.
    TestDisposable notRegistered = new TestDisposable("not_registered");

/* b/260862010
    assertThat(DisposerExplorer.isContainedInTree(b2)).isTrue();
    assertThat(DisposerExplorer.isContainedInTree(root2)).isTrue();
    assertThat(DisposerExplorer.isContainedInTree(notRegistered)).isFalse();

    assertThat(DisposerExplorer.getTreeRoots()).containsExactly(root1, root2);

    assertThat(DisposerExplorer.getChildren(a)).containsExactly(a1, a2);
    assertThat(DisposerExplorer.hasChildren(a)).isTrue();
    assertThat(DisposerExplorer.getChildren(root2)).isEmpty();
    assertThat(DisposerExplorer.hasChildren(root2)).isFalse();
    assertThat(DisposerExplorer.getChildren(notRegistered)).isEmpty();
    assertThat(DisposerExplorer.hasChildren(notRegistered)).isFalse();

    assertThat(DisposerExplorer.getParent(a1)).isSameAs(a);
    assertThat(DisposerExplorer.getParent(root1)).isNull();
    assertThat(DisposerExplorer.getParent(notRegistered)).isNull();

    Collector v1 = new Collector();
    DisposerExplorer.visitTree(v1);
    assertThat(v1.visited).containsExactly(root1, a, a1, a2, b, b1, b2, root2);

    Collector v2 = new Collector() {
      @Override
      @NotNull
      public VisitResult visit(@NotNull Disposable disposable) {
        super.visit(disposable);
        return disposable == a ? VisitResult.SKIP_CHILDREN : disposable == b1 ? VisitResult.ABORT : VisitResult.CONTINUE;
      }
    };
    DisposerExplorer.visitDescendants(root1, v2);
    assertThat(v2.visited).containsExactly(a, b, b1).inOrder();

    Collector v3 = new Collector();
    DisposerExplorer.visitDescendants(root1, v3);
    assertThat(v3.visited).containsExactly(a, a1, a2, b, b1, b2).inOrder();

    assertThat(DisposerExplorer.findAll(d -> d.toString().endsWith("2"))).containsExactly(root2, a2, b2);

    assertThat(DisposerExplorer.findFirst(d -> d.toString().endsWith("a"))).isSameAs(a);
    assertThat(DisposerExplorer.findFirst(d -> d.toString().endsWith("x"))).isNull();
b/260862010 */

    // Clean up to avoid interference with other tests.
    Disposer.dispose(root1);
    Disposer.dispose(root2);
    Disposer.dispose(notRegistered);
  }

  private class TestDisposable implements Disposable {
    @NotNull private final String myName;

    TestDisposable(@NotNull String name) {
      myName = name;
      liveDisposables.add(this);
    }

    @Override
    @NotNull
    public String toString() {
      return myName;
    }

    @Override
    public void dispose() {
      liveDisposables.remove(this);
    }
  }

  private static class Collector implements Visitor {
    private final List<Disposable> visited = new ArrayList<>();

    @Override
    @NotNull
    public VisitResult visit(@NotNull Disposable disposable) {
      visited.add(disposable);
      return VisitResult.CONTINUE;
    }
  }
}
