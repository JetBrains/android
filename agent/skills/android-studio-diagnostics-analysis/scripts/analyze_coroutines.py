#!/usr/bin/env python3
"""
IntelliJ Thread + Coroutine Dump Analyzer.

Parses IntelliJ thread dumps (which include a coroutine dump section) and
provides structured queries for diagnosing freezes, deadlocks, and hangs.

Usage:
    python3 scripts/analyze_coroutines.py <dump_file> <command> [args...]

Commands:
    summary              Overview stats: coroutine counts, dispatcher distribution, etc.
    running              List all RUNNING coroutines with their thread cross-references.
    lock-holders         Find coroutines that hold read/write locks (have ComputationState in context).
    lock-waiters         Find coroutines/threads waiting to acquire locks.
    deadlock             Detect potential deadlock cycles (EDT ↔ read/write lock contention).
    edt                  Show EDT thread state and all Dispatchers.EDT coroutines.
    tree <pattern>       Show the full ancestry tree of coroutines matching a name/ID pattern.
    search <pattern>     Search coroutines by name, class, or stack frame content.
    threads              Cross-reference all threads with their coroutines.
"""

import sys
import re
from dataclasses import dataclass, field
from collections import Counter


@dataclass
class ThreadInfo:
    name: str
    raw_name: str  # full quoted name including @coroutine suffix
    state: str
    stack: list[str] = field(default_factory=list)
    coroutine_ref: str = ""  # @name#id if present
    line_number: int = 0

    @property
    def is_edt(self) -> bool:
        return "AWT-EventQueue" in self.name

    @property
    def is_dispatcher_worker(self) -> bool:
        return "DefaultDispatcher-worker" in self.name


@dataclass
class CoroutineInfo:
    header: str
    name: str
    coroutine_class: str
    job_state: str
    exec_state: str  # SUSPENDED, RUNNING, CREATED, or "" for scopes
    context_str: str
    depth: int  # indentation depth (0 = top-level)
    stack: list[str] = field(default_factory=list)
    children: list["CoroutineInfo"] = field(default_factory=list)
    parent: "CoroutineInfo | None" = None
    repeat_count: int = 1  # from [xN of]
    line_number: int = 0
    is_supervisor: bool = False
    coroutine_id: str = ""  # from CoroutineId(N) in context

    @property
    def dispatchers(self) -> list[str]:
        return re.findall(r'Dispatchers\.\S+', self.context_str)

    @property
    def has_computation_state(self) -> bool:
        return "ComputationState" in self.context_str

    @property
    def computation_state(self) -> str:
        m = re.search(r'ComputationState\([^)]+\)', self.context_str)
        return m.group(0) if m else ""

    @property
    def is_parallelized_read(self) -> bool:
        return "isParallelizedRead=true" in self.context_str

    @property
    def is_blocking_read(self) -> bool:
        return self.has_computation_state and not self.is_parallelized_read

    @property
    def component_manager(self) -> str:
        m = re.search(r'ComponentManager\([^)]+\)', self.context_str)
        return m.group(0) if m else ""

    @property
    def modality_state(self) -> str:
        m = re.search(r'ModalityState\.\S+', self.context_str)
        return m.group(0) if m else ""

    @property
    def display_name(self) -> str:
        parts = []
        if self.name:
            parts.append(f'"{self.name}"')
        parts.append(f"{self.coroutine_class}{{{self.job_state}}}")
        if self.exec_state:
            parts.append(f"state: {self.exec_state}")
        if self.coroutine_id:
            parts.append(f"#{self.coroutine_id}")
        return ", ".join(parts)

    def ancestry_chain(self) -> list["CoroutineInfo"]:
        chain = []
        node = self
        while node:
            chain.append(node)
            node = node.parent
        chain.reverse()
        return chain

    def all_descendants(self) -> list["CoroutineInfo"]:
        result = []
        for child in self.children:
            result.append(child)
            result.extend(child.all_descendants())
        return result


