#!/usr/bin/env bash

# path to default Arduino director - the tools directory is found within it
if [[ -z "$INSTALLDIR" ]]; then
    INSTALLDIR="$HOME/Documents/Arduino"
fi
echo "INSTALLDIR: $INSTALLDIR"

# Three java libraries used by the tool.
#  .jar files are expected to be found somewhere above the present directory
#  To create them, clone the Arduino master repository and run 'ant build' 
pde_path=`find ../../../ -name pde.jar`
core_path=`find ../../../ -name arduino-core.jar`
lib_path=`find ../../../ -name commons-codec-1.7.jar`
if [[ -z "$core_path" || -z "$pde_path" || -z "$lib_path" ]]; then
    echo "Some java libraries have not been found"
    echo " if Arduino-master is built yet (did you run ant build?)"
    return 1
fi
echo "pde_path: $pde_path"
echo "core_path: $core_path"
echo "lib_path: $lib_path"
echo "----------------------"
echo ..
echo ..
echo ..


set -e

# build jars
mkdir -p bin
rm -rf bin/com
javac -target 1.8 -cp "$pde_path:$core_path:$lib_path" \
      -d bin src/makeUF2.java

# build tool in Arduino IDE tool folder
pushd bin
mkdir -p $INSTALLDIR/tools
rm -rf $INSTALLDIR/tools/makeUF2
mkdir -p $INSTALLDIR/tools/makeUF2/tool
zip -r $INSTALLDIR/tools/makeUF2/tool/makeUF2.jar *
popd

