package au.com.dius.pact.consumer.specs2

import au.com.dius.pact.consumer.PactVerificationResult.{Error, ExpectedButNotReceived, Mismatches, Ok, PartialMismatch, UnexpectedRequest}
import au.com.dius.pact.consumer.model.MockProviderConfig
import au.com.dius.pact.consumer.specs2.PactFragmentBuilder.PactWithAtLeastOneRequest
import au.com.dius.pact.consumer.{ConsumerPactRunnerKt, MockServer, PactTestExecutionContext, PactTestRun, PactVerificationResult}
import au.com.dius.pact.core.model.{Consumer, PactSpecVersion, RequestResponsePact}
import org.specs2.execute.{AsResult, Failure, Result, Success}
import org.specs2.specification.core.Fragment
import org.specs2.specification.create.FragmentsFactory

import scala.collection.JavaConverters._

trait PactSpec extends FragmentsFactory {

  val provider: String
  val consumer: String
  val providerState: Option[String] = None

  def uponReceiving(description: String) = {
    val pact = PactFragmentBuilder(new Consumer(consumer)).hasPactWith(provider)
    if (providerState.isDefined) pact.given(providerState.get).uponReceiving(description)
    else pact.uponReceiving(description)
  }

  implicit def liftFragmentBuilder(builder: PactWithAtLeastOneRequest): ReadyForTest = {
    new ReadyForTest(builder.asPact())
  }

  implicit def pactVerificationAsResult: AsResult[PactVerificationResult] = {
    new AsResult[PactVerificationResult] {
      def asResult(test: => PactVerificationResult): Result = {
        test match {
          case r: Ok => r.getResult.asInstanceOf[Result]
          case r: PartialMismatch => Failure(PrettyPrinter.printProblem(r.getMismatches.asScala))
          case e: Mismatches => Failure(PrettyPrinter.print(e.getMismatches.asScala))
          case e: Error => Failure(m = s"Test failed with an exception: ${e.getError.getMessage}",
            stackTrace = e.getError.getStackTrace.toList)
          case u: UnexpectedRequest => Failure(PrettyPrinter.printUnexpected(List(u.getRequest)))
          case u: ExpectedButNotReceived => Failure(PrettyPrinter.printMissing(u.getExpectedRequests.asScala))
        }
      }
    }
  }

  class ReadyForTest(pactFragment: RequestResponsePact) {
    def withConsumerTest(test: (MockServer, PactTestExecutionContext) => Result): Fragment = {
      val config = MockProviderConfig.createDefault(PactSpecVersion.V3)
      val description = s"Consumer '${pactFragment.getConsumer.getName}' has a pact with Provider '${pactFragment.getProvider.getName}': " +
        pactFragment.getInteractions.asScala.map { i => i.getDescription }.mkString(" and ") + sys.props("line.separator")

      fragmentFactory.example(description, {
        val f: PactTestRun[Result] = (m: MockServer, c: PactTestExecutionContext) => test(m, c)
        ConsumerPactRunnerKt.runConsumerTest(pactFragment, config, f)
      })
    }
  }

  case class ConsumerTestFailed(r: Result) extends RuntimeException
}
