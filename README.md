dCache location service
=======================

The dCache location service provides a minimal interface to obtain
the location of a file in dCache. The location of a file is expressed
as a pool name, but may be translated through some other form
using a series of regex substitutions.

The service is packaged as a dCache plugin.

To install the plugin, unpack the tarball in
/usr/local/share/dcache/plugins/:

    mkdir -p /usr/local/share/dcache/plugins/
    cd /usr/local/share/dcache/plugins/
    tar xzf /tmp/location-service-2.6.0.tar.gz

The service needs to be instantiated in a dCache domain by adding it
to the layout file:

    [someDomain/location]

Please see location.properties for possible configuration properties.
