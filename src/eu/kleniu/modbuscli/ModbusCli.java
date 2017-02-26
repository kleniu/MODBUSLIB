package eu.kleniu.modbuscli;

import eu.kleniu.modbuslib.ModbusTcpClient;

class MyClient extends ModbusTcpClient {

	public MyClient(int debugLevel) {
		super(debugLevel);
	}

	@Override
	public void displayLog(String debMsg) {
		System.out.println( debMsg );
	}
	
}


public class ModbusCli {
	// main method must be public, static and void
	public static void main(String[] args) {
		MyModbusCli app = new MyModbusCli();		
		System.exit(app.myMain(args));
	}
}
