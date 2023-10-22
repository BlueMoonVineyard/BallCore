// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

val scala3Version = "3.3.1"
val circeVersion = "0.14.1"
val scoreBoardLibraryVersion = "2.0.0-RC9"
val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.5.2"
val foliaVersion = "1.20.1-R0.1-SNAPSHOT"

lazy val dependencyPlugin = project
  .in(file("dependency-plugin"))
  .settings(
    name := "BallCoreDependencyPlugin",
    version := "0.1.0-SNAPSHOT",
    assembly / assemblyJarName := "BallCoreDependencyPlugin.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList(ps @ _*) if ps.endsWith(".conf") => MergeStrategy.concat
      case x => (assembly / assemblyMergeStrategy).value(x)
    },

    scalaVersion := scala3Version,

    resolvers += "spigot-repo" at "https://hub.spigotmc.org/nexus/content/repositories/snapshots/",
    resolvers += "paper-repo" at "https://repo.papermc.io/repository/maven-public/",
    resolvers += "codemc-repo" at "https://repo.codemc.io/repository/maven-public/",
    resolvers += "jitpack.io" at "https://jitpack.io/",
    resolvers += "akka" at "https://repo.akka.io/maven",

    libraryDependencies += "dev.folia" % "folia-api" % foliaVersion % "provided", // intransitive()
    libraryDependencies += "com.velocitypowered" % "velocity-api" % "3.2.0-SNAPSHOT" % "provided",
    libraryDependencies += "com.github.plokhotnyuk.rtree2d" %% "rtree2d-core" % "0.11.12",
    libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % "4.0.0",
    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
    libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.40.0.0",
    libraryDependencies += "org.locationtech.jts" % "jts-core" % "1.16.1",
    libraryDependencies += "com.github.megavexnetwork.scoreboard-library" % "scoreboard-library-api" % scoreBoardLibraryVersion,
    libraryDependencies += "com.github.megavexnetwork.scoreboard-library" % "scoreboard-library-implementation" % scoreBoardLibraryVersion,
    libraryDependencies += "com.github.megavexnetwork.scoreboard-library" % "scoreboard-library-v1_20_R1" % scoreBoardLibraryVersion,
    libraryDependencies += "com.github.retrooper.packetevents" % "spigot" % "2.0.0-SNAPSHOT" % "provided",
    libraryDependencies += "org.spongepowered" % "configurate-core" % "4.1.2",
    libraryDependencies += "org.spongepowered" % "configurate-yaml" % "4.1.2",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.2",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.9.0",
    libraryDependencies += "org.typelevel" %% "cats-free" % "2.9.0",
    libraryDependencies += "org.typelevel" %% "cats-effect-cps" % "0.4.0",
    libraryDependencies += "org.tpolecat" %% "skunk-core" % "0.6.0",
    libraryDependencies += "org.tpolecat" %% "skunk-circe" % "0.6.0",

    // currently vendored in lib for a bugfix
    // libraryDependencies += "com.github.stefvanschie.inventoryframework" % "IF" % "0.10.8",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    ),
  )

lazy val commonCode = project
  .in(file("common-code"))
  .dependsOn(dependencyPlugin)
  .settings(
    name := "BallCoreCommonCode",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,

    libraryDependencies += "dev.folia" % "folia-api" % foliaVersion % "provided",
    libraryDependencies += "com.velocitypowered" % "velocity-api" % "3.2.0-SNAPSHOT" % "provided",

    Test / fork := true,
    Test / run / javaOptions += "--enable-preview",
  )

lazy val velocityPlugin = project
  .in(file("velocity-plugin"))
  .dependsOn(dependencyPlugin)
  .dependsOn(commonCode)
  .settings(
    name := "BallCoreVelocity",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,

    libraryDependencies += "com.velocitypowered" % "velocity-api" % "3.2.0-SNAPSHOT" % "provided",

    Test / fork := true,
    Test / run / javaOptions += "--enable-preview",
  )

lazy val actualPlugin = project
  .in(file("."))
  .dependsOn(dependencyPlugin)
  .dependsOn(commonCode)
  .settings(
    name := "BallCore",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "com.github.seeseemelk" % "MockBukkit-v1.20" % "3.18.0" % Test,

    libraryDependencies += "org.locationtech.jts" % "jts-core" % "1.16.1" % "provided",
    libraryDependencies += "dev.folia" % "folia-api" % foliaVersion % "provided", // intransitive()

    Test / fork := true,
    Test / run / javaOptions += "--enable-preview",
  )

lazy val hubPlugin = project
  .in(file("hub-plugin"))
  .dependsOn(dependencyPlugin)
  .dependsOn(commonCode)
  .settings(
    name := "BallCoreHub",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "com.github.seeseemelk" % "MockBukkit-v1.19" % "2.29.0" % Test,

    libraryDependencies += "dev.folia" % "folia-api" % foliaVersion % "provided", // intransitive()

    Test / fork := true,
    Test / run / javaOptions += "--enable-preview",
  )
