This project is a derivative work based on IntelliJ's VistaLauncher code, which
can be found in:
(STUDIO_SRC_ROOT)/tools/idea/native/VistaUpdaterLauncher

Our fork is a bit simplified, as unused code is stripped, and renamed to how it
will actually be used for clarity.

This project must be signed, so it is built manually and dropped into
(STUDIO_SRC_ROOT)/prebuilts/

To build:
1) Open request_admin_rights.sln (requires VS2010+) and build Release/Win32
2) Follow internal instructions to sign the build
3) Put the final build into (STUDIO_SRC_ROOT)/prebuilts/tools/windows