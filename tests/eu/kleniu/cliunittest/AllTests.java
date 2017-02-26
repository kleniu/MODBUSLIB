package eu.kleniu.cliunittest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ HelpOptionModbusCliTest.class, ReadCoilsModbusCliTest.class, ReadRegistersModbusCliTest.class,
		SetCoilModbusCliTest.class, ToggleCoilModbusCliTest.class, UnsetCoilModbusCliTest.class,
		WriteRegisterModbusCliTest.class })

public class AllTests {

}
