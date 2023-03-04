// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

val scala3Version = "3.2.2-RC1"
val circeVersion = "0.14.1"

lazy val dependencyPlugin = project
  .in(file("dependency-plugin"))
  .settings(
    name := "BallCoreDependencyPlugin",
    version := "0.1.0-SNAPSHOT",
    assembly / assemblyJarName := "BallCoreDependencyPlugin.jar",

    scalaVersion := scala3Version,

    resolvers += "spigot-repo" at "https://hub.spigotmc.org/nexus/content/repositories/snapshots/",
    resolvers += "paper-repo" at "https://repo.papermc.io/repository/maven-public/",
    resolvers += "codemc-repo" at "https://repo.codemc.io/repository/maven-public/",
    resolvers += "jitpack.io" at "https://jitpack.io/",

    libraryDependencies += "org.spigotmc" % "spigot-api" % "1.19-R0.1-SNAPSHOT" % "provided", // intransitive()
    libraryDependencies += "io.papermc.paper" % "paper-api" % "1.19-R0.1-SNAPSHOT" % "provided", // intransitive()
    libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % "4.0.0",
    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
    libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.40.0.0",
    // currently vendored in lib for a bugfix
    // libraryDependencies += "com.github.stefvanschie.inventoryframework" % "IF" % "0.10.8",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),
  )

lazy val actualPlugin = project
  .in(file("."))
  .dependsOn(dependencyPlugin)
  .settings(
    name := "BallCore",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,

    libraryDependencies += "io.papermc.paper" % "paper-api" % "1.19-R0.1-SNAPSHOT" % "provided", // intransitive()
    libraryDependencies += "me.filoghost.holographicdisplays" % "holographicdisplays-api" % "3.0.0" % "provided",
    libraryDependencies += "com.github.Slimefun" % "Slimefun4" % "RC-28" % "provided" intransitive(),
  )
