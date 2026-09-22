# lerd for PhpStorm

> Manage your [lerd](https://lerd.sh) environment from the IDE you already have
> open. The project resolves to its site, and its logs, PHP version, workers and
> services come with it.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![lerd](https://img.shields.io/badge/lerd-lerd.sh-ff2d20)](https://lerd.sh)

The plugin talks to nothing but the lerd already running on your machine, over
the loopback API on `127.0.0.1:7073`. No account, no token, no daemon of its
own. When lerd is stopped it says so and gets out of the way.

## Status

Early. The client and its model layer are in place; the status bar widget, the
tool window and the log console are being built on top.

## Building

```bash
./gradlew test          # unit tests
./gradlew runIde        # a sandbox PhpStorm with the plugin loaded
./gradlew verifyPlugin  # the JetBrains plugin verifier
./install-local.sh      # build and drop it into the PhpStorm you actually use
```

`install-local.sh` replaces the plugin in the newest `~/.local/share/JetBrains/PhpStorm*`
directory it finds; set `LERD_PLUGINS_DIR` to aim it somewhere else. PhpStorm loads
the new build on restart. Remove the `lerd-phpstorm` folder from that directory to
uninstall.

Requires a JDK 21 or newer. The IDE the plugin compiles against is downloaded by
the build; nothing touches your installed PhpStorm.

## Licence

MIT.
