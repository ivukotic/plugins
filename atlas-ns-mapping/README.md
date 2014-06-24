Authorization and mapping plugin for xrootd4j and dCache
========================================================

This is an authorization and mapping plugin for xrootd4j and dCache.

To compile the plugin, run:

    mvn package


The plugin can be tested by loading it with the xrootd4j standalone
server available from http://github.com/dCache/xrootd4j:

    java -Dlog=debug 
         -jar /path/to/xrootd4j/xrootd4j-standalone-1.0.1-jar-with-dependencies.jar \
         --plugins target/atlas-ns-mapping-2.0-SNAPSHOT/ \
         --handler authn:none,authz:atlas-name-to-name-plugin

Using the plugin with dCache
----------------------------

To use this plugin with dCache, place the directory containing this
file in /usr/local/share/dcache/plugins/

on a door node you need both N2N and monitor:
    
    ### site name. value for "rc_site" you may find here: http://atlas-agis-api.cern.ch/request/service/query/get_se_services/?json&flavour=XROOTD
    site=rc_site
	
    ### plugins
	xrootd/xrootdPlugins=gplazma:gsi,authz:atlas-name-to-name-plugin
    
	### Monitoring plugin
	pool/xrootdPlugins=edu.uchicago.monitor
	summary=atl-prod05.slac.stanford.edu:9931:60
	detailed=atl-prod05.slac.stanford.edu:9930:60
    	
when directly federating xrootd dCache doors one needs in addition an upstream redirector plugin on the door node:
    
    ### site name. value for "rc_site" you may find here: http://atlas-agis-api.cern.ch/request/service/query/get_se_services/?json&flavour=XROOTD
    site=rc_site
    
	### plugins
	xrootd/xrootdPlugins=gplazma:gsi,authz:atlas-name-to-name-plugin,redirector
    	
    ### Monitoring plugin
	pool/xrootdPlugins=edu.uchicago.monitor
	summary=atl-prod05.slac.stanford.edu:9931:60
	detailed=atl-prod05.slac.stanford.edu:9930:60

    #Redirector plugin
    xrootd.redirector.host = atlas-xrd-us.usatlas.org
    xrootd.redirector.port = 1094

    
probably never needed still if someone wants to hide a SpaceToken from FAX, just add this variable listing all of the space tokens you want exposed.
    
    overwriteSE=/pnfs/uchicago.edu/atlasgroupdisk,/pnfs/uchicago.edu/atlasproddisk
    
