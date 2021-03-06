package au.com.dius.pact.provider.junit

import au.com.dius.pact.core.model.Pact
import au.com.dius.pact.core.model.RequestResponsePact
import au.com.dius.pact.core.model.UrlSource
import au.com.dius.pact.provider.junit.loader.PactFolder
import au.com.dius.pact.provider.junit.loader.PactLoader
import au.com.dius.pact.provider.junit.loader.PactSource
import au.com.dius.pact.provider.junit.loader.PactUrl
import au.com.dius.pact.provider.junit.loader.PactUrlLoader
import au.com.dius.pact.provider.junit.target.Target
import au.com.dius.pact.provider.junit.target.TestTarget
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.InitializationError
import spock.lang.Specification

@SuppressWarnings('UnusedObject')
class PactRunnerSpec extends Specification {

  @Provider('myAwesomeService')
  @PactFolder('pacts')
  class TestClass {
    @TestTarget
    Target target
  }

  @Provider('Bob')
  class NoSourceTestClass {

  }

  @Provider('Bob')
  @PactUrl(urls = ['http://doesntexist/I%20hope?'])
  class FailsTestClass {

  }

  @Provider('Bob')
  @PactFolder('pacts')
  class NoPactsTestClass {

  }

  @Provider('Bob')
  @PactFolder('pacts')
  @IgnoreNoPactsToVerify
  class NoPactsIgnoredTestClass {

  }

  @Provider('Bob')
  @PactFolder('pacts')
  @PactSource(PactUrlLoader)
  class BothPactSourceAndPactLoaderTestClass {

  }

  @Provider('Bob')
  @PactUrl(urls = ['http://doesnt%20exist/I%20hope?'])
  @IgnoreNoPactsToVerify(ignoreIoErrors = 'true')
  class DoesNotFailsTestClass {

  }

  static class PactLoaderWithConstructorParameter implements PactLoader {

    private final Class clazz

    PactLoaderWithConstructorParameter(Class clazz) {
      this.clazz = clazz
    }

    @Override
    List<Pact> load(String providerName) throws IOException {
      [
        new RequestResponsePact(new au.com.dius.pact.core.model.Provider('Bob'),
          new au.com.dius.pact.core.model.Consumer(), [])
      ]
    }

    @Override
    au.com.dius.pact.core.model.PactSource getPactSource() {
      new UrlSource('url')
    }
  }

  static class PactLoaderWithDefaultConstructor implements PactLoader {

    @Override
    List<Pact> load(String providerName) throws IOException {
      [
        new RequestResponsePact(new au.com.dius.pact.core.model.Provider('Bob'),
          new au.com.dius.pact.core.model.Consumer(), [])
      ]
    }

    @Override
    au.com.dius.pact.core.model.PactSource getPactSource() {
      new UrlSource('url')
    }
  }

  @Provider('Bob')
  @PactSource(PactLoaderWithConstructorParameter)
  class PactLoaderWithConstructorParameterTestClass {
    @TestTarget
    Target target
  }

  @Provider('Bob')
  @PactSource(PactLoaderWithDefaultConstructor)
  class PactLoaderWithDefaultConstructorClass {
    @TestTarget
    Target target
  }

  def 'PactRunner throws an exception if there is no @Provider annotation on the test class'() {
    when:
    new PactRunner(PactRunnerSpec).run(new RunNotifier())

    then:
    InitializationError e = thrown()
    e.causes*.message ==
      ['Provider name should be specified by using au.com.dius.pact.provider.junit.Provider annotation']
  }

  def 'PactRunner throws an exception if there is no pact source'() {
    when:
    new PactRunner(NoSourceTestClass).run(new RunNotifier())

    then:
    InitializationError e = thrown()
    e.causes*.message == ['Exactly one pact source should be set']
  }

  def 'PactRunner throws an exception if the pact source throws an IO exception'() {
    when:
    new PactRunner(FailsTestClass).run(new RunNotifier())

    then:
    InitializationError e = thrown()
    e.causes.every { IOException.isAssignableFrom(it.class) }
  }

  def 'PactRunner throws an exception if there are no pacts to verify'() {
    when:
    new PactRunner(NoPactsTestClass).run(new RunNotifier())

    then:
    InitializationError e = thrown()
    e.causes*.message == ['Did not find any pact files for provider Bob']
  }

  def 'PactRunner only initializes once if run() is called multiple times'() {
    when:
    def runner = new PactRunner(TestClass)
    runner.run(new RunNotifier())
    def children1 = runner.children.clone()

    runner.run(new RunNotifier())
    def children2 = runner.children.clone()

    then:
    children1 == children2
  }

  def 'PactRunner does not throw an exception if there are no pacts to verify and @IgnoreNoPactsToVerify'() {
    when:
    new PactRunner(NoPactsIgnoredTestClass)

    then:
    notThrown(InitializationError)
  }

  @SuppressWarnings('LineLength')
  def 'PactRunner does not throw an exception if there is an IO error and @IgnoreNoPactsToVerify has ignoreIoErrors set'() {
    when:
    new PactRunner(DoesNotFailsTestClass)

    then:
    notThrown(InitializationError)
  }

  def 'PactRunner throws an exception if there is both a pact source and pact loader annotation'() {
    when:
    new PactRunner(BothPactSourceAndPactLoaderTestClass).run(new RunNotifier())

    then:
    InitializationError e = thrown()
    e.causes*.message == ['Exactly one pact source should be set']
  }

  def 'PactRunner handles a pact source with a pact loader that takes a class parameter'() {
    when:
    def runner = new PactRunner(PactLoaderWithConstructorParameterTestClass)
    runner.run(new RunNotifier())

    then:
    !runner.children.empty
  }

  def 'PactRunner handles a pact source with a pact loader that does not takes a class parameter'() {
    when:
    def runner = new PactRunner(PactLoaderWithDefaultConstructorClass)
    runner.run(new RunNotifier())

    then:
    !runner.children.empty
  }

  def 'PactRunner loads the pact loader class from the pact loader associated with the pact loader annotation'() {
    when:
    def runner = new PactRunner(TestClass)
    runner.run(new RunNotifier())

    then:
    !runner.children.empty
  }

}
