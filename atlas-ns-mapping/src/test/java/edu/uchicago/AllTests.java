package edu.uchicago;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ RucioN2NTest.class, TranslateRucioNames.class })
public class AllTests {

}
