import models.{DiffPair, FCInfo}
import org.specs2.mutable.Specification
import io.circe.generic.auto._  //required to provide evidence that complex types can be serialized

class DiffPairSpec extends Specification {
  "DiffPair.apply" should {
    "return None if the two simple parameters are the same" in {
      val test1 = "test"
      val test2 = "test"

      val result = DiffPair(test1, test2)
      result must beNone
    }

    "show the difference between two simple parameters" in {
      val test1 = "test"
      val test2 = "test2"

      val result = DiffPair(test1, test2)
      result must beSome(
        new DiffPair("test","test2")
      )
    }

    "return None if the two complex parameters are the same" in {
      val test1 = Seq(FCInfo(Seq(),"someProduct"))
      val test2 = Seq(FCInfo(Seq(),"someProduct"))

      val result = DiffPair(test1, test2)
      result must beNone
    }

    "show the difference between two complex parameters" in {
      val test1 = Seq(FCInfo(Seq(),"someProduct"))
      val test2 = Seq(FCInfo(Seq(),"someProduct"),FCInfo(Seq(),"someOtherProduct"))

      val result = DiffPair(test1, test2)
      result must beSome(
        new DiffPair(Seq(FCInfo(Seq(),"someProduct")), Seq(FCInfo(Seq(),"someProduct"),FCInfo(Seq(),"someOtherProduct")))
      )
    }
  }
}