def parse_thread_dump(lines: list[str], end_line: int) -> list[ThreadInfo]:
    threads = []
    current = None
    for i, line in enumerate(lines[:end_line]):
        # Thread header: "name" #N [nid] ...
        m = re.match(r'^"(.+?)"\s+#\d+', line)
        if m:
            if current:
                threads.append(current)
            raw_name = m.group(1)
            # Extract coroutine reference from thread name
            cr = re.search(r'@(.+)$', raw_name)
            coroutine_ref = cr.group(1) if cr else ""
            base_name = raw_name.split(" @")[0] if cr else raw_name
            current = ThreadInfo(
                name=base_name,
                raw_name=raw_name,
                state="",
                coroutine_ref=coroutine_ref,
                line_number=i + 1,
            )
            continue
        if current:
            state_m = re.match(r'\s+java\.lang\.Thread\.State:\s+(.+)', line)
            if state_m:
                current.state = state_m.group(1)
            elif line.startswith('\tat ') or line.startswith('\t- '):
                current.stack.append(line.strip())
    if current:
        threads.append(current)
    return threads


def parse_coroutine_dump(lines: list[str], start_line: int) -> list[CoroutineInfo]:
    """Parse the coroutine dump section into a forest of CoroutineInfo trees."""
    coroutine_lines = lines[start_line:]
    roots = []
    stack_by_depth: dict[int, CoroutineInfo] = {}  # depth -> most recent coroutine at that depth

    for i, line in enumerate(coroutine_lines):
        line_num = start_line + i + 1
        # Match coroutine header: optional tabs, then "- "
        m = re.match(r'^(\t*)-\s*(.+)', line)
        if m:
            depth = len(m.group(1))
            header = m.group(2)
            info = parse_coroutine_header(header, depth, line_num)
            stack_by_depth[depth] = info

            if depth == 0:
                roots.append(info)
            else:
                # Find parent: nearest coroutine at depth-1
                parent = stack_by_depth.get(depth - 1)
                if parent:
                    parent.children.append(info)
                    info.parent = parent
                else:
                    roots.append(info)
            continue

        # Stack trace line
        stack_m = re.match(r'^(\t+)at\s+(.+)', line)
        if stack_m:
            depth = len(stack_m.group(1))
            # Find the coroutine this stack belongs to
            # It belongs to the most recent coroutine at depth-1 (since stack is indented one more than the "- " line)
            owner = stack_by_depth.get(depth - 1)
            if owner:
                owner.stack.append(stack_m.group(2))

    return roots


# Regex for coroutine header parsing
HEADER_RE = re.compile(
    r'(?:\[x(\d+)\s+of\]\s+)?'  # optional [xN of]
    r'(?:"([^"]*)")?'  # optional "name"
    r':?'
    r'((?:supervisor:)?)'  # optional supervisor:
    r'(\w+)'  # CoroutineClass
    r'\{(\w+)\}'  # {JobState}
    r'(?:@\w+)?'  # optional @hashcode
    r'(?:,\s*state:\s*(\w+))?'  # optional , state: STATE
    r'(?:\s+\[(.+)\])?'  # optional [context]
)


def parse_coroutine_header(header: str, depth: int, line_number: int) -> CoroutineInfo:
    m = HEADER_RE.match(header)
    if not m:
        return CoroutineInfo(
            header=header, name="", coroutine_class="Unknown",
            job_state="?", exec_state="", context_str=header,
            depth=depth, line_number=line_number,
        )

    repeat_count = int(m.group(1)) if m.group(1) else 1
    name = m.group(2) or ""
    is_supervisor = bool(m.group(3))
    coroutine_class = m.group(4)
    job_state = m.group(5)
    exec_state = m.group(6) or ""
    context_str = m.group(7) or ""

    # Extract CoroutineId
    cid_m = re.search(r'CoroutineId\((\d+)\)', context_str)
    coroutine_id = cid_m.group(1) if cid_m else ""

    return CoroutineInfo(
        header=header, name=name, coroutine_class=coroutine_class,
        job_state=job_state, exec_state=exec_state, context_str=context_str,
        depth=depth, repeat_count=repeat_count, line_number=line_number,
        is_supervisor=is_supervisor, coroutine_id=coroutine_id,
    )


