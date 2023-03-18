resolvers ++= Resolver.sonatypeOssRepos("snapshots")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.2")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.5.3",
  "com.thesamet.scalapb" %% "compilerplugin" % "0.11.7"
)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.6")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
