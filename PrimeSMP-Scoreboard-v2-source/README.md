# PrimeSMP Scoreboard v2

Paper 1.21.11 scoreboard plugin for PrimeSMP.

## Features

- Animated PrimeSMP title
- Vault economy (works with EssentialsX Economy through Vault)
- Vault primary rank
- Kills/deaths
- Persistent shards
- Persistent playtime
- Ping
- Bukkit team
- Online/max players
- Paper TPS
- `/psb on`
- `/psb off`
- `/psb reload`
- Updates lines without recreating the scoreboard every update
- GitHub Actions Maven build

## Build

Requires Java 21 and Maven:

```bash
mvn clean package
```

The JAR will be:

```text
target/PrimeSMP-Scoreboard-v2.jar
```

## Server dependencies

Install:

- Paper 1.21.11
- Vault
- EssentialsX (for economy)
- A Vault-compatible permissions plugin such as LuckPerms (for ranks)

Vault and the economy/permission providers are runtime dependencies.

## GitHub

Push this project to GitHub. The workflow in `.github/workflows/build.yml`
automatically builds the JAR and uploads it as a GitHub Actions artifact.
