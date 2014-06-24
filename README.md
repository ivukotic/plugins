ATLAS dCache xRootD door plugins
==============================================

This package currently contains two plugins:

monitor plugin

	intercepts xRootD protocol messages exchanged between
	xRootD client and dCache xRootD door, extracts monitoring
	information, packages it in xRootD standard format and sends
	it using UDP to UCSD collector.
    
redirector plugin

    enables upstream redirection. in case a file was not found at the site,
    the client gets redirected to a configurable "upstream" redirector. 