This directory contains content that should be copied over a vanilla NSIS 3+
installation, as our script depends on them.

The Include/ and Plugins/ directories contain the following plugins:

FindProcDLL - A plugin which lets you search through all running processes,
  useful for detecting if Studio is running or not. We use the version modified
  by hnedka.
  http://nsis.sourceforge.net/FindProcDLL_plug-in

nsis7z - A plugin which provides a way to extract 7zip files and has nice
  integration with the status bar on the progress page.
  http://nsis.sourceforge.net/Nsis7z_plug-in

UAC - A plugin which provides user access control (admin vs. user) support. Note
  that with NSIS 3+, a line in the standard UAC.nsh file was failing to compile
  with an error about using !undef on a non-existing define. Our local version
  is modified to remove this extraneous line.
  http://nsis.sourceforge.net/UAC_plug-in