def all_coroutines(roots: list[CoroutineInfo]) -> list[CoroutineInfo]:
    result = []
    for r in roots:
        result.append(r)
        result.extend(r.all_descendants())
    return result


def find_separator(lines: list[str]) -> int:
    for i, line in enumerate(lines):
        if "Coroutine dump" in line and "---" in line:
            return i
    return -1


# ─── Commands ────────────────────────────────────────────────────────────────


def cmd_summary(threads: list[ThreadInfo], roots: list[CoroutineInfo]):
    coroutines = all_coroutines(roots)

    states = Counter(c.exec_state for c in coroutines if c.exec_state)
    classes = Counter(c.coroutine_class for c in coroutines)

    dispatchers: Counter[str] = Counter()
    for c in coroutines:
        for d in c.dispatchers:
            dispatchers[d] += 1

    scope_count = sum(1 for c in coroutines if not c.exec_state)
    total = len(coroutines)

    print("=== DUMP SUMMARY ===\n")
    print(f"Threads: {len(threads)}")
    print(f"Coroutines: {total} ({scope_count} scopes, {total - scope_count} executable)\n")

    print("States:")
    for state, count in states.most_common():
        print(f"  {state}: {count}")

    print(f"\nCoroutine Types:")
    for cls, count in classes.most_common(10):
        print(f"  {cls}: {count}")

    print(f"\nDispatchers:")
    for disp, count in dispatchers.most_common():
        print(f"  {disp}: {count}")

    # Lock holders (from thread dump - actually holding locks)
    thread_holders = find_thread_read_lock_holders(threads)
    if thread_holders:
        print(f"\nRead lock holders (from thread stacks): {len(thread_holders)} thread(s)")
        for h in thread_holders:
            print(f"  \"{h.thread.raw_name}\" (line {h.thread.line_number})")
            if h.blocking_frame:
                print(f"    Stuck at: {h.blocking_frame}")
            print(f"    Inside: {h.read_action_frame}")

    # EDT state
    edt_threads = [t for t in threads if t.is_edt]
    if edt_threads:
        edt = edt_threads[0]
        print(f"\nEDT state: {edt.state}")
        if edt.coroutine_ref:
            print(f"  Running coroutine: @{edt.coroutine_ref}")

    # RUNNING coroutines
    running = [c for c in coroutines if c.exec_state == "RUNNING"]
    if running:
        print(f"\nRUNNING coroutines: {running_count(running)}")
        for c in running:
            thread = find_thread_for_coroutine(c, threads)
            thread_info = f" on {thread.name}" if thread else ""
            print(f"  Line {c.line_number}: {c.display_name}{thread_info}")

    # CREATED coroutines
    created = [c for c in coroutines if c.exec_state == "CREATED"]
    if created:
        print(f"\nCREATED (queued) coroutines: {len(created)}")
        for c in created:
            print(f"  Line {c.line_number}: {c.display_name}")


def running_count(running):
    return len(running)


def cmd_running(threads: list[ThreadInfo], roots: list[CoroutineInfo]):
    coroutines = all_coroutines(roots)
    running = [c for c in coroutines if c.exec_state == "RUNNING"]

    if not running:
        print("No RUNNING coroutines found.")
        return

    print(f"=== {len(running)} RUNNING COROUTINES ===\n")
    for c in running:
        thread = find_thread_for_coroutine(c, threads)
        print(f"--- Line {c.line_number}: {c.display_name} ---")
        if thread:
            print(f"Thread: \"{thread.raw_name}\" (line {thread.line_number})")
            print(f"Thread state: {thread.state}")
        else:
            print("Thread: (not found in thread dump)")

        # Print ancestry
        chain = c.ancestry_chain()
        if len(chain) > 1:
            print("Ancestry:")
            for ancestor in chain[:-1]:
                indent = "  " * ancestor.depth
                print(f"  {indent}{ancestor.display_name}")

        # Dispatchers/context
        if c.dispatchers:
            print(f"Dispatcher: {', '.join(c.dispatchers)}")
        if c.has_computation_state:
            print(f"Lock: {c.computation_state} (HOLDS {'blocking' if c.is_blocking_read else 'cancellable'} read lock)")

        # Stack
        if c.stack:
            print("Coroutine stack:")
            for frame in c.stack:
                print(f"  at {frame}")
        if thread and thread.stack:
            # Show first few non-coroutine-internal frames
            app_frames = [f for f in thread.stack if not is_internal_frame(f)]
            if app_frames:
                print("Key thread stack frames:")
                for frame in app_frames[:10]:
                    print(f"  {frame}")
        print()


