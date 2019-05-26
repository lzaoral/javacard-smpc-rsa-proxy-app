# JavaCard SMPC RSA Proxy

Proxy application providing the interface between the reference implementation
and the on-card applet.

The `CardManager.java` and `Util.java` files are based on the [JavaCard Template project with Gradle](https://github.com/crocs-muni/javacard-gradle-template-edu)
project.

## Requirements

* Apache Maven
* Java Development Kit 8

## Building

```
mvn package
```

## Usage

```
java -jar target/smpc_rsa_proxy-jar-with-dependencies.jar [mode] [action]
```

## Stress Testing

The `*_test.sh` files can be used to test the applets on given smart card and to
determine the success rate of usable moduli generation of the given smart card.
Expects the reference implementation `smpc_rsa` and `message.txt` files in the
same folder. If the project has not yet been built, runs the `mvn package`
command automatically.

## Usage on Linux

The JDK on Linux uses the `libpcsclite.so` library to communicate with
smart cards and expects this library in a specific path. However, some
distributions (e.g. Ubuntu) use different paths. Run the application with
```
 -Dsun.security.smartcardio.library={path_to_libpcsclite}
```
flag or set the environmental variable `JAVA_TOOL_OPTIONS` to this flag
to use the correct library path.

