// addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.9.0")
// https://www.scala-sbt.org/sbt-native-packager/gettingstarted.html#your-first-package
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.1")
resolvers += Resolver.url("bintray-sbt-plugins", url("https://dl.bintray.com/sbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.0")
