#!/bin/sh
# This script generates _DbLexer.java from _DbLexer.flex
# Usage: ./runflex.sh

ADT_IDEA="tools/adt/idea"

# Try to get the location of this script.
if [ -n $BASH ]; then
  # see http://stackoverflow.com/a/246128/1546000
  MY_LOCATION=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
  cd $MY_LOCATION
else
  # Let's assume script was run from the same dir.
  MY_LOCATION=$(pwd)
fi

# Check mac or linux to get sed argument to enable extended regex.
case $(uname -s) in
  Darwin)
    EXT_REGEX="-E"
    ;;
  *)
    EXT_REGEX="-r"
    ;;
esac


ADT_IDEA=$(echo $MY_LOCATION | sed $EXT_REGEX -e "s,.*$ADT_IDEA[^/]*/,," -e "s,[^/]+,..,g")

JFLEX="$ADT_IDEA/../../idea/tools/lexer"

java -Dfile.encoding=UTF-8 \
	     -classpath $JFLEX/jflex-1.4/lib/JFlex.jar \
	     JFlex.Main --charat --nobak  \
	     --skel $JFLEX/idea-flex.skeleton \
	     --quiet \
	     _DbLexer.flex
