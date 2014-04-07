Authorization and mapping plugin for xrootd4j and dCache
========================================================

This is plugin that makes dCache xrootd door redirect a client in case the requested file can not be found.
The redirector to be used is set in properties like this:

    xrootd.redirector.host = glrd.usatlas.org
    xrootd.redirector.port = 1094