def cmd_lock_holders(threads: list[ThreadInfo], roots: list[CoroutineInfo]):
    coroutines = all_coroutines(roots)

    # Primary: thread-level read lock holders (actually holding locks)
    thread_holders = find_thread_read_lock_holders(threads)

    if not thread_holders:
        print("No read lock holders found in thread stacks.")
        print("(Threads inside runReadAction/readActionBlocking that are not waiting to acquire)")
        return

    print(f"=== {len(thread_holders)} THREAD(S) HOLDING READ LOCKS ===\n")
    for h in thread_holders:
        print(f"--- \"{h.thread.raw_name}\" (line {h.thread.line_number}) ---")
        print(f"State: {h.thread.state}")
        print(f"Read action: {h.read_action_frame}")
        if h.blocking_frame:
            print(f"Stuck at: {h.blocking_frame}")

        # Show the caller of the read action (what code initiated it)
        caller_found = False
        for idx in range(h.read_action_idx + 1, len(h.thread.stack)):
            frame = h.thread.stack[idx]
            if not is_internal_frame("at " + frame) and not any(ra in frame for ra in READ_ACTION_FRAMES):
                print(f"Called from: {frame}")
                caller_found = True
                break

        # Show what the thread is doing inside the read action
        print("Stack inside read action:")
        for idx in range(0, min(h.read_action_idx, 10)):
            frame = h.thread.stack[idx]
            if not is_internal_frame("at " + frame):
                print(f"  {frame}")

        # Cross-reference with coroutine
        if h.thread.coroutine_ref:
            print(f"Coroutine: @{h.thread.coroutine_ref}")

        print()


def cmd_lock_waiters(threads: list[ThreadInfo], roots: list[CoroutineInfo]):
    coroutines = all_coroutines(roots)

    # Coroutine-level waiters: SUSPENDED at acquireReadPermit/acquireReadActionPermit
    cr_waiters = []
    for c in coroutines:
        if c.exec_state in ("SUSPENDED", "RUNNING"):
            for frame in c.stack:
                if "acquireReadPermit" in frame or "acquireReadActionPermit" in frame or "acquireWriteActionPermit" in frame or "upgradeWritePermit" in frame:
                    cr_waiters.append((c, frame))
                    break

    # Thread-level waiters
    th_waiters = []
    for t in threads:
        for frame in t.stack:
            if "acquireReadPermit" in frame or "acquireReadActionPermit" in frame or "acquireWriteActionPermit" in frame or "upgradeWritePermit" in frame:
                th_waiters.append((t, frame))
                break

    print(f"=== LOCK WAITERS ===\n")
    if cr_waiters:
        print(f"Coroutines waiting for locks ({len(cr_waiters)}):")
        for c, frame in cr_waiters:
            lock_type = "write" if "Write" in frame or "upgrade" in frame else "read"
            print(f"  Line {c.line_number}: {c.display_name}")
            print(f"    Waiting for: {lock_type} lock")
            print(f"    at {frame}")
            print()

    if th_waiters:
        print(f"Threads waiting for locks ({len(th_waiters)}):")
        for t, frame in th_waiters:
            lock_type = "write" if "Write" in frame or "upgrade" in frame else "read"
            print(f"  \"{t.raw_name}\" (line {t.line_number})")
            print(f"    State: {t.state}")
            print(f"    Waiting for: {lock_type} lock")
            print(f"    {frame}")
            if t.coroutine_ref:
                print(f"    Coroutine: @{t.coroutine_ref}")
            print()

    if not cr_waiters and not th_waiters:
        print("No lock waiters found.")


