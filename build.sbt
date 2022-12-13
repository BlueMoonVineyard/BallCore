val scala3Version = "3.2.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "BallCore",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    resolvers += "spigot-repo" at "https://hub.spigotmc.org/nexus/content/repositories/snapshots/",
    resolvers += "jitpack.io" at "https://jitpack.io/",

    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "org.spigotmc" % "spigot-api" % "1.19-R0.1-SNAPSHOT" % "provided" intransitive(),
    libraryDependencies += "com.github.Slimefun" % "Slimefun4" % "RC-28" % "provided" intransitive(),
  )
