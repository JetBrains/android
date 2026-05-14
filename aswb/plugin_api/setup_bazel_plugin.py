import os
import re
import shutil
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
# Find the repository root by looking for the .repo directory
def find_repo_root(path):
    current = os.path.abspath(path)
    while current != os.path.dirname(current):
        if os.path.exists(os.path.join(current, ".repo")):
            return current
        current = os.path.dirname(current)
    print("Error: Could not find .repo directory to determine repository root.")
    sys.exit(1)

REPO = find_repo_root(SCRIPT_DIR)
FILES_TO_MODIFY = [
    'tools/adt/idea/aswb/plugin_api/BUILD',
    'tools/adt/idea/aswb/BUILD'
]

plugin_query_cache = {}
exists_cache = {}

def get_absolute_labels(build_file):
    pkg = "//" + os.path.dirname(build_file) + ":all"

    # use to find all aboslute labels in exports or deps
    result = subprocess.run(
        ["buildozer", "print exports deps", pkg],
        cwd=REPO,
        capture_output=True,
        text=True
    )

    # pattern match to find all absolute labels starting with // or @
    labels = set()
    for match in re.findall(r'(//[^\s\]"]+|@[^\s\]"]+)', result.stdout):
        labels.add(match.strip('"').strip("'"))

    # filter out package-level/visibility labels
    filtered_labels = set()
    for label in labels:
        if "__pkg__" in label or "__subpackages__" in label or "//visibility" in label:
            continue
        filtered_labels.add(label)

    return filtered_labels

def is_bundled_in_plugin(target):
    if target in plugin_query_cache:
        return plugin_query_cache[target]

    if target.startswith("//tools/adt/idea/studio:"):
        plugin_name = target.split(':')[1]
        plugin_query_cache[target] = plugin_name
        return plugin_name

    # check if it's an internal target that is bundled into a studio_plugin
    query = f'attr("modules", "{target}", //tools/adt/idea/studio:*)'
    try:
        result = subprocess.run(
            ['bazel', 'query', '--keep_going', query],
            cwd=REPO,
            capture_output=True,
            text=True
        )
        for line in result.stdout.strip().split('\n'):
            if line.startswith('//tools/adt/idea/studio:'):
                plugin_name = line.split(':')[1]
                plugin_query_cache[target] = plugin_name
                return plugin_name
    except Exception:
        pass

    plugin_query_cache[target] = None
    return None

def check_target_exists(target):
    if target in exists_cache:
        return exists_cache[target]

    # quick ignore for obvious non-targets or macros
    if target.endswith('/') or target.endswith('.bzl') or target.startswith('//visibility:') or target.startswith('//conditions:'):
        exists_cache[target] = True
        return True

    # check if the target exists
    try:
        result = subprocess.run(
            ['bazel', 'query', '--keep_going', target],
            cwd=REPO,
            capture_output=True,
            text=True
        )
        if result.returncode == 0:
            exists = True
        else:
            err = result.stderr.lower()
            if "no such target" in err or "no such package" in err or "build file not found" in err or "not declared in package" in err:
                exists = False
            else:
                exists = True
        exists_cache[target] = exists
        return exists
    except Exception:
        exists_cache[target] = False
        return False

def replace_label(pkg, old_label, new_label):
    args = ["buildozer", f"replace * {old_label} {new_label}", pkg]
    subprocess.run(args, cwd=REPO, capture_output=True)

def remove_label(pkg, label):
    args = ["buildozer", f"remove * {label}", pkg]
    subprocess.run(args, cwd=REPO, capture_output=True)

def process_file(build_file, removed_targets_list):
    labels = get_absolute_labels(build_file)
    pkg = "//" + os.path.dirname(build_file) + ":all"

    for label in labels:
        plugin_name = is_bundled_in_plugin(label)
        if plugin_name:
            # if it is, something like <package>:<target>, replace it with @intellij//:<target>
            new_label = f"@intellij//:{plugin_name}"
            replace_label(pkg, label, new_label)
        else:
            exists = check_target_exists(label)
            if not exists:
                # if that target does not exist, remove it
                remove_label(pkg, label)
                removed_targets_list.add(label)

if __name__ == '__main__':
    # copy intellij.MODULE.bazel.OSS to tools/base/intellij-bazel/intellij.MODULE.bazel to use android studio
    src_module = os.path.join(REPO, 'tools/adt/idea/aswb/intellij.MODULE.bazel.OSS')
    dst_module = os.path.join(REPO, 'tools/base/intellij-bazel/intellij.MODULE.bazel')
    if os.path.exists(src_module):
        print(f"Copying {src_module} to {dst_module}...")
        os.makedirs(os.path.dirname(dst_module), exist_ok=True)
        shutil.copy2(src_module, dst_module)
    else:
        print(f"Warning: Source file {src_module} not found. Skipping copy.")

    # ensure buildozer is available
    if not shutil.which("buildozer"):
        print("Error: buildozer not found. Please install buildozer.")
        sys.exit(1)

    removed_targets = set()
    for f in FILES_TO_MODIFY:
        print(f"Processing {f}...")
        process_file(f, removed_targets)

    # read lib_deps from plugin_api_module and add to studio_plugin libs
    # this is necessary when we build bazel plugin alone
    print("Syncing lib_deps from plugin_api_module to studio_plugin libs...")
    plugin_api_module = "//tools/adt/idea/aswb/plugin_api:plugin_api_module"
    result = subprocess.run(
        ["buildozer", "print lib_deps", plugin_api_module],
        cwd=REPO,
        capture_output=True,
        text=True
    )
    lib_deps = re.findall(r'(//[^\s\]]+|@[^\s\]]+)', result.stdout)
    if lib_deps:
        studio_plugin_target = "//tools/adt/idea/aswb:%studio_plugin"
        for dep in lib_deps:
            # Clean up the label if it has trailing characters or is quoted
            dep = dep.strip('"').strip("'")
            if dep != "@intellij//:org.jetbrains.android":
                print(f"  Adding {dep} to {studio_plugin_target} libs...")
                subprocess.run(
                    ["buildozer", f"add libs {dep}", studio_plugin_target],
                    cwd=REPO,
                    capture_output=True
                )

    if removed_targets:
        print("\n" + "="*80)
        print("REMOVED TARGETS (Not bundled and do not exist in external workspace):")
        for t in sorted(list(removed_targets)):
            print(f"  - {t}")
        print("="*80)
        print("\nWARNING: I removed the not exists targets, it may lead to build failed.")