# Android Studio startup sequence

WARNING: IntelliJ changes their startup lifecycle often as they try to fix performance issues. Many APIs are marked
"internal", so they could change anytime. The following information is current as of AS 2022.1 (Electric Eel).

* `ApplicationLoadListener.beforeApplicationLoaded`
  * Splash screen shown -- no loading bar yet
  * This is **explicitly listed as not to be used by 3rd party plugins**.
  * `AndroidPluginInitializer` is invoked to initialize analytics & studio progress manager (which should be moved further down)
* `ApplicationComponent` constructors are called
  * Splash screen loading bar is moving
  * **ApplicationComponents are deprecated.** Please do not add new ones, or add more functionality into existing ones.
    b/243082216 tracks our work to remove them. As of now, we have the following application components:
    * `AndroidInitialConfigurator` is run at this time.
    * `AndroidInitialConfigurator` also executes all Runnables that extend `androidStudioInitializer`:
      * `AndroidSdkInitializer`
        * sdk setup, first run wizard mode, some gui test specific code.
        * Oddly, `SystemInfoStatsMonitor` is started here
* `ApplicationInitializedListener.componentsInitialized`
  * Splash screen loading bar is moving
  * `LowMemoryReporter$OnStartup`
  * `ThreadingChecker`
  * `DisableGradleProjectOpenProcessor` used to unregister the platform's `GradleProjectOpenProcessor`
* `PreloadingActivity.preload`
  * On background thread in parallel with componentsInitialized; only if preloading is enabled
  * **Preloading activities should not have any side effects except for improving subsequent performance**, so that if
    they are not executed for any reason, the behavior of the IDE remains the same.
* `ActionConfigurationCustomizer.customize`
  * `ActionManager` is first accessed, usually during preloading
  * `AndroidStudioInitializer` sets up analytics and system health monitoring.
  * `AndroidStudioActionCustomizer`:
    * `setUpNewFilePopupActions`, `hideRarelyUsedIntellijActions` and `setupResourceManagerActions`
  * `AndroidPlugin$ActionCustomizer`
  * `NewProjectActionsInitializer`
  * `GradleSpecificActionCustomizer`
  * `GradleSpecificInitializer`
  * `AdtImportInitializer`
  * `PsdActionsInitializer`
* `ProjectLifecycleListener.beforeProjectLoaded`
  * NOTE: **Deprecated for removal in IJ 213**, and appears to not be called for initial project if
    "reopen projects on startup" is enabled.
* TODO: Verify that `ProjectManagerListener.projectOpened` is invoked here
* `ProjectLifecycleListener.projectComponentsInitialized`
  * Under project loading dialog; deprecated
* `AppLifecycleListener.appStarted`
  * For some reason **this is called after project loading has started if "reopen projects on startup" is enabled**.
* `StartupActivity.DumbAware.runActivity`
  * On background thread after project open
  * Runs under the progress loading dialog if registered using the startupActivity EP. Otherwise, if registered
    using the postStartupActivity EP, it runs without any progress indicator at all.
  * NOTE: Core.xml has a comment saying, "only bundled plugin can define startupActivity". Presumably this is
    because JetBrains does not want random plugins slowing down project open...? Not sure. Seems strange because,
    without startupActivity, it is unclear to me how one could register a project-open callback that is actually
    guaranteed to finish before the user starts interacting with the opened project...
* `StartupActivity.Background.runActivity`
  * On background thread, 5 seconds after project open
* `StartupActivity.runActivity`
  * On EDT, after indexing finishes in the opened project.