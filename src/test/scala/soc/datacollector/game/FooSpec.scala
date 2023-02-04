package soc.datacollector.game

import zio.test.Assertion.isUnit
import zio.test.{DefaultRunnableSpec, ZSpec, assertM}

object FooSpec extends DefaultRunnableSpec{

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = fooTest

  val fooTest = testM("foo") {
    assertM(zio.console.putStrLn("foo"))(isUnit)
  }
}
