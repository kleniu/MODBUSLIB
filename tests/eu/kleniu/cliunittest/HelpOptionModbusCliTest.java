package eu.kleniu.cliunittest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import eu.kleniu.modbuscli.MyModbusCli;

public class HelpOptionModbusCliTest {

	@Test
	public void test() {
		String[] args = { "-h"};
		MyModbusCli app = new MyModbusCli();
		int retVal = app.myMain(args);
		assertEquals("-h reurns nonzero value.", retVal, 0);
	}

}
