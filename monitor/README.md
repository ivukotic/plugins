Channel handler plugin for xrootd4j and dCache
==============================================

This is a channel handler plugin for xrootd4j and dCache.

To compile the plugin, run:

    mvn package


The plugin can be tested by loading it with the xrootd4j standalone
server available from http://github.com/dcache/xrootd4j:

    java -Dlog=debug  \
		 -Dsitename=TEST.uc \
		 -Dsummary=test.stanford.edu:9931:60,localhost:9931:17 \
		 -Ddetailed=localhost:9930:13
         -jar /path/to/xrootd4j/xrootd4j-standalone-1.1.0-jar-with-dependencies.jar \
         --plugins target/monitor-1.0-SNAPSHOT/ \
         --handler authn:none,edu.uchicago.monitor

You may select several destinations to send collected information to. Each destination is given in form:
<hostname>:<port>[:<interval>[:<outbound port>]]
Interval signifies wait period in seconds between two consecutive sendings of information to that server.
Replace values given with the real ones.

Using the plugin with dCache
----------------------------

To use this plugin with dCache, place the directory containing this
file in /usr/local/share/dcache/plugins/


On the pool nodes you need only monitoring plugin:

	### Monitoring plugin
	pool/xrootdPlugins=edu.uchicago.monitor
	summary=atl-prod05.slac.stanford.edu:9931:60
	detailed=atl-prod05.slac.stanford.edu:9930:60

on a door node you need both N2N and monitor:

	### Monitoring plugin
	pool/xrootdPlugins=edu.uchicago.monitor
	### N2N plugin
	xrootdAuthzPlugin=atlas-name-to-name-plugin
	
	summary=atl-prod05.slac.stanford.edu:9931:60
	detailed=atl-prod05.slac.stanford.edu:9930:60

While using xrootdAuthzPlugin gives a worning message that it is obsolete, command:   
	xrootd/ xrootdPlugins=authn:atlas-name-to-name-plugin,authz:none
was not properly tested.
	

If your server is behind NAT, you should also define property servername in a form:
	servername=myserver.mydomain.org


