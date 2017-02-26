package eu.kleniu.libunittest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import eu.kleniu.modbuslib.ModbusTcpClient;

class MyClient extends ModbusTcpClient {
	private String tag;

	public MyClient(int debugLevel, String tag) {
		super(debugLevel);
		this.tag = tag;
	}

	@Override
	public void displayLog(String debMsg) {
		System.out.println( this.tag + " " + debMsg );
	}
	
}


public class TestModbusTcpClient {

	// change this constants to your specific values
	String ipAddress  = "192.168.2.50";
	String dnsName    = "myplc.home";
	int    portNumber = 502; 
	
	
	@Test
	public void test001ConnectionUsingIP() {
		int retVal = 0;
		ModbusTcpClient myClient = new MyClient(9, "TEST001");
		
		// Causes this test method to fail if the given value is null.
		assertNotNull("I cannot create ModbusTcpClient object.", myClient);
		
		retVal = myClient.mbtcSetup(ipAddress, portNumber);
		// Causes this test method to fail if the given two values are not equal.
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcConnect();
		assertEquals("I cannot establish connection to " + ipAddress + ":" + Integer.toString(portNumber), 
				     retVal, 
				     ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcDisconnect();
		assertEquals("I cannot close connection to " + ipAddress + ":" + Integer.toString(portNumber), 
			     retVal, 
			     ModbusTcpClient.RET_OK);
	}

	/*
	@Test
	public void test002ConnectionUsingDNSName() {
		int retVal = 0;
		ModbusTcpClient myClient = new MyClient(0, "TEST002");
		
		// Causes this test method to fail if the given value is null.
		assertNotNull("I cannot create ModbusTcpClient object.", myClient);
		
		retVal = myClient.mbtcSetup(dnsName, portNumber);
		// Causes this test method to fail if the given two values are not equal.
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcConnect();
		assertEquals("I cannot establish connection to " + dnsName + ":" + Integer.toString(portNumber), 
				     retVal, 
				     ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcDisconnect();
		assertEquals("I cannot close connection to " + dnsName + ":" + Integer.toString(portNumber), 
			     retVal, 
			     ModbusTcpClient.RET_OK);
	}
	*/
	
	@Test
	public void test003ConnectionUsingDifferentTCPParams() {
		int retVal = 0;
		ModbusTcpClient myClient = new MyClient(9, "TEST003");
		
		// Causes this test method to fail if the given value is null.
		assertNotNull("I cannot create ModbusTcpClient object.", myClient);
		
		retVal = myClient.mbtcSetup(ipAddress, portNumber, 4, 3, 2000, 1500, 0, 0);
		// Causes this test method to fail if the given two values are not equal.
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcConnect();
		assertEquals("I cannot establish connection to " + dnsName + ":" + Integer.toString(portNumber), 
				     retVal, 
				     ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcDisconnect();
		assertEquals("I cannot close connection to " + dnsName + ":" + Integer.toString(portNumber), 
			     retVal, 
			     ModbusTcpClient.RET_OK);
	}
	
	@Test
	public void test004ReadCoils() {
		int retVal = 0;
		boolean[] coilStatus = null;
		
		ModbusTcpClient myClient = new MyClient(9, "TEST004");
		
		// Causes this test method to fail if the given value is null.
		assertNotNull("I cannot create ModbusTcpClient object.", myClient);
		
		retVal = myClient.mbtcSetup(ipAddress, portNumber);
		// Causes this test method to fail if the given two values are not equal.
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcConnect();
		assertEquals("I cannot establish connection to " + dnsName + ":" + Integer.toString(portNumber), 
				     retVal, 
				     ModbusTcpClient.RET_OK);
		
		coilStatus = myClient.mbtcReadCoils(0, 16, 0);
		assertNotNull("I cannot read coil status.", coilStatus);
		
		
		retVal = myClient.mbtcDisconnect();
		assertEquals("I cannot close connection to " + dnsName + ":" + Integer.toString(portNumber), 
			     retVal, 
			     ModbusTcpClient.RET_OK);
	}
	
	@Test
	public void test005ReadRegisters() {
		int retVal = 0;
		int[] regStatus = null;
		// Date and Time registers in FATEK PLC memory map
		// FATEK   Description
		// =====   ===========
		// R4128 - Seconds
		// R4129 - Minutes
		// R4130 - Hours
		// R4131 - Day
		// R4132 - Month
		// R4133 - Year
		// R4134 - Week
		
		
		ModbusTcpClient myClient = new MyClient(9, "TEST005");
		
		// Causes this test method to fail if the given value is null.
		assertNotNull("I cannot create ModbusTcpClient object.", myClient);
		
		retVal = myClient.mbtcSetup(ipAddress, portNumber);
		// Causes this test method to fail if the given two values are not equal.
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);
		
		retVal = myClient.mbtcConnect();
		assertEquals("I cannot establish connection to " + dnsName + ":" + Integer.toString(portNumber), 
				     retVal, 
				     ModbusTcpClient.RET_OK);
		
		regStatus = myClient.mbtcReadRegisters(4128, 7, 0);
		assertNotNull("I cannot read register values.", regStatus);
		
		System.out.printf("TEST005 >>>>>>>>>>>> Date/Time from PLC: %02d:%02d:%02d 20%02d.%02d.%02d week: %d\n",regStatus[2],regStatus[1],regStatus[0],regStatus[5],regStatus[4],regStatus[3],regStatus[6] );
		
		retVal = myClient.mbtcDisconnect();
		assertEquals("I cannot close connection to " + dnsName + ":" + Integer.toString(portNumber), 
			     retVal, 
			     ModbusTcpClient.RET_OK);
	}
	
	@Test
	public void test006SetSingleCoil() {
		int retVal = 0;
		// Coils associated with markers in FATEK PLC memory map
		// FATEK    MODBUS Address Description
		// =====    ============== ==============
		// M0-M2001 02000-04001    Markers/Internal Relays
		
		int coilToWrite = 2700; // M700
		
		ModbusTcpClient myClient = new MyClient(9, "TEST006");
		
		// Causes this test method to fail if the given value is null.
		assertNotNull("I cannot create ModbusTcpClient object.", myClient);
		
		retVal = myClient.mbtcSetup(ipAddress, portNumber);
		// Causes this test method to fail if the given two values are not equal.
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);


		retVal = myClient.mbtcConnect();
		assertEquals("I cannot establish connection to " + dnsName + ":" + Integer.toString(portNumber), 
				     retVal, 
				     ModbusTcpClient.RET_OK);
		
		boolean coilStatus[] = myClient.mbtcReadCoils(coilToWrite, 1, 0);
		assertNotNull("I cannot read coil state.", coilStatus);
		
		boolean desiredStatus = true;
		if ( coilStatus[0] ) desiredStatus = false;
		
			
		retVal = myClient.mbtcWriteSingleCoil(coilToWrite, desiredStatus, 0);
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);
		
		
		retVal = myClient.mbtcDisconnect();
		assertEquals("I cannot close connection to " + dnsName + ":" + Integer.toString(portNumber), 
			     retVal, 
			     ModbusTcpClient.RET_OK);
	}
	
	@Test
	public void test007SetSingleRegister() {
		int retVal = 0;
		// Registers associated with Retentive in FATEK PLC memory map
		// FATEK    MODBUS Address Description
		// =====    ============== ==============
		// R0~R2999 0-2999         Holding Register
		
		int regToWrite = 0; // R0
		
		ModbusTcpClient myClient = new MyClient(9, "TEST007");
		
		// Causes this test method to fail if the given value is null.
		assertNotNull("I cannot create ModbusTcpClient object.", myClient);
		
		retVal = myClient.mbtcSetup(ipAddress, portNumber);
		// Causes this test method to fail if the given two values are not equal.
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);


		retVal = myClient.mbtcConnect();
		assertEquals("I cannot establish connection to " + dnsName + ":" + Integer.toString(portNumber), 
				     retVal, 
				     ModbusTcpClient.RET_OK);
		
		int regStatus[] = myClient.mbtcReadRegisters(regToWrite, 1, 0);
		assertNotNull("I cannot read register value.", regStatus);
		
		int desiredStatus = regStatus[0] + 1;
		
			
		retVal = myClient.mbtcWriteSingleRegister(regToWrite, desiredStatus, 0);
		assertEquals("I cannot setup TCP connection.", retVal, ModbusTcpClient.RET_OK);
		
		
		retVal = myClient.mbtcDisconnect();
		assertEquals("I cannot close connection to " + dnsName + ":" + Integer.toString(portNumber), 
			     retVal, 
			     ModbusTcpClient.RET_OK);
	}
}
