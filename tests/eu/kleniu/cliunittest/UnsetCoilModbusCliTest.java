package eu.kleniu.cliunittest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import eu.kleniu.modbuscli.MyModbusCli;

public class UnsetCoilModbusCliTest {

	@Test
	public void test() {
		String[] args = { "-ip", "192.168.2.50", "-cu", "2700"};
		MyModbusCli app = new MyModbusCli();
		int retVal = app.myMain(args);
		assertEquals(retVal, 0);
	}

}
