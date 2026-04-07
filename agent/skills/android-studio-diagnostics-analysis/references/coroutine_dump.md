
# Coroutine Dump Analysis

Use this skill to parse, query, and diagnose Kotlin coroutine dumps captured from IntelliJ-based IDEs. Thread dumps from IntelliJ contain an extra section at the end (`---------- Coroutine dump ----------`) with a full structured coroutine hierarchy.

## Step 1: Locate and Load the Dump

When the user provides a thread dump file path, read the file and identify the two main sections:

1. **Thread dump section** (from the start to `---------- Coroutine dump ----------`)
2. **Coroutine dump section** (from that separator to EOF)

Both sections are needed for a full diagnosis. Use `Grep` to find the separator line number, then `Read` each section.

## Step 1.5: Use the Parser Script

A Python parser script is available at `scripts/analyze_coroutines.py` for automated analysis. **Always start with this before manual grepping.**

```bash
python3 scripts/analyze_coroutines.py <dump_file> <command> [args...]
```

| Command | Description | When to Use |
|---------|-------------|-------------|
| `summary` | Overview stats: coroutine counts, states, dispatchers, lock holders, EDT state, RUNNING/CREATED coroutines | **Always run first** for any dump analysis |
| `deadlock` | Detect deadlock cycles: EDT↔read/write lock, runBlocking↔EDT, non-suspending runReadAction, starved EDT coroutines | **Run second** when investigating a freeze |
| `lock-holders` | Find coroutines holding read/write locks (ComputationState), show their RUNNING descendants with thread cross-refs | When you need to understand who holds locks |
| `lock-waiters` | Find coroutines/threads waiting to acquire locks | To see what's blocked by lock contention |
| `edt` | Show EDT thread state, stack, and all EDT/UI-dispatched coroutines | To understand EDT utilization |
| `running` | List all RUNNING coroutines with thread cross-refs, stacks, ancestry | To see what's actively executing |
| `tree <pattern>` | Show full ancestry chain of coroutines matching a name/ID pattern | To trace a specific coroutine's place in the hierarchy |
| `search <pattern>` | Search coroutines by name, class, or stack frame content | To find coroutines related to a specific service/class |
| `threads` | Cross-reference all threads with their coroutines | To see thread↔coroutine mappings |

**Typical workflow for freeze diagnosis:**
1. `summary` → get the overview, note lock holders and RUNNING coroutines
2. `deadlock` → check for circular dependencies
3. `lock-holders` → deep-dive into what's holding locks and why
4. `tree <name>` → trace specific coroutines of interest
5. Manual `Read`/`Grep` for specific stack frames or dump regions

## Step 2: Understand the Coroutine Dump Format

### Hierarchy via Indentation

The coroutine dump uses indentation (tabs) to represent parent-child structured concurrency relationships:

```
- parent coroutine header
	- child coroutine header
		- grandchild coroutine header
			at stackframe
			at stackframe
```

Each entry starts with `- ` (dash space). Deeper indentation = child of the coroutine above with less indentation. Stack trace lines are indented under their coroutine and start with `at `.

### Header Format

```
-[xN of] "name":CoroutineClass{JobState}[, state: STATE] [context]
```

**Components:**

| Component | Description | Example |
|-----------|-------------|---------|
| `[xN of]` | Repeated subtree count (only when N>1) | `[x8 of]`, `[x26 of]` |
| `"name"` | Coroutine name (quoted, optional) | `"Main toolbar update"`, `"static nav bar vm"` |
| `:supervisor:` | Indicates a supervisor scope (between name and class) | `"Application":supervisor:ChildScope{Active}` |
| `CoroutineClass` | Kotlin coroutine type | `StandaloneCoroutine`, `DeferredCoroutine`, `ProducerCoroutine`, `BlockingCoroutine`, `LazyStandaloneCoroutine`, `ChildScope`, `SupervisorJobImpl`, `JobImpl` |
| `{JobState}` | Job lifecycle state | `{Active}`, `{Completing}`, `{Cancelled}`, `{Cancelling}` |
| `state: STATE` | Execution state (only on leaf/executable coroutines) | `SUSPENDED`, `RUNNING`, `CREATED` |
| `[context]` | Comma-separated coroutine context elements | See Context Elements below |