def cmd_deadlock(threads: list[ThreadInfo], roots: list[CoroutineInfo]):
    coroutines = all_coroutines(roots)

    print("=== DEADLOCK ANALYSIS ===\n")
    issues_found = 0

    # Check 1: EDT waiting for write lock
    edt_threads = [t for t in threads if t.is_edt]
    edt_wants_write = False
    edt_wants_write_type = ""
    for edt in edt_threads:
        for frame in edt.stack:
            if "upgradeWritePermit" in frame or "processWriteLockAcquisition" in frame:
                edt_wants_write = True
                edt_wants_write_type = "write lock upgrade"
                break
            if "runWriteAction" in frame and "acquireWriteActionPermit" not in " ".join(edt.stack):
                edt_wants_write = True
                edt_wants_write_type = "write action"
                break

    if edt_wants_write:
        print(f"[!] EDT is BLOCKED waiting for {edt_wants_write_type}")
        edt = edt_threads[0]
        if edt.coroutine_ref:
            print(f"    Running coroutine: @{edt.coroutine_ref}")

        # Find actual read lock holders from thread stacks
        thread_holders = find_thread_read_lock_holders(threads)
        if thread_holders:
            print(f"    Blocked by {len(thread_holders)} thread(s) holding read locks:")
            deadlock_holders = []
            for h in thread_holders:
                print(f"      \"{h.thread.raw_name}\" (line {h.thread.line_number})")
                print(f"        Inside: {h.read_action_frame}")

                # Check if this holder is stuck waiting for EDT
                needs_edt = False
                reason = ""
                for frame in h.thread.stack[:h.read_action_idx]:
                    if "invokeLater" in frame:
                        needs_edt = True
                        reason = "invokeLater (posting to EDT event queue)"
                    elif "Condition.await" in frame or "ConditionObject.await" in frame:
                        needs_edt = True
                        reason = "Condition.await (waiting for EDT consumer)"
                    elif "EventStealer" in frame:
                        needs_edt = True
                        reason = "EventStealer (SuvorovProgress EDT interaction)"
                    elif "addElement" in frame and any(
                        "LockSupport.park" in f or "Unsafe.park" in f for f in h.thread.stack
                    ):
                        needs_edt = True
                        reason = "backpressure wait in addElement (needs EDT to drain)"
                if needs_edt:
                    print(f"        ** STUCK: {reason}")
                    if h.blocking_frame:
                        print(f"        at {h.blocking_frame}")
                    deadlock_holders.append(h)
                    issues_found += 1

            if deadlock_holders:
                print()
                print(f"    ** CIRCULAR DEADLOCK DETECTED **")
                for dh in deadlock_holders:
                    print(f"    EDT -> wants write lock")
                    print(f"    \"{dh.thread.name}\" -> holds read lock (inside {dh.read_action_frame})")
                    print(f"    \"{dh.thread.name}\" -> stuck waiting for EDT to process results")
                    print(f"    EDT -> frozen (cycle)")
            issues_found += 1
        print()

    # Check 2: EDT blocked on runBlocking with children needing EDT
    for edt in edt_threads:
        for frame in edt.stack:
            if "BlockingCoroutine" in frame or "joinBlocking" in frame:
                print(f"[!] EDT is blocked in runBlocking")
                # Check if any child coroutine needs Dispatchers.EDT
                edt_coroutines = [c for c in coroutines
                                  if "Dispatchers.EDT" in c.context_str
                                  and c.exec_state in ("SUSPENDED", "CREATED")]
                if edt_coroutines:
                    print(f"    {len(edt_coroutines)} coroutine(s) need Dispatchers.EDT but can't run:")
                    for c in edt_coroutines[:5]:
                        print(f"      Line {c.line_number}: {c.display_name}")
                    issues_found += 1
                break

    # Check 3: Non-suspending runReadAction from coroutines
    bad_reads = []
    for t in threads:
        if not t.is_dispatcher_worker:
            continue
        for frame in t.stack:
            if ("runReadAction" in frame or "ReadAction.compute" in frame) and t.coroutine_ref:
                # Check if it's waiting (not actually executing the action body)
                is_waiting = any("acquireReadPermit" in f or "Object.wait" in f or "Unsafe.park" in f for f in t.stack)
                if is_waiting:
                    bad_reads.append(t)
                    break

    if bad_reads:
        print(f"[!] {len(bad_reads)} dispatcher thread(s) blocked on non-suspending runReadAction:")
        for t in bad_reads:
            print(f"    \"{t.raw_name}\" (line {t.line_number})")
            # Find the caller: first app frame after the last runReadAction/lock frame
            last_read_idx = -1
            for idx, frame in enumerate(t.stack):
                if "runReadAction" in frame or "ReadAction.compute" in frame or "acquireReadPermit" in frame:
                    last_read_idx = idx
            if last_read_idx >= 0:
                for frame in t.stack[last_read_idx + 1:]:
                    if not is_internal_frame("at " + frame) and "ReadAction" not in frame and "runReadAction" not in frame:
                        print(f"      Called from: {frame}")
                        break
        print(f"    Fix: Replace runReadAction with suspending readAction {{ }}")
        print()
        issues_found += 1

    # Check 4: CREATED coroutines on Dispatchers.EDT (starved)
    starved_edt = [c for c in coroutines
                   if c.exec_state == "CREATED"
                   and ("Dispatchers.EDT" in c.context_str or "Dispatchers.UI" in c.context_str)]
    if starved_edt and edt_wants_write:
        print(f"[!] {len(starved_edt)} coroutine(s) queued on EDT that can't run:")
        for c in starved_edt:
            print(f"    Line {c.line_number}: {c.display_name}")
        print()
        issues_found += 1

    if issues_found == 0:
        print("No obvious deadlock patterns detected.")
    else:
        print(f"Total issues found: {issues_found}")


