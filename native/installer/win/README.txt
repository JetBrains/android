This is the base directory for our Android Studio Win installer.

Our installer is an NSIS script, setup_android_studio.nsi, and is built with
NSIS3+ (for Unicode support).
See also: http://nsis.sourceforge.net/Main_Page
And also: http://nsis.sourceforge.net/License

On top of a vanilla installation of NSIS, we extend it by adding the plugins
in the subfolder plugins_for_nsis/. This is a manual process but only needs to
be done once. Please see the README in that directory for more specific
information about the plugins used.

Finally, the header documentation in our NSIS script contains useful information
on how to compile it, such as required defines that must be set.
