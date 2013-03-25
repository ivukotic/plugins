Authorization and mapping plugin for xrootd4j and dCache
========================================================

This is a RUCIO version of authorization and mapping plugin 
for xrootd4j and dCache.

To compile the plugin, run:

    mvn package


The plugin can be tested by loading it with the xrootd4j standalone
server available from http://github.com/gbehrmann/xrootd4j:

    java -Dlog=debug 
         -jar /path/to/xrootd4j/xrootd4j-standalone-1.0.1-jar-with-dependencies.jar \
         --plugins target/atlas-ns-mapping-2.0-SNAPSHOT/ \
         --handler authn:none,authz:atlas-name-to-name-plugin

Using the plugin with dCache
----------------------------

To use this plugin with dCache, place the directory containing this
file in /usr/local/share/dcache/plugins/

To enable the plugin, define the following property in dcache.conf:

    xrootdAuthzPlugin=atlas-rucio-n2n-plugin 

that way is now deprecated. Proper way is:

    xrootd/xrootdPlugins=authn:none,authz:atlas-rucio-n2n-plugin
    pool/xrootdPlugins=