def cmd_edt(threads: list[ThreadInfo], roots: list[CoroutineInfo]):
    coroutines = all_coroutines(roots)

    print("=== EDT ANALYSIS ===\n")

    # EDT thread state
    edt_threads = [t for t in threads if t.is_edt]
    if not edt_threads:
        print("EDT thread not found in dump.")
        return

    edt = edt_threads[0]
    print(f"EDT Thread: \"{edt.raw_name}\"")
    print(f"State: {edt.state}")
    if edt.coroutine_ref:
        print(f"Running coroutine: @{edt.coroutine_ref}")
    print()

    if edt.stack:
        app_frames = [f for f in edt.stack if not is_internal_frame(f)]
        print("Key stack frames:")
        for frame in (app_frames or edt.stack)[:15]:
            print(f"  {frame}")
        print()

    # All EDT/UI coroutines
    edt_coroutines = [c for c in coroutines
                      if "Dispatchers.EDT" in c.context_str
                      or "Dispatchers.UI" in c.context_str
                      or "Dispatchers.ui(" in c.context_str]

    if edt_coroutines:
        by_state = Counter(c.exec_state or "scope" for c in edt_coroutines)
        print(f"EDT/UI coroutines: {len(edt_coroutines)} ({dict(by_state)})")
        for c in edt_coroutines:
            if c.exec_state:  # skip scopes
                print(f"  [{c.exec_state}] Line {c.line_number}: {c.display_name}")


