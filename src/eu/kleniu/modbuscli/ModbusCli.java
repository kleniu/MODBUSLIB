/*
   Copyright Robert Kleniewski

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */


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
