# /// script
# requires-python = ">=3.8"
# dependencies = []
# ///

import os
import sys
import argparse
import json
import zipfile
import io

class DiagnosticsProvider:
    def exists(self, path): raise NotImplementedError()
    def open(self, path): raise NotImplementedError()
    def list_files(self, path): raise NotImplementedError()

class DirectoryProvider(DiagnosticsProvider):
    def __init__(self, root):
        self.root = root
    def _path(self, path):
        return os.path.join(self.root, path)
    def exists(self, path):
        return os.path.exists(self._path(path))
    def open(self, path):
        return open(self._path(path), 'r', encoding='utf-8', errors='ignore')
    def list_files(self, path):
        full_path = self._path(path)
        if not os.path.isdir(full_path): return []
        results = []
        for root, _, files in os.walk(full_path):
            for f in files:
                rel_to_root = os.path.relpath(os.path.join(root, f), full_path)
                results.append(rel_to_root.replace(os.sep, '/'))
        return results

class ZipProvider(DiagnosticsProvider):
    def __init__(self, zip_path):
        self.zf = zipfile.ZipFile(zip_path, 'r')
        self.names = self.zf.namelist()
        self.prefix = ""
        # Common pattern: diagnostics are either at root or in a single top-level folder
        if self.names:
            first_parts = self.names[0].split('/')
            if len(first_parts) > 1:
                prefix_candidate = first_parts[0] + '/'
                if all(n.startswith(prefix_candidate) for n in self.names):
                    self.prefix = prefix_candidate

    def _path(self, path):
        return (self.prefix + path).replace('\\', '/')

    def exists(self, path):
        return self._path(path) in self.names or any(n.startswith(self._path(path) + '/') for n in self.names)

    def open(self, path):
        return io.TextIOWrapper(self.zf.open(self._path(path)), encoding='utf-8', errors='ignore')

    def list_files(self, path):
        prefix = self._path(path)
        if prefix and not prefix.endswith('/'): prefix += '/'
        results = []
        for n in self.names:
            if n.startswith(prefix) and not n.endswith('/'):
                results.append(n[len(prefix):])
        return results

def analyze_system_info(provider):
    sys_info_path = os.path.join('System Info', 'SystemInfo.log')
    if not provider.exists(sys_info_path):
        return "SystemInfo.log not found."

    info = []
    with provider.open(sys_info_path) as f:
        for line in f:
            if any(keyword in line for keyword in ["Android Studio", "OS Version", "Java Version", "Android Gradle Plugin", "Gradle"]):
                info.append(line.strip())

    return "\n".join(info)

def analyze_thread_dumps(provider):
    if not provider.exists('Thread Dumps'):
        return None

    files = provider.list_files('Thread Dumps')
    freezes = {}

    for f in files:
        parts = f.split('/')
        if len(parts) >= 2 and 'freeze' in parts[-2].lower() and parts[-1].startswith('threadDump-'):
            folder = parts[-2]
            if folder not in freezes:
                # Extract timestamp if present: threadDumps-freeze-YYYYMMDD-HHMMSS-...
                timestamp = "Unknown"
                time_parts = folder.split('-')
                for i, p in enumerate(time_parts):
                    if p == 'freeze' and i + 2 < len(time_parts):
                        timestamp = f"{time_parts[i+1]} {time_parts[i+2]}"
                        break

                analysis = analyze_single_thread_dump(provider, os.path.join('Thread Dumps', f))
                if analysis:
                    analysis['timestamp'] = timestamp
                    freezes[folder] = analysis

    return [{"folder": k, "analysis": v} for k, v in sorted(freezes.items())]

