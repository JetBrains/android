# AGP Upgrade Assistant

The AGP Upgrade Assistant is a collection of infrastructure, user interfaces and
somewhat reusable components for the specific purpose of updating projects
consistently in response to a user request to upgrade the version of the Android
Gradle Plugin used in that project.

## Overview

The AGP Upgrade Assistant was introduced in Android Studio 4.2, extending or
substantially replacing existing infrastructure which special-cased three
actions to take:

1. Upgrading the version of AGP to the latest version;
2. Upgrading the version of Gradle to the version associated with that version
   of AGP;
3. Conditionally adding the Google maven repository to the Gradle repositories,
   if the AGP version was being upgraded from a version earlier than 3.0.0 to
   one later than 3.0.0

The introduction of the AGP Upgrade Assistant in Android Studio 4.2 allowed
users to modify their build files if necessary to account for the switch of
default in AGP to use Java 8 (rather than the historic default of Java 7), as
well as to recommend modifying dependency configurations from the deprecated
`compile` to whichever of `api` and `implementation` was appropriate, and to
support migration of projects [from fabric to Firebase
Crashlytics](http://web.archive.org/web/20211017083605/https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android).
Without the AGP Upgrade Assistant, each of these steps (or operations like them)
would have had to be done manually, prompted by a failure of the project to
sync, build, or function correctly once built or deployed.

Since its initial introduction, the AGP Upgrade Assistant has developed in
functionality, now offering features such as:

1. Upgrading the version of AGP to any version compatible with Android Studio
   between the version currently in use and the latest version;
2. A user interface allowing both selection of changes, both coarse-grained (by
   selectively enabling components to be part of the upgrade process) and
   fine-grained (by enabling or disabling individual usages);
3. Unification of notions of compatibility with versions of the Android Gradle
   plugin, as enforced by Gradle Sync;
4. Encoding of changes required for successful upgrades across two major
   versions of AGP (7.0.0 and 8.0.0) as well as handling other compatibility
   issues;
5. Handling of broadly-declarative build files, whether they are expressed in
   Groovy, KotlinScript or some combination of those with Toml Version Catalogs.

## General Principles

### 100% success is unachievable {#100-success-is-unachievable}

For a variety of reasons, some of which are detailed below, the AGP Upgrade
Assistant can not guarantee to successfully update all Android projects, no
matter how limited the definition of success might be.  For this reason, the
Upgrade Assistant's operation has to be as tolerant of failure, and as helpful
to the user in the face of failure, as possible.

### Non-declarative build configuration out of scope

A build as managed by Gradle is configured, in general, using code in languages
which are Turing complete.  In order to make even partial success in updating
projects achievable, we make no attempt in the AGP Upgrade Assistant to handle
non-declarative build configuration beyond a few special cases such as appending
constant data to lists and maps.  We do not at present attempt to handle

- substantial code in build files;
- buildSrc;
- a conventions plugin;

and uses of the AGP Upgrade Assistant in projects making substantial use of
these for build configuration will probably fail.

#### Detection of deprecated APIs

For code in build files and in `buildSrc`, we could in principle at least detect
the use of deprecated APIs, and block upgrades until those usages were removed.
(example: our attempt to do this with the removal of the Transform API in AGP
8.0, and similarly the old Variant API in future).

### User choice

We recognize that there is a significant constituency of users who would like to
believe that a tool such as the AGP Upgrade Assistant might Just Work, and we
intentionally do not disallow the user from trying other upgrade paths (such as
a single-step upgrade-to-latest) even if we are less than optimistic about the
chance that everything will operate correctly after such an upgrade.  Thus, even
if we detect that a user is requesting an upgrade which we consider unlikely to
succeed, we allow the user to execute it anyway: it might get them closer.
However, if we can prove that the upgrade selected by the user cannot possibly
succeed, or that we do not have enough information to be able to make that
determination, we do have a mechanism for [blocking an
upgrade](#blocking-an-upgrade)

### Initial condition

The AGP Upgrade Assistant assumes that:

1. the state of the project when the Upgrade Assistant is launched is fully
   reflected in the IDE (i.e. there are no changes to build files that have not
   been imported to the IDE with a successful sync);
2. the state of the project when the Upgrade Assistant executes an upgrade is
   not substantially different from the state when the Upgrade Assistant was
   launched (or if it is, the user has performed explicit syncs and/or refreshes
   using the UI provided).

### Success criterion

After an upgrade, we deem the upgrade successful if the project successfully
syncs (imports, loads into the IDE) without additional user intervention.

In particular, we make no analogous statement about the project successfully
building, deploying, or passing any accompanying tests; while we hope that
changes to allow the project to sync will tend to increase the chance that the
project will build and deploy successfully, we recognize that in the presence of
semantic changes in the Android Gradle Plugin, and version changes of Gradle
itself and other gradle plugins, can lead to differences in the build.

### Version number intervals

We impose a single “time” variable, based on the ordering of versions of the
Android Gradle plugin.

Many other relevant software artifacts also have versions that might have to be
examined or changed in order to effect an upgrade, such as Gradle itself, other
Gradle plugins, or environmental software such as the JDK used to run Gradle.
Changes or thresholds in those versions are mapped, directly or indirectly, to
the ordering defined by Android Gradle plugin versions; we make no attempt to
model the complete space of possible upgrade states and outcomes, instead
encoding required versions of those artifacts for any given Android Gradle
plugin version, and assuming that if the project is using a later version of
those artifacts pre-upgrade than the system thinks are required post-upgrade,
they continue to be valid.  (One consequence of this is that we do not attempt
to lower the versions of any software used by a project.)

### Version suggestions

AGP's versioning scheme is partly-Semantic, in the sense that major changes in
behaviour are reserved for major version number increments, while patch-level
increments (after the .0 of any release series) are intended to include safe
bug-fix changes only.

This implies that some upgrades involve less risk than others.  Upgrades to
newer patch-level versions should be almost risk-free; upgrades within a given
major series will involve some behavior changes, while changes to build files
required by upgrades between major series might be substantial, and there might
be subtle semantic changes that are not immediately obvious.

The Upgrade Assistant takes the opinionated view that under normal circumstances
we do not recommend upgrading through more than one major version number change
at once, and we only recommend upgrading the major version number from the last
version in the previous series.  This is only a recommendation; the Upgrade
Assistant allows the user to select any published version that is not known to
be incompatible with the version of Android Studio it is running it.

#### Incremental or multi-step upgrades

The default version recommendation means that upgrading a project to the latest
version can take multiple steps.  At present, the User Interface does not handle
multiple steps within one logical session; once one upgrade is complete, as far
as the Upgrade Assistant is concerned, it is in the past and not retrievable
through any more specialized means than Undo.  It might be nice to revisit some
of the possibilities for a more integrated handling of multi-step upgrades.

## Architecture

### Implementation as Refactoring Processor

The Upgrade Assistant is implemented as a Refactoring Processor, albeit one with
a number of extra bells and whistles and substantial internal structure.  We
have had to override some sections of the implementation of the
`BaseRefactoringProcessor` in order to provide some small amounts of custom
behaviour beyond that afforded through extensions.

#### Component refactoring processors

Some of the internal structure resides in the use of component refactoring
processors: each set of modifications to build configuration files for one
conceptual change should be contained within one component processor.

#### Independence of components

Historically, the AGP Version upgrade processor itself was somewhat privileged
in the architecture; however, this special status is now limited to the context
of presenting information to the user.

For a given project with a particular AGP version, each component computes its
necessity; for many processors dealing with some change in AGP behaviour, this
will involve comparing whether the AGP version is before, within or after a
region, bounded below by the version in which a new behaviour was introduced,
and above by the version in which the old behaviour was removed.  These
processors can be said to have an "active region" in version number space.

All component processors are handled individually, and no component should
depend on another component being enabled or active; nor should they in general
depend on their usages being handled before or after those of a different
component, except: if the version number interval for one processor's active
region precedes another's, the processors can rely on the fact that all of the
earlier processor's actions will be taken before any of those of the later
processor.

Note: currently the above statement about ordering is only informally true, by
virtue of the order of the `componentRefactoringProcessors` defined in
`AgpUpgradeRefactoringProcessor`; it would be good to implement more of Allen's
(1983) logic and either enforce this by construction or validate it.  It is
important in order to offer some guarantees about the composability of upgrades,
that the result of A→B and B→C be the same in textual form as A→C -- the
expectation is that any ordering of processors should give the same semantics,
but automated tests are easier to implement in terms of equality of outputs than
equality of Gradle Dsl semantics.

#### Ordering of usages within components

It is sometimes important for the usages generated by a single component to be
processed in order; for example, to move Dsl properties to a different place in
the hierarchy, adding the property at the new place must be done while the value
of the old place is still available in the Dsl model.  (Note that this could be
achieved by storing the value in the creation usage, or by using an atomic move
operation, but it can be convenient to use the ordering of usages within a
component).

Under normal operation, the usage order is preserved.  However, executing the
proposed refactoring from the Preview (usage view) window returns the active
usage set to the AgpUpgradeRefactoringProcessor in arbitrary order, through some
internal (to the IntelliJ platform) use of an unordered set.  We keep a list of
the usages previewed so as to be able to restore the original order of the
previewed usages.

#### Meta-components: move, rewrite, remove

A number of component upgrades involve fairly simple operations on the build
Dsl, corresponding to renaming or reparenting individual properties, deleting
them (either with some other mechanism for backwards-compatibility, or as a
final removal of something no longer supportable), or changing the preferred way
of referring to properties (for example, migrating from one overloaded operator
to separate operators for different-typed values).  Component processors to do
these operations can be built from re-usable metacomponents, specified as fields
in the `PropertiesOperationsRefactoringInfo` data class; individual move,
rewrite or delete operations can also be used as part of the implementation of
general component processors.

#### Blocking an upgrade {#blocking-an-upgrade}

Individual components can *block* an upgrade.  If an individual component
detects that the state of the project is such that it should be active, but that
it cannot successfully alter the project to lead to a successful upgrade, then
it should block, by returning a list of `BlockReason` objects from an override
of the `blockProcessorReasons()` member function.

If the processor is not mandatory for the upgrade, the user can disable it and
proceed with upgrading using any remaining processors.  However, if the processor
is mandatory for the upgrade, the user is not offered the ability to override
the block through the Upgrade Assistant interface; they must make the necessary
modifications to remove the block *before* the Upgrade Assistant will allow its
managed upgrade process.

#### Re-use of component refactoring processors

In an ideal world, we would be able to re-use each component refactoring
processor directly to provide an Action to perform the changes to the build for
the corresponding behaviour change externally to the Upgrade Assistant, for
example as Refactoring menu items.  This has not been explored fully as yet.

### Build file modification using gradle-dsl model

Most of the Component Refactoring Processors make changes to build files, and
most (all?) of them do so using the facilities of the gradle-dsl model.  This
model provides a view of the build configuration, mostly independent of how that
configuration is expressed, and allows changes to be made to build and
configuration files whether they are expressed in Groovy, Kotlin or Toml.

The changes to the build model made by the processors accumulate within the
build model, with later queries to the model reflecting earlier changes, but are
only written out to file near the end of the Refactoring Process, in the
extending `performPsiSpoilingRefactoring()` method.

#### File modification outside the gradle-dsl model

Modifications to project files using other mechanisms (through VirtualFiles, or
even through standard Java IO) might be needed, if changes are needed to files
that are not covered by the gradle-dsl model.  Any such changes require the
system to be notified of the changes for consistency.  Individual component
processors can do this by adding the PsiFile corresponding to files changed in
this way to their `otherAffectedFiles` list; the IDE view of those files will be
updated at the end of the operation of the processor, performing any postponed
operations and committing documents as necessary.

### Modal interface for forced upgrade

When we detect a declared incompatibility between the project's declared version
of the Android Gradle Plugin and the versions of AGP supported by the running
Android Studio, we pop up a modal dialog informing the user of this, and
allowing them to run an upgrade with very limited customizeability.  The limited
ability of the user to tweak the upgrade, coupled with the fact that we declare
ourselves incompatible between preview versions of AGP/Studio, despite often
being compatible and Sync at least partly working, suggests that we should
perhaps revisit this flow in the light of the better scope for compatibility we
have between AGP and Studio versions.

The modal interface is in some sense required for incompatible versions of AGP
and Studio, because if the versions are truly incompatible then we cannot trust
very much of the project information that we have: most of the operations of the
Upgrade Assistant do not depend on valid (Sync) models, though the precondition
really ought to be that they should be obtainable.  On the other hand, the
incompatibility between different preview versions of AGP and Studio is at least
partly artificial, and could perhaps be relaxed if we implemented
producer/consumer model versioning.

#### Compatibility between AGP and Android Studio

More generally, there is the possibility in the future that AGP and Android
Studio release cycles will decouple at least somewhat.  If they do, some thought
needs to go into how Android Studio should treat versions of AGP that are
released *after* themselves -- there is no realistic way of knowing what changes
a newer version of AGP might require.

### The tool window interface

In the more expected case of the AGP Upgrade Assistant being used when an
upgrade is merely recommended (or strongly recommended) rather than forced, we
provide a tool window interface rather than a modal one.

This tool window interface allows the user to customize several things about the
upgrade, with consistency maintained by various validators and UI triggers:

- the target AGP version: we do not enforce an upgrade to a particular version.
  While the initial target is a recommended version (with somewhat complicated
  rules for generating that recommendation; see [above](#version-suggestions)
  for a prose description and `computeGradlePluginUpgradeState()` for the
  details), the user can override this recommendation, taking larger or smaller
  steps as they wish.  The selection box enforces the well-formedness of the AGP
  version string and the constraint that upgrades should go forwards in version
  space only.

- for the project's current AGP version and the user's specified target version,
  a number of upgrade components will be: preconditions (pre-upgrade), required
  (upgrade) or recommended (applicable post-upgrade) steps.  (Others will be
  irrelevant to that combination of current and target version, and yet others
  will in principle be applicable to that combination but would have no effect
  on the particular project).  For those upgrade components, the checkbox tree
  maintains consistency: preconditions can only be unselected if the required
  components are unselected, which itself can only happen if post-upgrade steps
  are unselected.  In the case of "upgrading" to the same version, where there
  are no required steps, recommended steps are selected by default.

- if all components is unselected, the upgrade is blocked, or there is nothing
  to do, the run and preview (see below) buttons are automatically disabled;
  they are re-enabled if the user unblocks the upgrade or selects a component.

### Preview or Show Usages

Users would like to have a view of changes that will be made to their project,
usually expressing that as wanting a "diff view" or something.  Preview, or Show
Usages, is not that, but it is nevertheless interesting.  The Show Usages view
(available both from the tool window and -- more problematically, given that it
is not modal -- from the modal forced-upgrade dialog) represents the changes
that are to be made similarly to the usages in other refactoring previews
(e.g. a method or class rename), with some of the same affordances: including
the ability by users to remove individual usages from the action to be taken.
This allows fine-grained semantic customization of the operation of the
processor, but on a meaningful-change by meaningful-change basis, rather than
line-by-line: if a given usage's refactoring operation does multiple things,
they will all happen together or not at all.

### Reverting changes

We want to make it easy for users to revert changes made by the Upgrade
Assistant, because of our acknowledgment that [100% success is
unachievable](#100-success-is-unachievable).

#### Undo

The Upgrade Assistant integrates with Undo.  The overall processor ensures that
Sync runs after the refactoring is complete; it also adds a
`BasicUndoableAction` to the UndoManager that ensures that Sync runs after Undo
(and Redo) operations.

The overall processor also manages any undo or redo hooks needed by component
processors, making sure to call them appropriately.  At the moment the only
component processor making use of this is the one updating the project JDK to
meet compatibility requirements of AGP.

Note that it seems fairly easy to confuse Undo involving changes from a single
Action (such as a refactoring) in multiple files.

#### Local File History

We also provide a revert operation using the Local File History mechanism,
presented to the user as a button in the post-upgrade report.  Using it might
not completely restore the state of the project, as for example changes to
e.g. the project JDK might not be captured in Local File History; we might need
to integrate better with the hooks implemented for Undo.

## Periodic maintenance

A few things related to the Upgrade Assistant need updating when we are close to
a release of Android Studio and/or AGP, mostly related to data and testing where
the AGP Upgrade Assistant is (close to) the source of historical truth.

### Required version of Gradle

AGP and Studio share a common view of the version of Gradle required for that
version of AGP, through `SdkConstants.GRADLE_LATEST_VERSION`.  This means that
for a release of Studio, the Upgrade Assistant will automatically be aware of
the version of Gradle required for upgrades to the latest version of AGP.

However, the Upgrade Assistant also supports upgrades to versions of AGP that
are not the current, each of which might have its own required version of
Gradle; the Upgrade Assistant by design updates Gradle, if necessary, to the
minimal version required by the requested version of AGP.  A table listing
compatibility requirements is maintained in `CompatibleGradleVersion`, for use
in the Upgrade Assistant but also in project templates and in tests; this table
should be essentially the same as the user-facing documentation at the [Android
Gradle Plugin release
notes](https://developer.android.com/studio/releases/gradle-plugin#updating-gradle).

### Required versions of other software

More infrequently, Android Studio and/or AGP change their distribution or
requirements regarding other external software: for example, the SDK build
tools, the NDK, or the JDK.

Optional or required upgrades to the JDK are handled by a dedicated component
refactoring processor, and changes to the table required versions need to be
made when a new version of the JDK is required by the system.  At the moment
that table is local to the processor, in the `CompatibleJdkVersion` enum class.

At present, changes in required versions of the SDK build tools or the NDK are
not handled by the AGP Upgrade Assistant.

### Integration and end-to-end tests

The integration tests for the AGP Upgrade Assistant are, for infrastructural
reasons, run in shards each specialized to the *current* version of AGP for that
test.  This is because it is relatively expensive to start a Gradle daemon
process, but once it has been started it can be shared between a number of tests
if each uses the same version of AGP.  We verify that the upgrade succeeds (in
our terms, which implies syncing successfully), but rather than running sync on
the post-upgrade state of the project (which would require starting more Gradle
processes, slowing the tests down and using up our infra capacity) we verify
that the post-upgrade state as described by build-related files is equal to
golden files.

When a stable (or RC, or possibly beta) version of Studio is released, it should
be integrated into the `OldAgpTests` and `AgpVersionSoftwareEnvironment`
mechanisms in tests, and prebuilts made available, so that integration tests can
be run of the Upgrade Assistant's (on `studio-main`) ability to upgrade projects
to versions of AGP on the beta channel.

Tests of the upgrade process from canary to canary are currently done
partly-manually.  Automating them would be good, but ideally we should not have
to upload prebuilds of every AGP canary.  Perhaps we should upload the *first*
AGP alpha of every series, and verify that the Upgrade Assistant can upgrade to
the latest alpha / beta (but note that this might require a change in behaviour
of the Upgrade Assistant recommendations to accommodate the fact that the test
environment will not treat the "latest" version as having been published).

## Further reading

- James F. Allen, “[Maintaining Knowledge about Temporal
  Intervals](http://cse.unl.edu/~choueiry/Documents/Allen-CACM1983.pdf)”.
  Communications of the ACM 26:11, pp.832—843 (1983)

- (Google internal) [Index of AGP Upgrade Assistant
  documents](https://docs.google.com/document/d/1wKkG15sj60UrLBbVLZD-w1BGryaQcin5juawMJ0GLpM/edit)

