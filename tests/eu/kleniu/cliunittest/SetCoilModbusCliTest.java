package eu.kleniu.cliunittest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import eu.kleniu.modbuscli.MyModbusCli;

public class SetCoilModbusCliTest {

	@Test
	public void test() {
		String[] args = { "-ip", "192.168.2.50", "-cs", "2700"};
		MyModbusCli app = new MyModbusCli();
		int retVal = app.myMain(args);
		assertEquals(retVal, 0);
	}

}
