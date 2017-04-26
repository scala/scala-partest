object VersionKeys {
  import sbt.settingKey

  // To facilitate scripted build of all modules, used in scala/scala's bootstrap script
  val scalaXmlVersion   = settingKey[String]("Version to use for the scala-xml dependency.")
}
