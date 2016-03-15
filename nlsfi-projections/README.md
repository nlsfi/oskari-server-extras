# Projection transform helper for Finnish projections

Wraps a library for Finnish coordinate system transforms so it's usable with Oskari-server.
The compiled lib is available in
 oskari.org [releases Maven repository](http://oskari.org/nexus/content/repositories/releases) (no source available):

    <dependency>
        <groupId>fi.nls.projektio</groupId>
        <artifactId>java-projections</artifactId>
        <version>2012.0.0</version>
    </dependency>

See fi.nls.oskari.NLSFIProjections class for supported projections.

Configured for use in oskari-server with oskari-ext.properties:

    projection.library.class=fi.nls.oskari.NLSFIPointTransformer

With the property the `Coordinates` action route uses this library as default transform.

Note! This module is coupled with oskari-server version. The dependency uses 1.36.0-SNAPSHOT or later version of oskari-server
 at compile time, but doesn't include oskari-server modules as runtime dependency. This should be used as an additional dependency in
  oskari-server-extension or similar setup to provide the required runtime dependencies. Include in your oskari-server-extension with:

        <dependency>
            <groupId>fi.nls.oskari.extras</groupId>
            <artifactId>nlsfi-projections</artifactId>
            <version>1.0</version>
        </dependency>

The functionality uses java.awt so you might need to set system property `java.awt.headless=true`
