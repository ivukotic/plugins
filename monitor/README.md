Channel handler plugin for xrootd4j and dCache
==============================================

This is a channel handler plugin for xrootd4j and dCache.

To compile the plugin, run:

    mvn package


The plugin can be tested by loading it with the xrootd4j standalone
server available from http://github.com/gbehrmann/xrootd4j:

    java -Dlog=debug -Dsitename=TEST.uc -Dhostname=CollectorHost -Dport=CollectorPort -Ddelay=30 
         -jar /path/to/xrootd4j/xrootd4j-standalone-1.1.0-jar-with-dependencies.jar \
         --plugins target/monitor-1.0-SNAPSHOT/ \
         --handler authn:none,edu.uchicago.monitor

replace CollectorHost and CollectorPort with proper values.

Using the plugin with dCache
----------------------------

To use this plugin with dCache, place the directory containing this
file in /usr/local/share/dcache/plugins/

To enable only this plugin, define the following property in dcache.conf:
	xrootd/xrootdPlugins=authn:none,authz:none,edu.uchicago.monitor
	pool/xrootdPlugins=
	
    sitename=TEST.uc 
	hostname=CollectorHost
	port=CollectorPort
	delay=30


