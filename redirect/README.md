Authorization and mapping plugin for xrootd4j and dCache
========================================================

This is plugin that makes dCache xrootd door redirect a client in case the requested file can not be found.
The redirector to be used is set in properties like this:

	# add it to the list of plugins, order is important don't change it
    xrootd.plugins=gplazma:gsi,redirector,authz:atlas-name-to-name-plugin
    
    # gives it an upstream redirector to redirect to. The same that is configured in AGIS.
    xrootd.redirector.host = atlas-xrd-central.usatlas.org
    xrootd.redirector.port = 1094