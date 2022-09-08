# Android Studio startup sequence

WARNING: IntelliJ changes their startup lifecycle often as they try to fix performance issues. Many APIs are marked
"internal", so they could change anytime. The following information is current as of AS 2022.1 (Electric Eel).

* `ApplicationLoadListener.beforeApplicationLoaded`
  * Splash screen shown -- no loading bar yet
  * This is **explicitly listed as not to be used by 3rd party plugins**.
  * `AndroidPluginInitializer` is invoked to initialize analytics & studio progress manager (which should be moved further down)
  * called on a background thread
* `ApplicationService` with `preload=true` (not guaranteed), or `preload=await`
  * Application Services can be preloaded (as a last resort) if `preload` attribute is set in the service registration.
  * In this case, they are initialized from a background thread.
  * `preload=await` implies that the startup will wait for this service to be initialized before moving to the next phase in startup.
  * `preload=true` implies that the startup sequence will not wait for the service to be initialized, but these may not be initialized
    at all in some modes of the IDE (such as LIGHT_MODE).
  * Since this happens so early in startup, you cannot rely on much of the IntelliJ infrastructure being initialized at this time.
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
  * `AndroidStudioInitializer`
* `PreloadingActivity.preload`
  * On background thread in parallel with componentsInitialized; only if preloading is enabled
  * **Preloading activities should not have any side effects except for improving subsequent performance**, so that if
    they are not executed for any reason, the behavior of the IDE remains the same.
* `ActionConfigurationCustomizer.customize`
  * Called by `ActionManager.<init>`, which runs sometime during app startup upon first access
  * `AndroidStudioActionCustomizer`
  * `AndroidPlugin$ActionCustomizer`
  * `NewProjectActionsInitializer`
  * `GradleSpecificActionCustomizer`
  * `GradleSpecificInitializer` (TODO: this should not be a `ActionConfigurationCustomizer`)
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

-----------

The following extensions are safe to use, whereas the ones above should be used with caution and perhaps a review
with someone from the platform team.

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