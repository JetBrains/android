import sys
import subprocess
import os
from os.path import *

scriptLocation = abspath(sys.argv[0])
ideHome = dirname(dirname(scriptLocation))
java = join(ideHome, 'jre', 'bin', 'java')
studioExecutable = 'studio.sh'

def findJarsUnder(path):
    jars = []
    for root, dirs, files in os.walk(path):
        jars += [join(root, name) for name in files if name.endswith('.jar')]
    return jars

jars = findJarsUnder(join(ideHome, 'lib')) + findJarsUnder(join(ideHome, 'plugins'))
jars.append(join(ideHome, 'jre', 'lib', 'tools.jar'))

classpath = ':'.join(jars)

testgroups = ['PROJECT_SUPPORT', 'PROJECT_WIZARD', 'THEME', 'EDITING', 'TEST_FRAMEWORK', 'UNRELIABLE', 'DEFAULT']

args = [java, '-classpath', classpath]

if len(sys.argv) > 1:
    if sys.argv[1] in testgroups:
        args.append('-Dui.test.group=%s' % sys.argv[1])
    else:
        args.append('-Dui.test.class=%s' % sys.argv[1])
else:
    print "Usage: Expecting exactly one argument, either a test group name or fully qualified test class name."
    sys.exit(1)

args.append('-Didea.gui.test.remote.ide.path=%s' % join(ideHome, 'bin', studioExecutable))
args.append('-Didea.gui.test.running.on.release=true')
args.append('-Didea.gui.test.from.standalone.runner=true')
args.append('-Dbootstrap.testcase=com.android.tools.idea.tests.gui.GuiTestSuite')
args.append('com.intellij.testGuiFramework.launcher.StandaloneLauncherKt')

subprocess.call(args)
