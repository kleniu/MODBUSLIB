package eu.kleniu.modbuscli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class MyModbusCli {
	
	private String 	 ipAddress = "";
	private int 	portNumber = 0;
	
	private boolean   printHelp = false;
	private boolean 	 silent = false;
	private int        debugLvl = 0;
	
	private boolean     doReadCoils = false;
	private boolean       doSetCoil = false;
	private boolean    doToggleCoil = false;
	private boolean     doUnsetCoil = false;
	private boolean doReadRegisters = false;
	private boolean doWriteRegister = false;
	private int      coilAddress = 0;
	private int      coilsNumber = 0;
	private int  registerAddress = 0;
	private int  registersNumber = 0;
	private int    registerValue = 0;
	
	private void _msg(String msg) {
		if( !silent ) {
			System.out.println(msg);
		}
	}
	
	private int _doit() {
		boolean[] coilStatus = null;
		int[] registerStatus = null;
		boolean coilCurVal;
		int funRet = 0;
		int retVal = 0;
		String formatedMsg = "";
		
		// do connection and request PLC
		MyClient client = new MyClient(debugLvl);
		if ( retVal == 0 && client.mbtcSetup(ipAddress, portNumber) != MyClient.RET_OK) 
			retVal = 1;
		
		if ( retVal == 0 && client.mbtcConnect() != MyClient.RET_OK) {
			_msg("connection error: check parameters");
			retVal = 2;
		}
		
		if( retVal == 0 && doReadCoils ) {
				coilStatus = client.mbtcReadCoils(coilAddress, coilsNumber, 0);
				if (coilStatus != null) {
					for (int i = 0; i < coilStatus.length; i++) {
						formatedMsg = String.format("coil=%05d status=%b", i + coilAddress, coilStatus[i]);
						System.out.println(formatedMsg);
					}
				}
				else {
					_msg("read error: cannot read coils status");
					client.mbtcDisconnect();
					retVal = 3;
				}
		}

		if( retVal == 0 && doSetCoil ) {	
			funRet = client.mbtcWriteSingleCoil(coilAddress, true, 0);
			if (funRet == MyClient.RET_OK) {
				formatedMsg = String.format("coil=%05d newStatus=%b", coilAddress, true);
				System.out.println(formatedMsg);
			}
			else {
				_msg("write error: cannot set the coil");
				client.mbtcDisconnect();
				retVal = 4;
			}
		}

		if( retVal == 0 && doToggleCoil ) {
			coilStatus = client.mbtcReadCoils(coilAddress, 1, 0);
			if (coilStatus != null) {
				coilCurVal = coilStatus[0];
				funRet = client.mbtcWriteSingleCoil(coilAddress, !coilCurVal, 0);
				if (funRet == MyClient.RET_OK) {
					formatedMsg = String.format("coil=%05d newStatus=%b", coilAddress, !coilCurVal);
					System.out.println(formatedMsg);
				}
				else {
					_msg("write error: cannot toggle coil");
					client.mbtcDisconnect();
					retVal = 6;
				}
			}
			else {
				_msg("read error: cannot read coil current status");
				client.mbtcDisconnect();
				retVal = 5;
			}						
		}
		
		if( retVal == 0 && doUnsetCoil ) {	
			funRet = client.mbtcWriteSingleCoil(coilAddress, false, 0);
			if (funRet == MyClient.RET_OK) {
				formatedMsg = String.format("coil=%05d newStatus=%b", coilAddress, false);
				System.out.println(formatedMsg);
			}
			else {
				_msg("write error: cannot unset the coil");
				client.mbtcDisconnect();
				retVal = 7;
			}
		}
		
		if( retVal == 0 && doReadRegisters ) {
			registerStatus = client.mbtcReadRegisters(registerAddress, registersNumber, 0);
			if (registerStatus != null) {
				for (int i = 0; i < registerStatus.length; i++) {
					formatedMsg = String.format("register=%05d value=%06d", i + registerAddress, registerStatus[i]);
					System.out.println(formatedMsg);
				}
			}
			else {
				_msg("read error: cannot read registers value");
				client.mbtcDisconnect();
				retVal = 8;
			}
		}
		
		if( retVal == 0 && doWriteRegister ) {	
			funRet = client.mbtcWriteSingleRegister(registerAddress, registerValue, 0);
			if (funRet == MyClient.RET_OK) {
				formatedMsg = String.format("register=%05d newValue=%06d", registerAddress, registerValue);
				System.out.println(formatedMsg);
			}
			else {
				_msg("write error: cannot write to the register");
				client.mbtcDisconnect();
				retVal = 9;
			}
		}
		
		if ( retVal == 0 ) {
			if (client.mbtcDisconnect() != MyClient.RET_OK) {
				_msg("disconnect error: cannot disconnect from PLC");
				retVal = 10;
			}
		}
				
		return retVal;
	}

	/**
	 * defines available options for command line invocation
	 * @return Options object
	 */
	private Options _defineOptions() {
		Options options = new Options();
		
		Option help = Option.builder("h")
				.longOpt("help")
                .desc("displays this help message")
                .build();
		options.addOption(help);
		
		Option readCoils = Option.builder("cr")
                .argName("coil_address[:number]")
                .hasArg()
                .desc("read coil status. If optional number is specified"
                	  + " then given amount of subsequent coils will be read.")
                .build();
		options.addOption(readCoils);
		
		Option readRegs = Option.builder("rr")
                .argName("reg_address[:number]")
                .hasArg()
                .desc("read register value. If optional number is specified"
                	  + " then given amount of subsequent registers will be read.")
                .build();
		options.addOption(readRegs);
		
		Option setCoil = Option.builder("cs")
                .argName("coil_address")
                .hasArg()
                .desc("set coil.")
                .build();
		options.addOption(setCoil);
		
		Option usetCoil = Option.builder("cu")
                .argName("coil_address")
                .hasArg()
                .desc("uset coil.")
                .build();
		options.addOption(usetCoil);
		
		Option toggleCoil = Option.builder("ct")
                .argName("coil_address")
                .hasArg()
                .desc("toggle coil.")
                .build();
		options.addOption(toggleCoil);
		
		Option setReg = Option.builder("rw")
                .argName("reg_address:value")
                .hasArg()
                .desc("write specified value in the register. Value can be set to any number between <0,65k>")
                .build();
		options.addOption(setReg);
		
		Option silent = Option.builder("s")
                .longOpt("silent")
                .desc("display minimal amount of information.")
                .build();
		options.addOption(silent);
		
		Option debug = Option.builder("d")
                .longOpt("debug")
                .desc("display debugging information.")
                .build();
		options.addOption(debug);
		
		Option ip = Option.builder("ip")
                .argName("ip_address")
                .hasArg()
                .desc("IP address of PLC")
                .build();
		options.addOption(ip);
		
		Option port = Option.builder("p")
                .argName("port_number")
                .hasArg()
                //.type(Integer.class)
                .desc("TCP port number")
                .build();
		options.addOption(port);
		
		return options;
	}

	/**
	 * prints help message based on defined options
	 * if options are not defined (null) method does nothing
	 */
	private void _printHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		
		if(options != null) {
			formatter.printHelp( "java -jar mbcli.jar <params>", options );
		}
	}
	
	/**
	 * parses given options
	 * it prints description message on error
	 * @param args
	 * @return CommandLine object<br>
	 * or null on failure
	 */
	private CommandLine _parseOptions(Options options, String[] args) {
		CommandLine cmd = null;
		CommandLineParser parser = new DefaultParser();
		
		try {
			cmd = parser.parse( options, args);
		} catch (ParseException e) {
			_msg( e.getMessage() );
			return null;
		}
		return cmd;
	}
	
	private void _setPropertiesHelp(CommandLine cmd) {
		if (cmd.hasOption("h")) 
			printHelp = true;
		else
			printHelp = false;
	}
	
	private void _setPropertiesDebug(CommandLine cmd) {
		if (cmd.hasOption("d")) 
			debugLvl = 9;
		else 
			debugLvl = 0;
	}
	
	private void _setPropertiesSilent(CommandLine cmd) {
		if (cmd.hasOption("s")) 
			silent = true;
		else
			silent = false;
	}
	
	private boolean _setPropertiesNameIP(CommandLine cmd) {
		boolean retVal = true;
		
		if (cmd.hasOption("ip")) {
			ipAddress = cmd.getOptionValue("ip");
			if (ipAddress.length() == 0) {
				_msg("syntax error: missed IP address or DNS name of PLC after -ip option");
				retVal = false;
			}
		}
		else {
			if ( !printHelp ) {
				_msg("syntax error: missing required IP address or DNS name of PLC. Use -ip option");
				return retVal = false;
			}
		}
		
		return retVal;
	}
	
	private boolean _setPropertiesPort(CommandLine cmd) {
		boolean retVal = true;
		
		if (cmd.hasOption("p")) {
			try {
				portNumber = Integer.parseInt(cmd.getOptionValue("p"));
			} catch (NumberFormatException e) {
				_msg("syntax error: value (entered:" + cmd.getOptionValue("p") + ") provided with -p option is not a valid number");
				retVal = false;
			}	
			if (retVal && portNumber <= 0) {
				_msg("syntax error: number after -p option must be greater then 0");
				retVal = false;
			}
		}
		else {
			portNumber = 502;
		}
		return retVal;
	}
	
	private boolean _setPropertiesReadCoils(CommandLine cmd) {
		boolean retVal = true;

		if (cmd.hasOption("cr")) {
			String optVals[] = cmd.getOptionValue("cr").split(":");
			// first value
			if (optVals.length == 0) {
				_msg("syntax error: coil address must be provided with -cr option");
				retVal = false;
			}
			else {
				if (optVals[0].length() == 0) {
					_msg("syntax error: coil address must be provided after -cr option");
					retVal = false;
				}
				else {
					try {
						coilAddress = Integer.parseInt(optVals[0]);
					} catch (NumberFormatException e) {
						_msg("syntax error: coil address (entered:" + optVals[0] + ") provided with -cr option is not a valid number");
						retVal = false;	
					}
					if (coilAddress < 0) {
						_msg("syntax error: coil aadress (entered:" + optVals[0] + ") provided with -cr option must be possitive number");
						retVal = false;
					}
				}
			}
			// second (optional) value
			if (optVals.length == 1) {
				coilsNumber = 1;
			}
			else {
				if (optVals[1].length() == 0) {
					coilsNumber = 1;
				}
				else {
					try {
						coilsNumber = Integer.parseInt(optVals[1]);
					} catch (NumberFormatException e) {
						_msg("syntax error: coils number (entered:" + optVals[1] + ") provided with -cr option is not a valid number");
						retVal = false;	
					}
					if (coilsNumber < 0) {
						_msg("syntax error: number of coils (entered:" + optVals[1] + ") provided with -cr option must be possitive number");
						retVal = false;
					}
				}
			}
			// if retVal is still true we are ok to go
			if(retVal) 
				doReadCoils = true;
			else
				doReadCoils = false;		
		}
		return retVal;
	}
	
	private boolean _setPropertiesReadRegisters(CommandLine cmd) {
		boolean retVal = true;

		if (cmd.hasOption("rr")) {
			String optVals[] = cmd.getOptionValue("rr").split(":");
			// first value
			if (optVals.length == 0) {
				_msg("syntax error: register address must be provided with -rr option");
				retVal = false;
			}
			else {
				if (optVals[0].length() == 0) {
					_msg("syntax error: register address must be provided after -rr option");
					retVal = false;
				}
				else {
					try {
						registerAddress = Integer.parseInt(optVals[0]);
					} catch (NumberFormatException e) {
						_msg("syntax error: register address (entered:" + optVals[0] + ") provided with -rr option is not a valid number");
						retVal = false;	
					}
					if (registerAddress < 0) {
						_msg("syntax error: register aadress (entered:" + optVals[0] + ") provided with -rr option must be possitive number");
						retVal = false;
					}
				}
			}
			// second (optional) value
			if (optVals.length == 1) {
				registersNumber = 1;
			}
			else {
				if (optVals[1].length() == 0) {
					registersNumber = 1;
				}
				else {
					try {
						registersNumber = Integer.parseInt(optVals[1]);
					} catch (NumberFormatException e) {
						_msg("syntax error: number of registers (entered:" + optVals[1] + ") provided with -rr option is not a valid number");
						retVal = false;	
					}
					if (registersNumber < 0) {
						_msg("syntax error: number of registers (entered:" + optVals[1] + ") provided with -rr option must be possitive number");
						retVal = false;
					}
				}
			}
			// if retVal is still true we are ok to go
			if(retVal) 
				doReadRegisters = true;
			else
				doReadRegisters = false;		
		}
		return retVal;
	}
	
	private boolean _setPropertiesSetCoil(CommandLine cmd) {
		boolean retVal = true;

		if (cmd.hasOption("cs") ) {
			String optVal = cmd.getOptionValue("cs");
			// first value
			if (optVal.length() == 0) {
				_msg("syntax error: coil address must be provided with -cs option");
				retVal = false;
			}
			else {
				try {
					coilAddress = Integer.parseInt(optVal);
					coilsNumber = 1;
				} catch (NumberFormatException e) {
					_msg("syntax error: coil address (entered:" + optVal + ") provided with -cs option is not a valid number");
					retVal = false;	
				}
				if (coilAddress < 0) {
					_msg("syntax error: coil aadress (entered:" + optVal + ") provided with -cs option must be possitive number");
					retVal = false;
				}
				
			}
			// if retVal is still true we are ok to go
			if(retVal) 
				doSetCoil = true;
			else
				doSetCoil = false;		
		}
		return retVal;
	}
		
	private boolean _setPropertiesWriteRegister(CommandLine cmd) {
		boolean retVal = true;

		if (cmd.hasOption("rw")) {
			String optVals[] = cmd.getOptionValue("rw").split(":");
			// first value
			if (optVals.length == 0) {
				_msg("syntax error: register address must be provided with -rw option");
				retVal = false;
			}
			else {
				if (optVals[0].length() == 0) {
					_msg("syntax error: register address must be provided after -rw option");
					retVal = false;
				}
				else {
					try {
						registerAddress = Integer.parseInt(optVals[0]);
					} catch (NumberFormatException e) {
						_msg("syntax error: register address (entered:" + optVals[0] + ") provided with -rw option is not a valid number");
						retVal = false;	
					}
					if (registerAddress < 0) {
						_msg("syntax error: register aadress (entered:" + optVals[0] + ") provided with -rw option must be possitive number");
						retVal = false;
					}
				}
			}
			// second (optional) value
			if (optVals.length == 1) {
				_msg("syntax error: register value must be specified with -rw option");
				retVal = false;
			}
			else {
				if (optVals[1].length() == 0) {
					_msg("syntax error: register value must be specified with -rw option");
					retVal = false;
				}
				else {
					try {
						registerValue = Integer.parseInt(optVals[1]);
					} catch (NumberFormatException e) {
						_msg("syntax error: register value (entered:" + optVals[1] + ") provided with -rr option is not a valid number");
						retVal = false;	
					}
				}
			}
			// if retVal is still true we are ok to go
			if(retVal) 
				doWriteRegister = true;
			else
				doWriteRegister = false;		
		}
		return retVal;
	}
		
	private boolean _setPropertiesToggleCoil(CommandLine cmd) {
		boolean retVal = true;

		if (cmd.hasOption("ct") ) {
			String optVal = cmd.getOptionValue("ct");
			// first value
			if (optVal.length() == 0) {
				_msg("syntax error: coil address must be provided with -ct option");
				retVal = false;
			}
			else {
				try {
					coilAddress = Integer.parseInt(optVal);
					coilsNumber = 1;
				} catch (NumberFormatException e) {
					_msg("syntax error: coil address (entered:" + optVal + ") provided with -ct option is not a valid number");
					retVal = false;	
				}
				if (coilAddress < 0) {
					_msg("syntax error: coil aadress (entered:" + optVal + ") provided with -ct option must be possitive number");
					retVal = false;
				}
				
			}
			// if retVal is still true we are ok to go
			if(retVal) 
				doToggleCoil = true;
			else
				doToggleCoil = false;		
		}
		return retVal;
	}
	
	private boolean _setPropertiesUnsetCoil(CommandLine cmd) {
		boolean retVal = true;

		if (cmd.hasOption("cu") ) {
			String optVal = cmd.getOptionValue("cu");
			// first value
			if (optVal.length() == 0) {
				_msg("syntax error: coil address must be provided with -cu option");
				retVal = false;
			}
			else {
				try {
					coilAddress = Integer.parseInt(optVal);
					coilsNumber = 1;
				} catch (NumberFormatException e) {
					_msg("syntax error: coil address (entered:" + optVal + ") provided with -cu option is not a valid number");
					retVal = false;	
				}
				if (coilAddress < 0) {
					_msg("syntax error: coil aadress (entered:" + optVal + ") provided with -cu option must be possitive number");
					retVal = false;
				}
				
			}
			// if retVal is still true we are ok to go
			if(retVal) 
				doUnsetCoil = true;
			else
				doUnsetCoil = false;		
		}
		return retVal;
	}
	
	private boolean _setProperties(CommandLine cmd) {
		boolean retVal = true;
		
		_setPropertiesHelp(cmd);
		_setPropertiesDebug(cmd);
		_setPropertiesSilent(cmd);
		retVal = _setPropertiesNameIP(cmd);
		if( retVal ) retVal = _setPropertiesPort(cmd);
		if( retVal ) retVal = _setPropertiesReadCoils(cmd);
		if( retVal ) retVal = _setPropertiesSetCoil(cmd);
		if( retVal ) retVal = _setPropertiesToggleCoil(cmd);
		if( retVal ) retVal = _setPropertiesUnsetCoil(cmd);
		if( retVal ) retVal = _setPropertiesReadRegisters(cmd);
		if( retVal ) retVal = _setPropertiesWriteRegister(cmd);
		if (retVal && !printHelp && !doReadCoils && !doSetCoil && !doToggleCoil && !doUnsetCoil && !doReadRegisters && !doWriteRegister ) {
			_msg("usage error: at least one of the following switches: -h, -cr, -cs, -ct, -cu, -rr or -rw must be provided");
			retVal = false;
		}
		return retVal;
	}

	private boolean _isInStrArray(String[] array, String val) {
		boolean retVal = false;
		for (String s: array) {
			if (s.equals(val)) {
				retVal = true;
				break;
			}
		}
		return retVal;
	}
	
	/**
	 * my custom main method
	 * created to be non static and for unit testing
	 * 
	 * @param args values passed by command line arguments
	 * @return exit value to be returned to system.<br>
	 * Non-zero value indicates error.
	 */
	public int myMain(String[] args) {
		if ( _isInStrArray(args, "-s") || _isInStrArray(args, "--silent")) 
			silent = true;
		else 
			silent = false;
		
		_msg("##########################################################################");
		_msg("#####     Modbus CLI version 1.0 2017(c). Use -h option for help     #####");
		_msg("##### for additional info see blog: https://doozerslab.wordpress.com #####");
		_msg("##########################################################################");
				
		Options options = _defineOptions();
		
		// parse and print parsing error in case of failure
		CommandLine cmd = _parseOptions(options, args);
		// display help message and exit on error
		if( cmd == null) {
			_printHelp(options);
			return 1; // bad parsing
		}
		
		// set private properties based on options provided by command line
		if( !_setProperties(cmd)) {
			_printHelp(options);
			return 2; // bad option values
		}
		
		// do the job
		if (printHelp) {
			_printHelp(options);
			return 0; // OK - just print help
		}
		else if( _doit() == 0 )
			return 0; // OK
		else
			return 3; // problems with executions - switch on debugging

	}

} // end of class

