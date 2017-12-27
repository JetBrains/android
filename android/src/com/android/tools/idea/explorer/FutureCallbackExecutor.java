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
package com.android.tools.idea.explorer;

import com.google.common.util.concurrent.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * An {@link Executor} implementation that registers {@link ListenableFuture} callbacks
 * to be executed with itself via the {@link #addCallback(ListenableFuture, FutureCallback)} method.
 *
 * The goal is to act as an alternative to {@link Futures#addCallback(ListenableFuture, FutureCallback)}
 * to make the <code>executor</code> parameter explicit.
 */
public class FutureCallbackExecutor implements Executor {
  @NotNull private final Executor myExecutor;

  public FutureCallbackExecutor(@NotNull Executor executor) {
    myExecutor = executor;
  }

  public static FutureCallbackExecutor wrap(@NotNull Executor executor) {
    if (executor instanceof FutureCallbackExecutor) {
      return (FutureCallbackExecutor)executor;
    }
    return new FutureCallbackExecutor(executor);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    myExecutor.execute(command);
  }

  /**
   * Submits a {@link Callable} in this executor queue, and returns a {@link ListenableFuture}
   * that completes with the callable result or the exception thrown from the callable.
   */
  public <V> ListenableFuture<V> executeAsync(@NotNull Callable<V> function) {
    SettableFuture<V> futureResult = SettableFuture.create();
    myExecutor.execute(() -> {
      try {
        futureResult.set(function.call());
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });
    return futureResult;
  }

  /**
   * Adds a {@link FutureCallback} to a {@link ListenableFuture} with this instance as the executor.
   */
  public <V> void addCallback(@NotNull final ListenableFuture<V> future,
                              @NotNull final FutureCallback<? super V> callback) {
    Futures.addCallback(future, callback, this);
  }

  /**
   * Adds a {@link BiConsumer} callback to a {@link ListenableFuture} with this instance as the executor.
   * <ul>
   * <li>In case of success, the {@link BiConsumer#accept consumer.accept(v, null)} method is invoked,
   * where "{@code v}" is the future completion value.</li>
   * <li>In case of failure, the {@link BiConsumer#accept consumer.accept(null, t)} method is invoked,
   * where "{@code t}" is the future exception.</li>
   * </ul>
   */
  public <V> void addConsumer(@NotNull final ListenableFuture<V> future,
                              @NotNull final BiConsumer<? super V, Throwable> consumer) {
    addCallback(future, new FutureCallback<V>() {
      @Override
      public void onSuccess(@Nullable V result) {
        consumer.accept(result, null);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        consumer.accept(null, t);
      }
    });
  }

  /**
   * Similar to {@link ListenableFuture#addListener(Runnable, Executor)}, using this instance as the executor.
   */
  public <V> void addListener(@NotNull final ListenableFuture<V> future,
                              @NotNull final Runnable listener) {
    future.addListener(listener, myExecutor);
  }

  /**
   * Similar to {@link Futures#transform(ListenableFuture, com.google.common.base.Function, Executor)},
   * using this instance as the executor.
   * <p>Unlike {@link Futures#transform(ListenableFuture, com.google.common.base.Function, Executor) Futures.transform},
   * the {@link ThrowableFunction function} can throw checked exceptions.
   * See {@link Futures.AbstractChainingFuture#run}
   */
  public <I, O> ListenableFuture<O> transform(@NotNull ListenableFuture<I> input,
                                              @NotNull ThrowableFunction<? super I, ? extends O> function) {
    return Futures.transform(input, wrapThrowableFunction(function)::apply, myExecutor);
  }

  /**
   * Similar to {@link Futures#transform(ListenableFuture, com.google.common.base.Function, Executor)},
   * using this instance as the executor.
   */
  public <I, O> ListenableFuture<O> transformAsync(@NotNull ListenableFuture<I> input,
                                                   @NotNull AsyncFunction<? super I, ? extends O> function) {
    return Futures.transformAsync(input, function, myExecutor);
  }

  /**
   * Similar to {@link Futures#catching(ListenableFuture, Class, com.google.common.base.Function, Executor)},
   * using this instance as the executor.
   * <p>Unlike {@link Futures#transform(ListenableFuture, com.google.common.base.Function, Executor) Futures.transform},
   * the {@link ThrowableFunction function} can throw checked exceptions.
   * See {@link Futures.AbstractChainingFuture#run} implementation.
   */
  @NotNull
  public <V, X extends Throwable> ListenableFuture<V> catching(@NotNull ListenableFuture<? extends V> input, Class<X> exceptionType,
                                                               @NotNull ThrowableFunction<? super X, ? extends V> fallback) {
    return Futures.catching(input, exceptionType, wrapThrowableFunction(fallback)::apply, this);
  }

  /**
   * Similar to {@link Futures#catchingAsync(ListenableFuture, Class, AsyncFunction)},
   * using this instance as the executor.
   */
  @NotNull
  public <V, X extends Throwable> ListenableFuture<V> catchingAsync(@NotNull ListenableFuture<? extends V> input, Class<X> exceptionType,
                                                                    @NotNull AsyncFunction<? super X, ? extends V> fallback) {
    return Futures.catchingAsync(input, exceptionType, fallback, this);
  }

  /**
   * Similar to {@link Futures#transform(ListenableFuture, com.google.common.base.Function, Executor)},
   * using this instance as the executor, but executes the async function in both success and error
   * completion. If the {@code finallyBlock} itself fails, the returned future fails too.
   */
  public <I> ListenableFuture<I> finallyAsync(@NotNull ListenableFuture<I> input,
                                              @NotNull Callable<ListenableFuture<Void>> finallyBlock) {
    SettableFuture<I> futureResult = SettableFuture.create();
    addConsumer(input, (i, futureThrowable) -> {
      try {
        ListenableFuture<Void> futureFinallyBlock = finallyBlock.call();
        addConsumer(futureFinallyBlock, (aVoid, finallyError) -> {
          // Prefer original error over error from finally block
          if (futureThrowable != null) {
            futureResult.setException(futureThrowable);
          }
          else if (finallyError != null){
            futureResult.setException(finallyError);
          }
          else {
            futureResult.set(i);
          }
        });
      }
      catch (Throwable t) {
        // Prefer original error over error from finally block
        if (futureThrowable != null) {
          futureResult.setException(futureThrowable);
        }
        else {
          futureResult.setException(t);
        }
      }
    });
    return futureResult;
  }

  /**
   * Execute a task from the {@code taskFactory} for each element of the {@code iterator},
   * waiting for the {@link ListenableFuture} returned by each task before executing the next one.
   *
   * <p>The goal is to serialize the execution of multiple tasks that would otherwise
   * execute in parallel, typically as a way of throttling tasks.
   *
   * <p>Returns a {@link ListenableFuture} that completes when all tasks have completed.
   *
   * @param iterator    The source of elements to process
   * @param taskFactory A factory {@link Function} that returns a {@link ListenableFuture} for a given element
   * @param <T>         The type of the elements to process
   */
  @NotNull
  public <T> ListenableFuture<Void> executeFuturesInSequence(@NotNull Iterator<T> iterator,
                                                             @NotNull Function<T, ListenableFuture<Void>> taskFactory) {
    SettableFuture<Void> finalResult = SettableFuture.create();
    executeFuturesInSequenceWorker(iterator, taskFactory, finalResult);
    return finalResult;
  }

  private <T> void executeFuturesInSequenceWorker(@NotNull Iterator<T> iterator,
                                                  @NotNull Function<T, ListenableFuture<Void>> taskFactory,
                                                  @NotNull SettableFuture<Void> finalResult) {
    if (iterator.hasNext()) {
      ListenableFuture<Void> future = taskFactory.apply(iterator.next());
      addConsumer(future, (aVoid, throwable) -> executeFuturesInSequenceWorker(iterator, taskFactory, finalResult));
    }
    else {
      finalResult.set(null);
    }
  }

  /**
   * Wrap a {@link ThrowableFunction} into a {@link Function} using a {@link UndeclaredThrowableException}.
   *
   * <p>See {@link Futures.AbstractChainingFuture#run} implementation: the {@link UndeclaredThrowableException} in unwrapped
   * to the cause exception when caught, so callers don't see the UndeclaredThrowableException.
   */
  @NotNull
  private static <I, O> Function<I, O> wrapThrowableFunction(@NotNull ThrowableFunction<I, O> function) {
    return new Function<I, O>() {
      @Nullable
      @Override
      public O apply(@Nullable I input) {
        try {
          return function.apply(input);
        }
        catch (Exception e) {
          throw new UndeclaredThrowableException(e);
        }
      }
    };
  }

  /**
   * Similar to {@link Function} but allows the {@link #apply(Object)} method to throw checked exceptions.
   */
  public interface ThrowableFunction<T, R> {
    R apply(T t) throws Exception;
  }
}