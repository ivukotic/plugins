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
		 -Dvo=atlas
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

	### plugins
	pool/xrootdPlugins=edu.uchicago.monitor
    
	### Monitoring plugin
    summary=atl-prod05.slac.stanford.edu:9931:60
	detailed=atl-prod05.slac.stanford.edu:9930:60
	vo=ATLAS


FOR ATLAS only:

on a door node you need both N2N and monitor:
    
    ### site name. value for "rc_site" you may find here: http://atlas-agis-api.cern.ch/request/service/query/get_se_services/?json&flavour=XROOTD
    site=rc_site
	
    ### plugins
	xrootd/xrootdPlugins=gplazma:gsi,authz:atlas-name-to-name-plugin
    
	### Monitoring plugin
	pool/xrootdPlugins=edu.uchicago.monitor
	summary=atl-prod05.slac.stanford.edu:9931:60
	detailed=atl-prod05.slac.stanford.edu:9930:60
	vo=ATLAS
    	
when directly federating xrootd dCache doors one needs in addition an upstream redirector plugin on the door node:
    
    ### site name. value for "rc_site" you may find here: http://atlas-agis-api.cern.ch/request/service/query/get_se_services/?json&flavour=XROOTD
    site=rc_site
    
	### plugins
	xrootd/xrootdPlugins=gplazma:gsi,authz:atlas-name-to-name-plugin,redirector
    	
    ### Monitoring plugin
	pool/xrootdPlugins=edu.uchicago.monitor
	summary=atl-prod05.slac.stanford.edu:9931:60
	detailed=atl-prod05.slac.stanford.edu:9930:60
	vo=ATLAS

    #Redirector plugin
    xrootd.redirector.host = atlas-xrd-us.usatlas.org
    xrootd.redirector.port = 1094

If your server is behind NAT, you should also define property servername in a form:
	servername=myserver.mydomain.org


