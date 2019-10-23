# Introduction

AndroidPlugin (e.g. https://android.googlesource.com/platform/tools/adt/idea or https://github.com/JetBrains/android) has a number
of dependencies on tool libraries (e.g. https://android.googlesource.com/platform/tools/base). These dependencies are managed
differently in AOSP builds and Idea builds: AOSP links all the required repositories (see https://android.googlesource.com/platform/tools/base/+/studio-master-dev/source.md)
while Idea build uses pre-compiled versions of the dependencies. I.e. AOSP build always
references a module (with source files), while Idea build always references a library (precompiled source files from AOSP). 

# Problem
Difference in dependency management makes unnecessary diffs during merge. Namely:
* `<module>` vs `<library>` conflicts
* module may have exported dependencies on other modules and libraries while library may not. As a result,
all the transitive dependencies should be explicitly added. E.g. if `module A` exports `library B`, and 
`module C` references `module A`, then during migration of `module A` to `library A`, `module C` should
reference not only `library A`, but also `module C`. Note that this particular issue cannot be solved with
the library's transitive dependencies: a library may have no dependencies on modules.
 

# Solution
All the tool libraries in Idea builds are wrapped as modules exporting the library they wrap plus
additional components (modules/libraries) if needed.

# Benefits
* Easy change tracking in iml files (AOSP vs Idea repository)
* No need to "unpack" transitive dependencies when a module converted to a library
* No need to "cleanup" obsolete transitive dependencies
* Clean module structure (less libraries in dependencies)
* Visibility of many libraries narrowed from project to module level

# Operations
During the merge (AOSP to Idea) iml files with the matching names should be  merged from external
repositories (e.g. .../tools/base) with the iml files in this directory. Rule of thumb: files in
this directory are the same as AOSP files with only 2 changes:
1. New features in Idea may modify module dependency graph which might need to be reflected in these files.
1. iml files in current directory should not reference any source files. Instead, an extra library (precompiled sources)
should be added to the module dependencies and made exported.