ATLAS dCache xRootD door plugins
==============================================

This package currently contains three plugins:

monitor plugin

	intercepts xRootD protocol messages exchanged between
	xRootD client and dCache xRootD door, extracts monitoring
	information, packages it in xRootD standard format and sends
	it using UDP to UCSD collector.
    
redirector plugin

    enables upstream redirection. in case a file was not found at the site,
    the client gets redirected to a configurable "upstream" redirector. 
    
Name2Name plugin
    
    when a client requests a file given as a gLFN, it translates it to a PFN.
    an offical repository for this plugin is here: 
    http://git.cern.ch/pubweb/FAX.git
    