def cmd_tree(threads: list[ThreadInfo], roots: list[CoroutineInfo], pattern: str):
    coroutines = all_coroutines(roots)
    pat = re.compile(pattern, re.IGNORECASE)

    matches = [c for c in coroutines
               if pat.search(c.name) or pat.search(c.coroutine_id) or pat.search(c.header)]

    if not matches:
        print(f"No coroutines matching '{pattern}' found.")
        return

    print(f"=== {len(matches)} COROUTINE(S) MATCHING '{pattern}' ===\n")
    for c in matches:
        chain = c.ancestry_chain()
        print(f"--- Line {c.line_number}: {c.display_name} ---")
        print("Ancestry:")
        for ancestor in chain:
            indent = "  " * ancestor.depth
            marker = " <-- MATCH" if ancestor is c else ""
            state_info = f" [{ancestor.exec_state}]" if ancestor.exec_state else ""
            lock_info = " [HOLDS LOCK]" if ancestor.is_blocking_read and (not ancestor.parent or not ancestor.parent.has_computation_state) else ""
            print(f"  {indent}{ancestor.display_name}{state_info}{lock_info}{marker}")
        if c.stack:
            print("Stack:")
            for frame in c.stack:
                print(f"  at {frame}")
        if c.children:
            print(f"Children: {len(c.children)}")
            for child in c.children[:10]:
                state_info = f" [{child.exec_state}]" if child.exec_state else ""
                print(f"  - {child.display_name}{state_info}")
            if len(c.children) > 10:
                print(f"  ... and {len(c.children) - 10} more")
        print()


def cmd_search(threads: list[ThreadInfo], roots: list[CoroutineInfo], pattern: str):
    coroutines = all_coroutines(roots)
    pat = re.compile(pattern, re.IGNORECASE)

    matches = []
    for c in coroutines:
        if pat.search(c.name) or pat.search(c.header) or pat.search(c.coroutine_id):
            matches.append(c)
            continue
        for frame in c.stack:
            if pat.search(frame):
                matches.append(c)
                break

    if not matches:
        print(f"No coroutines matching '{pattern}'.")
        return

    print(f"=== {len(matches)} COROUTINE(S) MATCHING '{pattern}' ===\n")
    for c in matches:
        state_info = f" [{c.exec_state}]" if c.exec_state else ""
        disp = f" {', '.join(c.dispatchers)}" if c.dispatchers else ""
        print(f"  Line {c.line_number}: {c.display_name}{state_info}{disp}")
        # Show matching stack frame
        for frame in c.stack:
            if pat.search(frame):
                print(f"    at {frame}")
                break


def cmd_threads(threads: list[ThreadInfo], roots: list[CoroutineInfo]):
    coroutines = all_coroutines(roots)

    # Build coroutine lookup by ID
    by_id: dict[str, CoroutineInfo] = {}
    for c in coroutines:
        if c.coroutine_id:
            by_id[c.coroutine_id] = c

    print("=== THREAD ↔ COROUTINE CROSS-REFERENCE ===\n")

    matched = []
    unmatched_threads = []
    for t in threads:
        if t.coroutine_ref:
            # Extract ID from @name#ID
            id_m = re.search(r'#(\d+)$', t.coroutine_ref)
            cid = id_m.group(1) if id_m else ""
            cr = by_id.get(cid)
            matched.append((t, cr))
        elif t.is_dispatcher_worker or t.is_edt:
            unmatched_threads.append(t)

    if matched:
        print(f"Threads running coroutines ({len(matched)}):")
        for t, cr in matched:
            cr_info = f" -> {cr.display_name}" if cr else " -> (coroutine not found in dump)"
            print(f"  \"{t.raw_name}\" [{t.state}]{cr_info}")
        print()

    # Idle dispatcher workers
    idle_workers = [t for t in unmatched_threads if t.is_dispatcher_worker]
    if idle_workers:
        parking = sum(1 for t in idle_workers if "TIMED_WAITING" in t.state or "parking" in t.state.lower())
        print(f"Idle dispatcher workers: {len(idle_workers)} ({parking} parked)")


# ─── Helpers ─────────────────────────────────────────────────────────────────


def find_thread_for_coroutine(c: CoroutineInfo, threads: list[ThreadInfo]) -> ThreadInfo | None:
    if not c.coroutine_id:
        return None
    target = f"#{c.coroutine_id}"
    for t in threads:
        if target in t.coroutine_ref:
            return t
    # Also try matching by name
    if c.name:
        for t in threads:
            if c.name in t.coroutine_ref:
                return t
    return None


