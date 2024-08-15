"""Version of the blaze plugin."""

# This version will be overwritten in our rapid builds to the actual version number. We set the
# default version to 9999 so that a dev plugin built from Piper HEAD will override any production
# plugin (because IntelliJ will choose the highest version when it sees two conflicting plugins, so
# 9999 > 2017.06.05.0.1).
VERSION = "9999"