def analyze_single_thread_dump(provider, path):
    threads = {}
    current_thread = None

    with provider.open(path) as f:
        for line in f:
            line_stripped = line.strip()
            if line.startswith('"') and 'prio=' in line:
                parts = line.split('"')
                if len(parts) >= 3:
                    thread_name_match = parts[1]
                else:
                    thread_name_match = "Unknown"
                current_thread = thread_name_match
                if current_thread not in threads:
                    threads[current_thread] = []
                threads[current_thread].append(line_stripped)
            elif current_thread is not None:
                if line_stripped == "" and len(threads[current_thread]) > 2:
                    current_thread = None
                elif line_stripped != "":
                    threads[current_thread].append(line_stripped)

    edt_trace = threads.get("AWT-EventQueue-0", [])

    lock_keywords = ["runReadAction", "runWriteAction", "runWriteIntentReadAction", "smartAcquireReadPermit", "acquireReadPermit"]
    background_lock_threads = []

    for thread_name, trace in threads.items():
        if thread_name == "AWT-EventQueue-0":
            continue

        trace_str = "\n".join(trace)
        if any(keyword in trace_str for keyword in lock_keywords):
            background_lock_threads.append({
                "name": thread_name,
                "stackTrace": trace[:15]
            })

    return {
        "edtStackTrace": edt_trace[:15],
        "lockThreads": background_lock_threads
    }

def analyze_idea_log(provider):
    log_path = os.path.join('Logs', 'idea.log')
    if not provider.exists(log_path):
        return None

    errors = []
    current_error = None

    def is_timestamped(line):
        return len(line) > 19 and line[4] == '-' and line[7] == '-' and line[10] == ' ' and line[13] == ':'

    with provider.open(log_path) as f:
        for line in f:
            line_stripped = line.strip()
            if " ERROR - " in line or " FATAL - " in line:
                if current_error:
                    errors.append(current_error)
                current_error = {"header": line_stripped, "stackTrace": []}
            elif current_error:
                if is_timestamped(line):
                    errors.append(current_error)
                    current_error = None
                elif line_stripped:
                    current_error["stackTrace"].append(line_stripped)

        if current_error:
            errors.append(current_error)

    return errors

def format_as_markdown(system_info, freezes, errors):
    report = "# Diagnostics Report Analysis\n\n"
    report += "## System Information\n```text\n" + system_info + "\n```\n\n"

    if errors:
        report += "## Critical Errors (idea.log)\n"
        for err in errors[:10]:
            report += f"### {err['header']}\n"
            if err['stackTrace']:
                report += "```java\n" + "\n".join(err['stackTrace'][:20]) + "\n```\n"
            report += "\n"
        if len(errors) > 10:
            report += f"*... and {len(errors) - 10} more errors.*\n\n"

    report += "## Thread Dumps (Freezes)\n"
    report += "> **Note:** Diagnostic reports often contain multiple freeze dumps captured over time. Compare the timestamps below with the reported issue time to ensure you are analyzing the relevant event.\n\n"

    if not freezes:
        report += "No freeze thread dumps found.\n"
    else:
        for freeze in freezes:
            analysis = freeze['analysis']
            report += f"### Freeze: `{freeze['folder']}`\n"
            report += f"**Timestamp:** `{analysis.get('timestamp', 'Unknown')}`\n\n"

            if analysis['edtStackTrace']:
                report += "**AWT-EventQueue-0 (EDT) Stack Trace:**\n```java\n"
                report += "\n".join(analysis['edtStackTrace'])
                report += "\n```\n"

            if analysis['lockThreads']:
                report += "\n**Potential Lock-Holding Background Threads:**\n"
                for t in analysis['lockThreads']:
                    report += f"* **{t['name']}**\n```java\n"
                    report += "\n".join(t['stackTrace'])
                    report += "\n```\n"
    return report

def main():
    parser = argparse.ArgumentParser(description="Analyze Android Studio diagnostic reports (zip or directory).")
    parser.add_argument("path", help="Path to the diagnostics zip file or extracted folder.")
    parser.add_argument("--json", action="store_true", help="Output results in JSON format.")

    args = parser.parse_args()

    if zipfile.is_zipfile(args.path):
        provider = ZipProvider(args.path)
    elif os.path.isdir(args.path):
        provider = DirectoryProvider(args.path)
    else:
        print(f"Error: '{args.path}' is not a directory or a zip file.", file=sys.stderr)
        sys.exit(1)

    system_info = analyze_system_info(provider)
    freezes = analyze_thread_dumps(provider)
    errors = analyze_idea_log(provider)

    if args.json:
        result = {
            "system_info": system_info,
            "freezes": freezes,
            "errors": errors
        }
        print(json.dumps(result, indent=2))
    else:
        print(format_as_markdown(system_info, freezes, errors))

if __name__ == '__main__':
    main()