READ_ACTION_FRAMES = (
    "runReadAction",
    "ReadAction.compute",
    "ReadAction.run",
    "readActionBlocking",
    "readActionUndispatched",
    "constrainedReadActionUndispatched",
    "executeReadAction",
    "runInReadActionWithWriteActionPriority",
    "runWithWriteActionPriority",
)

LOCK_ACQUIRE_FRAMES = (
    "acquireReadPermit",
    "acquireReadActionPermit",
    "acquireWriteActionPermit",
    "upgradeWritePermit",
)


@dataclass
class ThreadReadLockInfo:
    """A thread that is inside a read action (holding the lock)."""
    thread: ThreadInfo
    read_action_frame: str  # the runReadAction/etc frame
    blocking_frame: str  # what the thread is stuck on (above runReadAction)
    read_action_idx: int  # stack index of the read action frame


def find_thread_read_lock_holders(threads: list[ThreadInfo]) -> list[ThreadReadLockInfo]:
    """Find threads that are INSIDE a read action (holding the lock),
    NOT threads waiting to acquire the lock."""
    holders = []
    for t in threads:
        # Find the deepest read action frame in the stack
        read_idx = -1
        read_frame = ""
        for idx, frame in enumerate(t.stack):
            if any(ra in frame for ra in READ_ACTION_FRAMES):
                read_idx = idx
                read_frame = frame

        if read_idx < 0:
            continue

        # Check that the thread is NOT waiting to acquire the lock
        # (lock acquisition frames would be ABOVE the read action frame, i.e., at lower indices)
        is_acquiring = False
        for idx in range(0, read_idx):
            if any(la in t.stack[idx] for la in LOCK_ACQUIRE_FRAMES):
                is_acquiring = True
                break

        if is_acquiring:
            # Thread is still trying to acquire the lock, not holding it
            continue

        # Find the topmost non-internal frame above the read action (what it's stuck on)
        blocking = ""
        for idx in range(0, read_idx):
            frame = t.stack[idx]
            if not is_internal_frame("at " + frame):
                blocking = frame
                break

        holders.append(ThreadReadLockInfo(
            thread=t,
            read_action_frame=read_frame,
            blocking_frame=blocking,
            read_action_idx=read_idx,
        ))

    return holders


INTERNAL_PREFIXES = (
    "at kotlinx.coroutines.",
    "at kotlin.coroutines.",
    "at java.base/",
    "at jdk.internal.",
    "at sun.",
    "at java.desktop/",
    "at java.security.",
    "- parking",
    "- waiting",
    "- locked",
)


def is_internal_frame(frame: str) -> bool:
    return any(frame.startswith(p) or frame.startswith("at " + p.removeprefix("at ")) for p in INTERNAL_PREFIXES)


# ─── Main ────────────────────────────────────────────────────────────────────


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    dump_file = sys.argv[1]
    command = sys.argv[2]
    args = sys.argv[3:]

    with open(dump_file, "r") as f:
        lines = f.readlines()

    lines = [line.rstrip("\n") for line in lines]
    sep = find_separator(lines)

    if sep < 0:
        print("ERROR: Could not find '---------- Coroutine dump ----------' separator.")
        print("This file may not contain a coroutine dump section.")
        sys.exit(1)

    threads = parse_thread_dump(lines, sep)
    roots = parse_coroutine_dump(lines, sep + 2)  # skip separator + blank line

    commands = {
        "summary": lambda: cmd_summary(threads, roots),
        "running": lambda: cmd_running(threads, roots),
        "lock-holders": lambda: cmd_lock_holders(threads, roots),
        "lock-waiters": lambda: cmd_lock_waiters(threads, roots),
        "deadlock": lambda: cmd_deadlock(threads, roots),
        "edt": lambda: cmd_edt(threads, roots),
        "tree": lambda: cmd_tree(threads, roots, args[0] if args else ""),
        "search": lambda: cmd_search(threads, roots, args[0] if args else ""),
        "threads": lambda: cmd_threads(threads, roots),
    }

    if command not in commands:
        print(f"Unknown command: {command}")
        print(f"Available commands: {', '.join(commands.keys())}")
        sys.exit(1)

    commands[command]()


if __name__ == "__main__":
    main()
