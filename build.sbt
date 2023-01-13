ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

ThisBuild / resolvers += "Maven Central Server" at "https://repo1.maven.org/maven2"

resolvers ++= Resolver.sonatypeOssRepos("snapshots")

//versions
val AwsVersion = "2.19.4"
val TranzactIOVersion = "2.0.0"
val DoobieVersion = "0.12.1"

val aws = Seq(
  "software.amazon.awssdk" % "dynamodb" % AwsVersion
)

val grpc = Seq(
  "io.grpc" % "grpc-netty" % "1.41.0",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion)

val db = Seq(
  "io.github.gaelrenoux" %% "tranzactio" % TranzactIOVersion,
  "org.tpolecat" %% "doobie-core" % DoobieVersion
)

val dependencies = aws ++ grpc ++ db

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
  .settings(libraryDependencies ++= dependencies)
