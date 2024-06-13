package com.android.tools.idea;

/**
 * This trampoline exists primarily to encourage IntelliJ IDEA to provide the right classpath for the "Android Studio" (and "K2")
 * Run Configurations.
 * <p>
 * IntelliJ IDEA determines which of the many possible classpaths to provide (for "Use classpath of module") based on whether the
 * class being run is in the production sources of the module (or its dependencies).  If the class is in a library
 * (as {@link com.intellij.idea.Main} is in the non-monobuild case) then the run configuration classpath generated includes test
 * sources by default, at least if the module (such as this studio module) has no source roots.
 * <p>
 * Merely creating a source root would probably be enough to convince the run configuration to use production sources only.  However,
 * such an empty source root would be vulnerable to later cleanup and deletion, leading to a silent performance regression for developers
 * working primarily in the IDE.  This trampoline has the secondary function of being the place to attach this comment.
 * <p>
 * This class should not end up in production, as its only function is to satisfy a constraint in IntelliJ IDEA.
 */
public class Main {
  public static void main(String[] args) {
    com.intellij.idea.Main.main(args);
  }
}