### Coroutine States

| State | Meaning | Diagnostic Significance |
|-------|---------|------------------------|
| `SUSPENDED` | Coroutine is waiting at a suspension point | Normal for most coroutines. Check the stack trace to see WHAT it's waiting on. |
| `RUNNING` | Coroutine is actively executing on a thread | Look at the stack trace to see what it's currently doing. Cross-reference with the thread dump. |
| `CREATED` | Initialized but hasn't started yet | May indicate a coroutine queued behind others (e.g., `limitedParallelism`). |

### Coroutine Types

| Type | Created By | Meaning |
|------|-----------|---------|
| `StandaloneCoroutine` | `launch { }` | Fire-and-forget coroutine |
| `DeferredCoroutine` | `async { }` | Returns a result via `await()` |
| `LazyStandaloneCoroutine` | `launch(start=LAZY)` | Lazily started |
| `BlockingCoroutine` | `runBlocking { }` | Blocks a thread waiting for completion |
| `ProducerCoroutine` | `produce { }`, `channelFlow { }` | Produces values into a channel |
| `ChildScope` / `supervisor:ChildScope` | `coroutineScope { }` / `supervisorScope { }` | Structural scope container |
| `SupervisorJobImpl` | `SupervisorJob()` | Supervisor job (children don't cancel siblings) |
| `JobImpl` | `Job()` | Plain job |

### Context Elements (IntelliJ-specific)

| Element | Meaning |
|---------|---------|
| `ComponentManager(ApplicationImpl@N)` | Application-level coroutine scope |
| `ComponentManager(ProjectImpl@N)` | Project-level coroutine scope |
| `Dispatchers.EDT` | Runs on EDT **with write-intent lock**. Use for model access (PSI, VFS, Documents). |
| `Dispatchers.UI` | Runs on EDT **without write-intent lock**. Use for pure UI work, no model access. |
| `Dispatchers.ui(RELAX)` | Runs on EDT without write-intent lock but **allows** initiating read/write actions. |
| `Dispatchers.Default` | Runs on the shared computation thread pool |
| `Dispatchers.IO` | Runs on the IO-optimized thread pool |
| `Dispatchers.IO.limitedParallelism(N)` | IO dispatcher limited to N concurrent coroutines |
| `Dispatchers.Unconfined` | Runs in the caller's thread until first suspension |
| `BlockingEventLoop` | Event loop for `runBlocking` |
| `ModalityState.NON_MODAL` | Non-modal context (normal state) |
| `ModalityState.ANY` | Coroutine can run in any modality state |
| `ComputationState(level=N,...)` | Lock infrastructure context. See "ComputationState" section below. **Warning**: presence alone does NOT mean a lock is held — see caveats. |
| `ClientId(value=Host)` | Code With Me client identification |
| `Kernel@...` | Fleet kernel context (present in modern IntelliJ) |
| `Rete(...)` | Fleet Rete (incremental computation) context |
| `kotlinx.coroutines.UndispatchedMarker` | Coroutine started undispatched |
| `CoroutineId(N)` | Unique coroutine identifier (when debug probes are enabled) |

### ComputationState: Lock Infrastructure Context

`ComputationState` in a coroutine's context carries the lock infrastructure used by read/write actions within that coroutine tree.

```
ComputationState(level=0, thisLevelLock=com.intellij.core.rwmutex.RWMutexIdeaImpl@ADDR, isParallelizedRead=false)
```

| Field | Meaning |
|-------|---------|
| `level=0` | Nesting depth of read/write actions (0 = outermost) |
| `thisLevelLock=RWMutexIdeaImpl@ADDR` | The RW mutex instance used for lock operations |
| `isParallelizedRead=false` | `false` = blocking read semantics. `true` = cancellable/parallelized read semantics. |

**CRITICAL CAVEAT — ComputationState does NOT prove a lock is held:**
- Every `runBlockingCancellable` call gets `ComputationState` from the default `zeroLevelComputationState` (`NestedLocksThreadingSupport.kt:208`). This means most coroutine trees created via `runBlockingCancellable` will show `ComputationState(level=0, ..., isParallelizedRead=false)` even when NO read action is active.
- `ComputationState` provides the lock infrastructure for potential future `readAction { }` / `writeAction { }` calls within the tree. It does NOT mean the lock is currently held.
- Child coroutines inherit the `ComputationState`, so its presence throughout a subtree is expected and normal.

**To find actual lock holders, you MUST check the thread dump:**
- Look for threads with `runReadAction`, `ReadAction.compute`, `runInReadActionWithWriteActionPriority`, or `readActionBlocking` in their stack that are NOT waiting at `acquireReadPermit` / `acquireReadActionPermit`. These threads are INSIDE a read action and hold the lock.
- A thread stuck inside a read action (e.g., at `Condition.await` or `Object.wait` above the `runReadAction` frame) is a lock holder that can't release.
- See "Procedure A" for the correct diagnostic flow.

### IntelliJ Scope Hierarchy

The coroutine dump shows IntelliJ's structured concurrency hierarchy:

```
BlockingCoroutine (main runBlocking)
  └── "Application":supervisor:ChildScope
       └── "ApplicationImpl@N container":supervisor:ChildScope
            ├── "(ApplicationImpl@N x com.intellij.java)":supervisor:ChildScope  [plugin scope]
            │    ├── "com.example.ServiceName":supervisor:ChildScope  [service scope]
            │    │    └── coroutines launched by the service
            │    └── "(ProjectImpl@N x ...)":supervisor:ChildScope  [project+plugin intersection]
            │         └── startup activities, project services
            ├── "(ApplicationImpl@N x com.intellij)":supervisor:ChildScope  [core plugin scope]
            └── ... more plugin scopes
```

**Name patterns:**
- `"ApplicationImpl@N container"` = Application-level container scope
- `"ProjectImpl@N container"` = Project-level container scope
- `"(ApplicationImpl@N x pluginId)"` = Intersection scope for plugin at app level
- `"(ProjectImpl@N x (ApplicationImpl@N x pluginId))"` = Intersection scope for plugin at project level
- `"com.example.ClassName"` = Service or component scope
- `"descriptive name"` = Named coroutine (via `CoroutineName`)

### Cross-Referencing Threads and Coroutines

Thread names in the thread dump encode the coroutine they're running:

```
"DefaultDispatcher-worker-8 @coroutineName#512083" #73 ...
 ^^^^^^^^^^^^^^^^^^^^^^^^    ^^^^^^^^^^^^^^^^^^^^^^^
 Thread pool worker name     Coroutine name and ID
```

The `@name#id` suffix matches the coroutine's `"name"` and `CoroutineId(N)` in the coroutine dump. Use this to:
1. Find which thread a RUNNING coroutine is on
2. Find which coroutine a blocked thread is executing
3. See the full JVM stack (thread dump) alongside the coroutine suspension stack (coroutine dump)

### Distinguishing Lock Holders vs Lock Waiters

This is the most critical diagnostic distinction. Lock holders must be found primarily in the **thread dump**, not the coroutine dump.

**Lock HOLDER** (already inside the action, holds the lock):
- **In the thread dump** (primary source): Thread has `runReadAction`, `ReadAction.compute`, `runInReadActionWithWriteActionPriority`, or `readActionBlocking` deeper in its stack, and is doing work or blocked on something ABOVE that frame (e.g., `Condition.await`, `Object.wait`, application code). The thread is past the lock acquisition point.
- **In the coroutine dump**: A RUNNING coroutine whose thread (found via `@name#id` cross-reference) is inside a read action as described above.
- These threads are **preventing** a pending write action from proceeding.
- **Do NOT rely on `ComputationState` alone** — it appears on most `runBlockingCancellable` coroutines as a default and does NOT mean a lock is held. See the ComputationState section.

**Lock WAITER** (trying to acquire, does not hold it):
- In the thread dump: blocked at the top of the stack at `acquireReadPermit` → `RunSuspend.await` → `Object.wait` (or `Unsafe.park`)
- In the coroutine dump: SUSPENDED at `acquireReadActionPermit` or `acquireReadPermit` or `InternalReadAction.readLoop`
- These are **victims** of the contention, not causes

## Step 3: Diagnostic Procedures

### Procedure A: Diagnosing a Freeze or Hang

1. **Check the EDT**: Look at `AWT-EventQueue-0`. If it's blocked, the UI is frozen. Note specifically whether it's blocked on `upgradeWritePermit`/`processWriteLockAcquisition` (pending write action) or `runBlocking`/`joinBlocking`.
2. **Find read lock HOLDERS in the thread dump**: Search for threads that have `runReadAction`, `ReadAction.compute`, `runInReadActionWithWriteActionPriority`, `readActionBlocking`, or `readActionUndispatched` in their stack AND are NOT at `acquireReadPermit`/`acquireReadActionPermit` at the top. These threads are INSIDE a read action and hold the lock. This is the most reliable way to find lock holders.
3. **Trace why lock holders can't finish**: For each thread holding a read lock, look at what it's actually doing:
   - `Condition.await()` / `LockSupport.park()` above a `runReadAction` frame = thread is stuck inside the read action waiting for something. Check if it's waiting for the EDT (e.g., `MixedResultsSearcher.addElement` backpressure waiting for `invokeLater` consumer).
   - `Object.wait()` on a monitor = thread is blocked on JVM-level synchronization inside the read action.
   - Active computation (e.g., `visitChildrenRecursively`) = thread is doing work, will finish eventually (not a deadlock, just slow).
4. **Cross-reference with coroutines**: If a lock-holding thread has `@name#id` in its name, find the matching coroutine in the coroutine dump to understand its place in the hierarchy.
5. **Find RUNNING coroutines**: `Grep` for `state: RUNNING` in the coroutine dump, then cross-reference with threads to see their full JVM stack.
6. **Check for `BlockingCoroutine` on EDT**: A `BlockingCoroutine` blocks a thread via `runBlocking`. If on the EDT, it's a direct freeze.
7. **Look for EDT-dispatched coroutines**: Search for `Dispatchers.EDT` or `Dispatchers.UI`. If the EDT is blocked, these can't run — and if anything inside a read action is waiting for them, you have a deadlock.

### Procedure B: Diagnosing a Deadlock

1. **Look for circular dependencies**: Thread A holding lock X waiting for lock Y, while Thread B holds lock Y waiting for lock X.
2. **Check for the read-action-needs-EDT deadlock** (most common): EDT wants a write lock (`upgradeWritePermit` / `processWriteLockAcquisition`), but a thread inside `runReadAction` (found via Procedure A step 2) is stuck at `Condition.await` or `LockSupport.park` waiting for the EDT to process results (e.g., `invokeLater` backpressure in `MixedResultsSearcher.addElement`). The EDT can't process because it's waiting for the write lock → circular deadlock.
3. **Look for `runBlocking` on the EDT**: EDT blocked on `runBlocking`/`joinBlocking` while a child coroutine needs `Dispatchers.EDT` = deadlock.
4. **Do NOT rely solely on `ComputationState`**: `ComputationState` in coroutine context is a default infrastructure element, not proof of lock holding. Always verify with thread dump stack analysis.
5. **Look for `invokeLater` backpressure**: Code inside a read action that produces results via `invokeLater` (to post to EDT) with a bounded buffer. If the buffer fills up and the producer blocks (e.g., `Condition.await`), it will never unblock because the EDT consumer can't run.

### Procedure C: Finding Suspicious Coroutines

1. **Large repetition counts**: `[xN of]` with high N values may indicate a leak or unbounded coroutine spawning.
2. **CREATED but not running**: Coroutines in `state: CREATED` that aren't starting may indicate a saturated dispatcher (especially `limitedParallelism`).
3. **RUNNING but actually waiting**: A coroutine with `state: RUNNING` but whose stack shows `Object.wait()`, `Unsafe.park()`, or `LockSupport.park*()` is technically running but actually blocked on a JVM-level lock/wait. This is the most interesting case.
4. **Modality mismatches**: Coroutines on `Dispatchers.EDT` or `Dispatchers.UI` with `ModalityState.NON_MODAL` will be paused if the IDE is showing a modal dialog. Check for `ModalityState.ANY` vs `ModalityState.NON_MODAL`.

### Procedure D: Counting and Summarizing

When asked to summarize a dump, provide:

1. **Total coroutine counts**: Use `Grep` to count occurrences of `state: SUSPENDED`, `state: RUNNING`, `state: CREATED`.
2. **Scope coroutines** (no state): Count lines matching `^\t*- ` that do NOT contain `state:` - these are structural scopes (`ChildScope`, `SupervisorJobImpl`, etc.).
3. **Top components**: Extract coroutine names to find which services/plugins have the most coroutines.
4. **Dispatcher distribution**: Count occurrences of each dispatcher type.
5. **Active work**: List all RUNNING coroutines and what they're doing.
6. **Potential issues**: Flag any patterns from Procedure C.

## Step 4: Useful Grep Patterns

Use these patterns on the dump file to quickly extract information:

```bash
# Find the coroutine dump separator
Grep: "Coroutine dump"

# All RUNNING coroutines (actively executing - most important for freeze diagnosis)
Grep: "state: RUNNING"

# All SUSPENDED coroutines with stack traces (look for what they're waiting on)
Grep: "state: SUSPENDED"

# All CREATED coroutines (queued, not yet started)
Grep: "state: CREATED"

# BlockingCoroutine instances (runBlocking - can block threads)
Grep: "BlockingCoroutine"

# Read/write lock contention (top cause of freezes)
Grep: "acquireReadPermit|acquireWriteIntentPermit|acquireWriteActionPermit|RWMutex"

# EDT/UI dispatcher coroutines (need the event dispatch thread)
Grep: "Dispatchers\.(EDT|UI|ui)"

# Repeated/multiplied coroutines (potential leaks)
Grep: "\\[x\\d+ of\\]"

# Specific service or plugin
Grep: "com.intellij.yourpackage"

# Coroutines waiting on specific suspension points
Grep: "awaitCancellation|SharedFlowImpl.collect|StateFlowImpl.collect|BufferedChannel"

# Coroutines inside read/write actions
Grep: "ComputationState"

# Modal state issues
Grep: "ModalityState"

# Top-level coroutines (root scopes, at indentation level 0)
Grep: "^- "

# Thread dump: EDT state
Grep: "AWT-EventQueue"

# Thread dump: DefaultDispatcher workers
Grep: "DefaultDispatcher-worker"

# Cross-reference: find thread running a specific coroutine ID
Grep: "@.*#512083"

# Thread dump: threads trying to acquire read locks (waiters, NOT holders)
Grep: "acquireReadPermit|acquireReadActionPermit"

# Thread dump: threads trying to acquire write locks
Grep: "upgradeWritePermit|processWriteLockAcquisition|acquireWriteActionPermit"

# Coroutine dump: lock holders (have ComputationState in context, not stack)
Grep: "ComputationState"

# Thread dump: non-suspending runReadAction calls from coroutines (anti-pattern)
Grep: "runReadAction|ReadAction.compute"

# Backpressure waits (Condition.await inside read actions = potential deadlock)
Grep: "Condition.await|addElement.*MixedResultsSearcher"
```

## Step 5: Presenting Findings

When presenting analysis to the user:

1. **Start with the summary**: How many coroutines total, how many in each state.
2. **Highlight the critical findings first**: RUNNING coroutines, lock contention, deadlocks.
3. **Show the coroutine hierarchy path**: For any suspicious coroutine, show its full ancestry chain (parent scopes up to the root).
4. **Include relevant stack frames**: Only the application-specific frames, not the kotlinx.coroutines internals.
5. **Cross-reference with threads**: If a RUNNING coroutine matches a thread, show both together.
6. **Provide actionable conclusions**: "Service X is blocked waiting for a read lock held by Y" rather than just listing frames.

## Common IntelliJ-Specific Patterns

### Pattern: `runBlocking` on EDT
```
"AWT-EventQueue-0" ... TIMED_WAITING (parking)
  at kotlinx.coroutines.BlockingCoroutine.joinBlocking(...)
```
**Diagnosis**: Code is calling `runBlocking` on the EDT, which freezes the UI until the coroutine completes. Look at what the blocking coroutine's children are doing.

### Pattern: Read Action in Coroutine Blocking on Write Lock
```
state: RUNNING
  at com.intellij.core.rwmutex.RWMutexIdeaImpl.acquireReadActionPermit(...)
```
**Diagnosis**: A coroutine is trying to acquire a read lock, but a write action is pending. Check the EDT for `acquireWriteIntentPermit`.

### Pattern: Saturated Limited Dispatcher
```
- "Pool":DeferredCoroutine{Active}, state: SUSPENDED [Dispatchers.IO.limitedParallelism(1)]
- "Pool":DeferredCoroutine{Active}, state: CREATED [Dispatchers.IO.limitedParallelism(1)]
- "Pool":DeferredCoroutine{Active}, state: CREATED [Dispatchers.IO.limitedParallelism(1)]
```
**Diagnosis**: One coroutine is executing (SUSPENDED at work) while others are CREATED (queued). The limited parallelism is the bottleneck.

### Pattern: Flow Collection Suspension (Normal)
```
state: SUSPENDED
  at kotlinx.coroutines.flow.SharedFlowImpl.collect$suspendImpl(SharedFlow.kt)
  at com.example.MyService$1.invokeSuspend(MyService.kt:42)
```
**Diagnosis**: Normal - coroutine is waiting for a Flow to emit a value. Not a problem unless it should have received a value by now.

### Pattern: `awaitCancellation` (Normal)
```
state: SUSPENDED
  at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt)
```
**Diagnosis**: Normal - coroutine intentionally suspends forever, waiting for cancellation. Common pattern for registering cleanup/disposal actions.

### Pattern: Compensated Parallelism
```
at kotlinx.coroutines.scheduling.ParallelismCompensationKt.withCompensatedParallelismAfterDeadline
at kotlinx.coroutines.internal.intellij.IntellijCoroutines.runAndCompensateParallelism
```
**Diagnosis**: IntelliJ's mechanism to add more threads to the coroutine scheduler when a coroutine blocks. Appears in thread dumps when a dispatcher thread is blocked inside a bridged (non-suspending) call.

### Pattern: Blocking Read Action Holding Lock While Needing EDT (Deadlock)
```
# Coroutine dump:
- BlockingCoroutine{Completing} [ComputationState(level=0,...,isParallelizedRead=false), BlockingEventLoop]
    - SomeCoroutine{Active}, state: RUNNING [ComputationState(...)]
        at Condition.await(...)           # waiting for EDT consumer
        at SomeAccumulator.addElement(...)

# Thread dump:
"AWT-EventQueue-0" ... TIMED_WAITING
    at NestedLocksThreadingSupport$ComputationState.upgradeWritePermit(...)
    at NestedLocksThreadingSupport.processWriteLockAcquisition(...)
    at NestedLocksThreadingSupport.runWriteAction(...)
```
**Diagnosis**: A `BlockingCoroutine` with `ComputationState(isParallelizedRead=false)` holds a blocking read lock. Inside it, a RUNNING coroutine is blocked on a JVM `Condition.await()` waiting for a consumer that runs on the EDT (typically via `invokeLater`). Meanwhile the EDT is trying to acquire a write lock, which can't be granted until this read action completes. Classic circular deadlock. The `BlockingCoroutine` is the key -- it bridges coroutines to blocking code and holds the read lock for its entire subtree's lifetime.

### Pattern: `edtWriteAction` Blocking EDT on Lock Upgrade
```
# Coroutine dump:
- "name":StandaloneCoroutine{Active}, state: RUNNING [Dispatchers.EDT]
    at NestedLocksThreadingSupport$ComputationState.upgradeWritePermit(...)
    at NestedLocksThreadingSupport.runWriteAction(...)
    at ApplicationImpl.runWriteAction(...)
    at CoroutinesKt$edtWriteAction$2.invokeSuspend(coroutines.kt)
    at SomePlugin.someMethod(...)
```
**Diagnosis**: Code called `edtWriteAction { }` which dispatches to the EDT and then calls the blocking `runWriteAction`. The EDT is now stuck waiting for all readers to finish. This is freeze-prone -- prefer `backgroundWriteAction { }` (acquires write lock suspending on background thread) or `readAndEdtWriteAction { }` / `readAndBackgroundWriteAction { }` (atomic read-then-write pattern).

### Pattern: Non-Suspending `runReadAction` Inside Coroutine Blocking on Write
```
# Thread dump:
"DefaultDispatcher-worker-N @coroutineName#ID" ... WAITING (on object monitor)
    at Object.wait(...)
    at RunSuspend.waitForDeferredWithoutInterceptor(...)
    at NestedLocksThreadingSupport$ComputationState.acquireReadPermit(...)
    at NestedLocksThreadingSupport.runReadAction(...)
    at ApplicationImpl.runReadAction(...)
    at SomeService.someMethod(...)  # <-- the offending call

# Coroutine dump:
- "coroutineName":StandaloneCoroutine{Active}, state: RUNNING [Dispatchers.Default]
    at Object.wait(...)
    at RunSuspend.await(...)
    at NestedLocksThreadingSupport$ComputationState.acquireReadPermit(...)
    at ...SomeService.someMethod(...)
```
**Diagnosis**: A coroutine on a background dispatcher called the non-suspending `runReadAction { }` (or `ReadAction.compute { }`), which blocks the dispatcher thread while waiting for the read lock. This thread is wasted -- it can't do other work. The coroutine should use the suspending `readAction { }` instead, which suspends the coroutine (freeing the thread) and automatically cancels + retries if a write action arrives.

## Read/Write Action Best Practices Reference

When suggesting fixes for lock-related issues found in dumps, refer to the suspending alternatives in `platform/core-api/src/com/intellij/openapi/application/coroutines.kt`:

### Read Actions

| Blocking (avoid in coroutines) | Suspending (prefer) | Behavior |
|-------------------------------|---------------------|----------|
| `ApplicationManager.getApplication().runReadAction { }` | `readAction { }` | Cancels + retries on write. Action must be idempotent. |
| `ReadAction.compute { }` / `ReadAction.run { }` | `readAction { }` | Same as above. |
| (no equivalent) | `readActionBlocking { }` | Suspends to acquire, then blocks writes until done. Use only when cancellation is unacceptable. |
| (no equivalent) | `smartReadAction(project) { }` | Like `readAction` but also waits for smart mode. |

### Write Actions

| Blocking (freeze-prone) | Suspending (prefer) | Behavior |
|-------------------------|---------------------|----------|
| `edtWriteAction { }` | `backgroundWriteAction { }` | Acquires write lock suspending on background thread. Never touches EDT. |
| `edtWriteAction { }` | `readAndEdtWriteAction { }` | Atomic read-then-write. Guarantees no other write between read and write. Write runs on EDT. |
| `edtWriteAction { }` | `readAndBackgroundWriteAction { }` | Same pattern, write runs on background thread. |

### Key Rules
- **Never call blocking `runReadAction` from a coroutine** -- use `readAction { }` instead. The blocking version wastes a dispatcher thread and can't cancel when a write arrives.
- **`readAction { }` (WARA) is cancellable** -- the action may be restarted if a write action arrives. The action **must be idempotent**.
- **`readActionBlocking { }` (WBRA) blocks writes** -- use only when you can't tolerate restarts (e.g., long-running computations where restart cost is too high).
- **`edtWriteAction { }` blocks the EDT** waiting for readers to finish. Prefer `backgroundWriteAction { }` or `readAndBackgroundWriteAction { }`.
- **No suspension inside read/write action blocks** -- `readAction { }` and `writeAction { }` blocks must not call `withContext()`, `delay()`, or other suspending functions. The lock is held for the entire block.
- **`Dispatchers.EDT` acquires write-intent lock** automatically. `Dispatchers.UI` does not. Use `Dispatchers.EDT` for model access, `Dispatchers.UI` for pure UI.
