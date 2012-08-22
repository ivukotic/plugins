ATLAS dCache xRootD door plugins
==============================================

This package currently contains two plugins:

atlas name-to-name plugin
	
	uses LFC to translate ATLAS global logical filenames
	to local Physical filenames

monitor plugin

	intercepts xRootD protocol messages exchanged between
	xRootD client and dCache xRootD door, extracts monitoring
	information, packages it in xRootD standard format and sends
	it using UDP to UCSD collector.