Authorization and mapping plugin for xrootd4j and dCache
========================================================

This is an authorization and mapping plugin for xrootd4j and dCache.

To compile the plugin, run:

    mvn package


The plugin can be tested by loading it with the xrootd4j standalone
server available from http://github.com/gbehrmann/xrootd4j:

    java -Dlog=debug -Dlfc_host=myLFChost -Dsrm_host=mySRMhost
         -jar /path/to/xrootd4j/xrootd4j-standalone-1.0.1-jar-with-dependencies.jar \
         --plugins target/atlas-ns-mapping-2.0-SNAPSHOT/ \
         --handler authn:none,authz:atlas-name-to-name-plugin

Using the plugin with dCache
----------------------------

To use this plugin with dCache, place the directory containing this
file in /usr/local/share/dcache/plugins/

To enable the plugin, define the following property in dcache.conf:

    xrootdAuthzPlugin=atlas-name-to-name-plugin 

that way is now deprecated. Proper way is:

    xrootd/xrootdPlugins=authn:none,authz:atlas-name-to-name-plugin
    pool/xrootdPlugins=

you also need to add these two lines into dcache.conf:
    lfc_host=myLFChost
    srm_host=mySRMhost
    
this is required for sites supporting rucio:
    site=rc_site
    proper value for "rc_site" you may find here: http://atlas-agis-api.cern.ch/request/service/query/get_se_services/?json&flavour=XROOTD
    
probably never needed still if someone wants to hide a SpaceToken from FAX, just add this variable listing all of the space tokens you want exposed.
    overwriteSE=/pnfs/uchicago.edu/atlasgroupdisk,/pnfs/uchicago.edu/atlasproddisk
    
