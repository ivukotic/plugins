Authorization and mapping plugin for xrootd4j and dCache
========================================================

This is an authorization and mapping plugin for xrootd4j and dCache.

To compile the plugin, run:

    mvn package


The plugin can be tested by loading it with the xrootd4j standalone
server available from http://github.com/gbehrmann/xrootd4j:

    java -Dlog=debug 
         -jar /path/to/xrootd4j/xrootd4j-standalone-1.2-SNAPSHOT-jar-with-dependencies.jar \
         --plugins target/atlas-ns-mapping-2.0-SNAPSHOT/ \
         --handler authn:none,authz:atlas-name-to-name-plugin

to run it successfuly it needs at least two environment variables set:
LFC_HOST and SRM_HOST
these determine which LFC DB will be used and what is the srm machine name.

if your LFC DB does not provide read only unauthorized access you will also need a valid ATLAS proxy.

