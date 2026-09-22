# Lerd for PhpStorm

> Your [Lerd](https://lerd.sh) site, inside the IDE you already have open. Its
> logs, its PHP version, its workers, its services, its queries and everything
> it dumped, without a browser tab.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![PhpStorm](https://img.shields.io/badge/PhpStorm-2025.2%2B-000?logo=phpstorm)](https://www.jetbrains.com/phpstorm/)
[![lerd](https://img.shields.io/badge/lerd-lerd.sh-ff2d20)](https://lerd.sh)
[![Reddit](https://img.shields.io/badge/Reddit-r%2Flerd-ff2d20?logo=reddit)](https://reddit.com/r/lerd)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/5JK54s7xCC)

The project you open resolves to exactly one Lerd site, and everything the
dashboard offers for *that* site comes with it. The plugin talks to nothing but
the Lerd already running on your machine, and gets out of the way entirely for
a project Lerd does not know.

## Features

### Always visible

- 🔴 **Quiet until it matters.** A status bar widget carries the site's domain
  and PHP version, greys out when Lerd is stopped, and is not drawn at all for
  a project that is not a Lerd site — so the plugin costs nothing while you
  work on something else.

- 🖱️ **The six verbs worth reaching for.** Click the widget: open in browser,
  restart, HTTPS on or off, pause, switch PHP version, open the dashboard.

- 🔌 **No polling.** State arrives over the dashboard's own websocket, so
  stopping Lerd in a terminal greys the widget out within a second.

### Site

- 🧭 **The overview, in three columns.** Domain, framework, branch and path;
  the PHP and Node version pickers; a toggle per worker; a row per service.
  The columns reflow to two and then one as the tool window narrows, so it
  reads the same docked to the side as along the bottom.

- 🗄️ **Open in Database.** Opens the service in the IDE's own Database tool
  window, lands on this site's database rather than the server node, and fills
  in the password Lerd already knows. It reuses the connection Lerd wrote into
  `.idea/dataSources.xml` instead of adding a second one beside it.

- 🐚 **Shells where you want them.** A shell inside the site's container, or a
  client shell into a service through the binaries Lerd ships: `mysql`, `psql`,
  `redis-cli`, `mongosh`.

- 🩺 **Site doctor.** Lerd's own findings for the site, and nothing when every
  check passes.

### Logs

- 🧯 **A framework log is a list of problems, not a wall.** The same failure two
  hundred times is one row with a count, a first-seen and a last-seen, and the
  full record beside it. Ids and quoted values are collapsed, so one error does
  not split into two hundred.

- 📡 **Everything else is a live tail.** PHP-FPM or FrankenPHP, every worker,
  every service the site uses, and nginx, resumed from where it left off when a
  connection drops.

- 🔗 **A path in a stack trace is a link.** Both panes are IDE consoles, so an
  exception in the queue worker is one click from the line that threw it. This
  is the whole reason for doing any of it inside the IDE rather than beside it.

### N+1 & Slow

- 🐌 **The queries worth fixing, worst first.** Repeats inside a single request
  and anything over the slow threshold, read from real captured traffic rather
  than from the code.

- ▶️ **Runnable.** Double-click a finding and the statement opens as a `.sql`
  scratch against the site's database, one keystroke from executing.

### Dumps

- 🔍 **Every lens the dashboard has.** Dumps, queries, jobs, views, mail, cache,
  events, HTTP, logs, exceptions and messages, each a tab with its count, and
  only drawn when it has something in it.

- 🧵 **A trace you can read.** The first frame outside `vendor/` is pulled to
  the top as where it came from, every frame is linked, and vendor frames
  recede instead of burying the two that matter.

- 🧪 **Test runs stay out of the way.** Captured and tagged rather than dropped,
  so a dump added to diagnose a failing test is one toggle away.

### The IDE's own settings, kept honest

- 🐘 **PHP follows the site.** The language level, the CLI interpreter (pointed
  at Lerd's `php` shim) and the PHP server entry for Xdebug all track whatever
  Lerd actually serves the site with. One direction on purpose: Lerd owns what
  runs the site, and a language level changed in the IDE is an inspection
  setting, not a reason to rebuild a container.

- 🌳 **Worktrees are first class.** Open a git worktree as its own project and
  it resolves to its parent site plus the branch, with its own units and logs.

## Requirements

PhpStorm 2025.2 or newer, and Lerd running on the same machine. No account, no
token, no daemon of its own.

## Install

From the JetBrains Marketplace, or build it yourself:

```sh
./install-local.sh
```

That drops the plugin into the newest `~/.local/share/JetBrains/PhpStorm*`
directory it finds; set `LERD_PLUGINS_DIR` to aim it elsewhere. PhpStorm loads
it on restart, and removing the `lerd-phpstorm` folder uninstalls it.

## Privacy and permissions

Everything happens over loopback to the Lerd already running as your user, on
`127.0.0.1:7073` — the same API the dashboard in your browser uses, which is
why neither needs credentials. Nothing is sent anywhere else and there is no
telemetry. Every change it makes is a request to Lerd's own API, and a command
a framework definition marks as destructive asks first.

When Lerd is stopped the plugin reads `~/.local/share/lerd/sites.yaml` so it can
still tell you that this project is a Lerd site and that Lerd is not running.

## Development

```sh
./gradlew test          # unit tests
./gradlew runIde        # a sandbox PhpStorm with the plugin loaded
./gradlew verifyPlugin  # the JetBrains plugin verifier, 2025.2 through 2026.3
```

Needs a JDK 21 or newer; the IDE it compiles against is downloaded by the
build, so nothing touches your installed PhpStorm.

The parts that decide anything are plain Kotlin with no IDE dependency and are
covered by tests: `api/` speaks to the daemon, `site/` maps the open project
onto a site, `logs/` groups and renders what comes back, `db/` turns a
connection URL into something the Database tools can open. `toolwindow/` and
`widget/` only draw.

## Licence

MIT.
