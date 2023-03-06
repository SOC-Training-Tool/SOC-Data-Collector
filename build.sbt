ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

ThisBuild / resolvers += "Maven Central Server" at "https://repo1.maven.org/maven2"

resolvers ++= Resolver.sonatypeOssRepos("snapshots")

//versions
val AwsVersion = "2.19.4"
val TranzactIOVersion = "2.0.0"
val DoobieVersion = "0.12.1"
val ZIOVersion = "1.0.15"

val aws = Seq(
  "software.amazon.awssdk" % "dynamodb" % AwsVersion
)

val grpc = Seq(
  "io.grpc" % "grpc-netty" % "1.41.0",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion)

val db = Seq(
  "io.github.gaelrenoux" %% "tranzactio" % TranzactIOVersion,
  "org.tpolecat" %% "doobie-core" % DoobieVersion,
  "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
  "com.zaxxer" % "HikariCP" % "5.0.1",
  "org.postgresql" % "postgresql" % "42.5.1"
)

val zioTest = Seq(
  "dev.zio" %% "zio-test"          % ZIOVersion % "test,it",
  "dev.zio" %% "zio-test-sbt"      % ZIOVersion % "test,it",
)
val zioTestContainers = Seq(
  "io.github.scottweaver" %% "zio-testcontainers-postgresql" % "0.9.0",
  "io.github.scottweaver" %% "zio-db-migration-aspect" % "0.9.0"
).map(_ % "it")

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

val dependencies = grpc ++ db ++ zioTest ++ zioTestContainers

lazy val root = (project in file("."))
  .settings(name := "SOCDataCollector")
  .settings(
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
    ),
    Compile / PB.protoSources := Seq(
      (ThisBuild / baseDirectory).value / "src" / "main" / "protobuf"
    ))
  .settings(Test / fork := true)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / fork := true)
  .settings(libraryDependencies ++= dependencies)
  .settings(libraryDependencies += "dev.zio" %% "izumi-reflect" % "1.0.0-M2")
  .settings(coverageExcludedPackages := "<empty>;.*io.soc.*")
