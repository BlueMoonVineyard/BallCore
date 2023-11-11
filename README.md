# BallCore

provides core mechanics to civcubed

## dependency plugin

the plugin with all of the third party dependencies (for both velocity and folia/paper)
this is the `dependencyPlugin/assembly` target, it'll be in `.target/<scala version>/BallCoreDependencyPlugin.jar`
needed for all other plugins

## common code

shared first party code between the other plugins (for both velocity and folia/paper)
this is the `commonCode/package` target
needed for all other plugins

## hub plugin

plugin for the hub server (paper)
this is the `hubPlugin/package` target

## velocity plugin

plugin for the proxy (velocity)
this is the `velocityPlugin/package` target

the HTTPS API used by the discord bot lives here

obligate postgresql database required, configuration will be read from `plugins/ballcorevelocityplugin/config.yaml`
```yaml
secrets:
    api-key: "an api key"
database:
    host: "localhost"
    port: "5432"
    user: "civcubed"
    database: "civcubed"
    password: "password"
```

## main plugin

plugin for the main server (folia, but paper compatibility w/ folia APIs should work fine)
this is the `actualPlugin/package` target

obligate postgresql database required, configuration will be read from `plugins/BallCore/config.yaml`
```yaml
database:
    host: "localhost"
    port: "5432"
    user: "civcubed"
    database: "civcubed"
    password: "password"
```
