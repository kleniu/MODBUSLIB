/**
 * 
 */
package eu.kleniu.modbuslib;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * @author Robert K.
 *
 */
public abstract class ModbusTcpClient {
	
    // ********************************************************************************************
    // **** Return codes
    // ********************************************************************************************
  
    public static final int RET_NOTCON = 31; // not connected
    public static final int RET_ERRFLU = 32; // error when flushing input stream
    public static final int RET_ERROCL = 33; // error when closing output stream
    public static final int RET_ERRICL = 34; // error when closing input stream
    public static final int RET_ERRCSO = 35; // error when closing socket
    
    public static final int RET_NOTSET = 21; // connection parameters we not set
    public static final int RET_ERRCON = 22; // problem to connect
    public static final int RET_ERRIST = 23; // problem to create input stream
    public static final int RET_ERROST = 24; // problem to create output stream
    public static final int RET_ERRSTO = 25; // we can not setup SO_TIMEOUT
    
    public static final int RET_BADARG = 11; // bad arguments in InetSocketAddress constructor
    public static final int RET_SECRES = 12; // security manager is on and do not let resolve name
    
    public static final int RET_ERRRAN = 41; // parameter range error
    public static final int RET_ERRBRE = 42; // bad or no response
    public static final int RET_ERRXRE = 43; // exception response
    
    public static final int RET_OK     = 0;
 
    // ********************************************************************************************
    // **** Private properties
    // ********************************************************************************************
    
    private int    _dispDebugMsgLevel;  // debug level 9 - very verbose 0 - do not display at all
    private Socket        _socClient ;
    private SocketAddress _socAddress;
    private DataInputStream  _iStream;	// Input Stream READ data
    private DataOutputStream _oStream;	// Output Stream WRITES data
    private long             _transId;  // this is the value for MODBUS Transaction Identifier
    private int            _soTimeout;
    private int           _conTimeout;
    private int           _tcpNoDelay;
    private int            _keepAlive;

    private int _rawRequestRetry;
    private int _rawRequestReconnectAndRetry;

    // ********************************************************************************************
    // **** Private methods
    // ********************************************************************************************
    private void _dispDebugLog(int level, String msgText) {
    	String debMsg = "";
		if ( level <= _dispDebugMsgLevel ) {
			debMsg = "DEBUG(" + Long.toString(System.currentTimeMillis()) + "): " + msgText;
			displayLog(debMsg);
		}
    } 
 
    private boolean _amIConnected() {
	if( _socClient != null )
	    return _socClient.isConnected() && ! _socClient.isClosed();
	else
	    return false;
    }

    private void _drainInputDataStream( DataInputStream streamToDrain ) {
		byte[] data = new byte[1];
		boolean procead = true;
		int retval;
	
		_dispDebugLog(9,"_drainInputDataStream: before drain loop");
		try {
			if( streamToDrain != null ) {
			    while ( procead ) {
			    	try {
			    		retval = streamToDrain.read(data,0,data.length);
			    		if ( retval != -1 ) {
				    		//_dispDebugLog(9,"drained data:" + _byteArrayToHexString(data));
			    			procead = true;
			    		}
				    	else
				    		procead = false;
			    	} catch (NullPointerException ee) {
						procead = false;
					}
			    }
			}
		} catch ( IOException ex ) {
		    //_dispDebugLog(9,"_drainInputDataStream: exception");
		}
	
		//_dispDebugLog(9,"_drainInputDataStream: after drain loop");

    }

    private int _getTransId() {
		if ( _transId < 65535 )
		    _transId++;
		else
		    _transId=1;
		return (int)_transId;
    }
  
    private byte[] _intToByteArray(int intValue) {
    	ByteBuffer buffer = ByteBuffer.allocate(4);
        //buffer.order(ByteBuffer.BIG_ENDIAN); it is by default BIG_ENDIAN
		return buffer.putInt(intValue).array();
    }
    
    private String _byteArrayToHexString(byte[] byteArray) {
		String retVal = "";
    	for (byte b: byteArray) {
			retVal += String.format("%02x", (b & 0xFF));
		}
		return retVal;
	}
    
    private int _twoBytesToInt(byte hi, byte lo) {
    	ByteBuffer buffer = ByteBuffer.allocate(2);
    	buffer.put(hi);
    	buffer.put(lo);
    	buffer.flip();
    	return buffer.getShort();
    }

    private boolean _readBitFromByte( byte dataByte, int pos ) {
		// do this to not have the problem with sign
		int data = dataByte;
		if ( ( ( data >> pos ) & 1 ) == 0 )
		    return false;
		else
		    return true;
    }
    
    /**
     * Retry to send MODBUS request in case of timeout/failure.
     * Method tries to reconnect to Master if send requests fails. 
     *
     * @param  requestPDU Protocol Data Unit (PDU) to be sent to MODBUS Master
     * @param  unitID              remote MODBUS Master identification number. Usualy 0
     * 
     * @return responsePDU response Protocol Data Unit (PDU) on success<br>
     * null on error
     *         
     */
    private byte[] _sRequest(byte[] requestPDU, byte unitID) {
		int retVal;
		int i;
		byte[] responsePDU;
	
		// connect to Master if not connected
		if( ! _amIConnected() ) {
		    _dispDebugLog(9,"_sRequest: we are not connected, trying to connect");
		    retVal = mbtcConnect();
		    if ( retVal != RET_OK ) {
				_dispDebugLog(9,"_sRequest: cannot connect mbtcConnect reurns" + String.valueOf(retVal));
				return null; // if we cannot connect then there is nothing to do returning null
		    }
		    else {
		    	_dispDebugLog(9,"_sRequest: connection sucessfully established");
		    }
		}
	
		responsePDU = null;
		for (i = 0; i < _rawRequestRetry; i++) {
		    responsePDU = _rawRequest( requestPDU, unitID );
		    if( responsePDU == null ) {
		    	// we have potential problem
		    	_dispDebugLog(9,"_sRequest: attempt #" + Integer.toString(i) + " - mbtcRawRequest failed.");
		    }
		    else {
		    	i = _rawRequestRetry; // everything is OK. So we force to exit "for loop"
		    }
		}
	
		if( responsePDU == null ) {
		    // we are still not OK - lets try reconnect trick
		    _dispDebugLog(9,"_sRequest: we still have the problem with rawRequest. Lets try to disconnect and connect again");
		    for (i = 0; i< _rawRequestReconnectAndRetry; i++) {
				retVal = mbtcDisconnect(); // it will definitely close the connection and clean up local properties
				_dispDebugLog(9,"_sRequest: we are disconnected now");
				
				retVal = mbtcConnect();
				if ( retVal != RET_OK ) {
				    _dispDebugLog(9,"_sRequest: cannot connect mbtcConnect returns: " + String.valueOf(retVal));
				    return null; // if we cannot connect then there is nothing to do returning null
				}
				else {
				    _dispDebugLog(9,"_sRequest: connection successfully established");
				}
		
				responsePDU = _rawRequest( requestPDU, unitID );
				if( responsePDU == null ) {
				    // we have potential problem
				    _dispDebugLog(9,"_sRequest: attempt of mbtcRawRequest failed. ");
				}
				else {
				    i = _rawRequestReconnectAndRetry; // everything is OK. So we force to exit "for loop"
				}
		    } // for
		}
	
		if( responsePDU == null ) {
		    _dispDebugLog(9,"_sRequest: request failed. Returning null");
		    return null;
		}
		else {
		    _dispDebugLog(9,"_sRequest: success");
		    return responsePDU;
		}
    }

    /**
     * Method communicates with MODSBUS Master via network socket. 
     * It creates Modbus Application Header (MBAP) before actual data will be sent via raw socket.
     * MBAP[7]=TRANSACTION_ID[2]+PROTOCOL_ID[2]+LENGTH[2]+UNIT_ID[1]<br>
     * Method then creates ADU (which is MBAP_header + PDU) and sends it to Master 
     *
     * @param  requestPDU Protocol Data Unit (PDU) to be sent to MODBUS Master
     * @param  unitID              remote MODBUS Master identification number. Usualy 0
     * 
     * @return responsePDU response Protocol Data Unit (PDU) on success<br>
     * null on error
     *         
     */
    private byte[] _rawRequest(byte[] requestPDU, byte unitID) {

		// MBAP Header is ALWAYS 7 bytes long!
		byte[] requestMBAPheader  = new byte[7];
		byte[] requestADU         = new byte[requestMBAPheader.length + requestPDU.length];
		byte[] responseMBAPheader = new byte[7];
		byte[] responsePDU;
		byte[] intToByte;
	
		// creating requestMBAPheader
		intToByte = _intToByteArray( _getTransId() );
		requestMBAPheader[0] = intToByte[2]; // HiByte of TRANSACTION ID
		requestMBAPheader[1] = intToByte[3]; // LoByte of TRANSACTION ID
		requestMBAPheader[2] = 0x00; // always 0 for Modbus
		requestMBAPheader[3] = 0x00; // always 0 for Modbus
		// length is always positive number so we do not care about negative representation of int :)
		// this is the length of transmitted data including 1 byte for unit ID
		intToByte = _intToByteArray( requestPDU.length + 1 );
		// BIG_ENDIAN = most significant byte is lowest memory adress
		requestMBAPheader[4] = intToByte[2]; // HiByte of len+id
		requestMBAPheader[5] = intToByte[3]; // LoByte of len+id
		requestMBAPheader[6] = unitID; // Unit Identifier
	
		_dispDebugLog(9,"_rawRequest: Request MBAP Header:                 " + _byteArrayToHexString(requestMBAPheader));
		_dispDebugLog(9,"_rawRequest: Request Protocol Data Unit (PDU):    " + _byteArrayToHexString(requestPDU));
	
		// creating request ADU = MBAP Header + PDU
		try {
			System.arraycopy(requestMBAPheader, 0, requestADU, 0, requestMBAPheader.length);
			System.arraycopy(requestPDU, 0, requestADU, requestMBAPheader.length, requestPDU.length);
		} catch (NullPointerException ex) {
			_dispDebugLog(9,"_rawRequest: Problem with copying arrays");
		    return null;
		}
	
		// write ADU
		try {
			    // now lets send it to the server
			    _oStream.write(requestADU,0,requestADU.length);
		} catch (NullPointerException ee) {
			_dispDebugLog(9,"_rawRequest: Problem with writing to output stream");
			return null;
		} catch (IOException ex) {
		    _dispDebugLog(9,"_rawRequest: Problem with writing ADU to slave");
		    // the caller method should do something with is - like reconnect or resent PDU one more time
		    return null;
		}
	
		// read MBAP header
		try {
				// and lets get the response MBAP Header
				_iStream.read(responseMBAPheader,0,responseMBAPheader.length);
		} catch (NullPointerException ee) {
			return null;
		} catch (IOException ex) {
		    _dispDebugLog(9,"_rawRequest: Problem with reading MBAP Header from slave.");
		    _drainInputDataStream( _iStream );
		    // the caller method should do something with is - like reconnect or resent PDU one more time
		    return null;
		}
		_dispDebugLog(9,"_rawRequest: Response MBAP header:                " + _byteArrayToHexString(responseMBAPheader));
	
		// read PDU
		if ( responseMBAPheader[5] >= 1 )
			responsePDU = new byte[responseMBAPheader[5]-1]; // 6-th byte is size of response PDU
		else
			return null;
		try {
		   	_iStream.read(responsePDU,0,responsePDU.length);
		} catch (NullPointerException ee) {
			_dispDebugLog(9,"_rawRequest: zero length responce PDU");
			return null;
		} catch (IOException ex) {
		    _dispDebugLog(9,"_rawRequest: Problem with reading PDU data.");
		    _drainInputDataStream( _iStream );
		    return null;
		}
		_dispDebugLog(9,"_rawRequest: Response Protocol Data Unit (PDU):   " + _byteArrayToHexString(responsePDU));
	
	
		return responsePDU;
    }


    // ********************************************************************************************
    // **** Public methods
    // ********************************************************************************************
    /**
     * Abstract method to be implemented by the User for displaying debug messages 
     * 
     * @param  debMsg formatted debugging message to be displayed in your application 
     * 
     */
    abstract public void displayLog(String debMsg); 

    
    /**
     * Initializes private properties of the object with the default values. 
     * Default values can be changed in mbtcSetup method.
     *
     * @param  debugLevel value 0 indicates no debugging messages. Value 9 will call abstract class displayLog with debug message. 
     * 
     */
    public ModbusTcpClient (int debugLevel) {
		_dispDebugMsgLevel = debugLevel; // very verbose debugging messaging
		_socClient  = null;
		_socAddress = null;
		_iStream = null;
		_oStream = null;
		_transId = 1;
	    
		_soTimeout  = 1000; // 1000 ms for socket timeout
	    _conTimeout = 1000; // 1000 ms for connection timeout
	    _tcpNoDelay = 1;    // set tcpNoDelay for MODBUS communication
	    _keepAlive  = 1;    // set tcp keepAlive for MODBUS communication
	
		_rawRequestRetry = 3;             
		_rawRequestReconnectAndRetry = 2; 
    }

    
	/**
     * Set specified value in the register at specified address.
     * 
     * @param  registerNumber   address of the coil
     * @param  registerNewValue desired state of the coil
     * @param  deviceID         MODBUS Master device ID. Usually 0.
     * 
     * @return RET_OK (0)      on success<br>
     *         RET_ERRRAN (41) if the register address is out of range
     *         RET_ERRBRE (42) none or bad response from MODBUS Master
     *         RET_ERRXRE (43) exception response from MODBUS Master
     *         
     */
    public int mbtcWriteSingleRegister(int registerNumber, int registerNewValue, int registerDeviceId){
    	// registers are numbered starting from 0
    	// the request PDU:
    	//    Function code     = 1 byte  - it will be 0x06 for writing single register
    	//    Register address  = 2 bytes - address of the holding register in range 0x0000 to 0xFFFF
    	//    Register value    = 2 bytes - first is HiByte number second byte is LoByte number 
    	//
    	// the response PDU - is just the echo of the requested PDU
    	//
    	// exception response PDU
    	//    Function code     = 1 byte - it will be 0x86
    	//    Exception code    = 1 byte - 01 illegal function - not implemented in server device
    	//                                 02 illegal data address - address is not supported by device
    	//                                 03 illegal data value - fault in structure
    	//                                 04 server device failure - problem at server site

    	byte[] requestPDU = new byte[5];
    	byte[] intToByte;
    	byte[] responsePDU;
    	byte unitID;

    	if( ( registerNumber < 0 ) || ( registerNumber >= 65535 ) ) {
    		_dispDebugLog(9,"mbtcWriteSingleRegister: register# out of range <0,65535) - registerNumber=" + String.valueOf(registerNumber));
    		return RET_ERRRAN;
    	}
	
    	// to write single holding register we use function 0x06
    	requestPDU[0] = 0x06;

    	// lets convert coil number to bytes
    	intToByte = _intToByteArray( registerNumber );
    	requestPDU[1] = intToByte[2]; // HiByte of register number
    	requestPDU[2] = intToByte[3]; // LoByte of register number

    	// let's set value
    	intToByte = _intToByteArray( registerNewValue );
    	requestPDU[3] = intToByte[2]; // HiByte of value
    	requestPDU[4] = intToByte[3]; // LoByte of value

    	// lets convert devideID to unitID
    	intToByte = _intToByteArray ( registerDeviceId );
    	unitID = intToByte[3];

    	// lets do the magic
    	responsePDU = _sRequest( requestPDU, unitID );


    	if( responsePDU == null ) {
    		_dispDebugLog(9,"mbtcWriteSingleRegister: _sRequest returned null value.");
    		return RET_ERRBRE;
    	}
    	else {
    		_dispDebugLog(9,"mbtcWriteSingleRegister: _sRequest returned: " + _byteArrayToHexString(responsePDU));

    		// lets analyze the responsePDU
    		if( responsePDU[0] != requestPDU[0] ) {
    			_dispDebugLog(9,"mbtcWriteSingleRegister: exception in responsePDU.");
    			return RET_ERRXRE;
    		}
    	}
    	 
    	return RET_OK;
    }
    

	/**
     * Switches ON or OFF the coil at specified address.
     * 
     * @param  coilNumber      address of the coil
     * @param  coilState       desired state of the coil
     * @param  deviceID        MODBUS Master device ID. Usually 0.
     * 
     * @return RET_OK (0)      on success<br>
     *         RET_ERRRAN (41) if the coil address is out of range
     *         RET_ERRBRE (42) none or bad response from MODBUS Master
     *         RET_ERRXRE (43) exception response from MODBUS Master
     *         
     */
    public int mbtcWriteSingleCoil( int coilNumber, boolean coilState, int deviceID ) {
		// when writing coils, we do not provide starting address but the Coil number
		// coils are numbered starting from 0
		// the request PDU:
		//    Function code     = 1 byte  - it will be 0x05 in this case
		//    Coil number       = 2 bytes - starting from 0
		//    Coil State        = 2 bytes - value 0xFF00 = ON ; value 0x0000 = OFF
		//
		// the response PDU - is just the echo of the requested PDU
		//
		// exception response PDU
		//    Function code     = 1 byte - it will be 0x85
		//    Exception code    = 1 byte - 01 illegal function - not implemented in server device
		//                                 02 illegal data address - adress is not supported by device
		//                                 03 illegal data value - fault in structure
		//                                 04 server device failure - problem at server site
	
		byte[] requestPDU = new byte[5];
		byte[] intToByte;
		byte[] responsePDU;
		byte unitID;
			
		if( ( coilNumber < 0 ) || ( coilNumber >= 65535 ) ) {
		    _dispDebugLog(9,"mbtcWriteSingleCoil: coil# out of range <0,65535) - coilNumber=" + String.valueOf(coilNumber));
		    return RET_ERRRAN;
		}
		
		// to write single coil we use function 0x05
		requestPDU[0] = 0x05;
	
		// lets convert coil number to bytes
		intToByte = _intToByteArray( coilNumber );
		requestPDU[1] = intToByte[2]; // HiByte of coil number
		requestPDU[2] = intToByte[3]; // LoByte of coil number
	
		// let's set ON/OFF value
		if ( coilState )
		    requestPDU[3] = (byte)0xff; // lets set ON value
		else
		    requestPDU[3] = (byte)0x00; // lets set OFF value
	
		requestPDU[4] = (byte)0x00; // it is always 0x00
	
		// lets convert devideID to unitID
		intToByte = _intToByteArray ( deviceID );
		unitID = intToByte[3];
	
		responsePDU = _sRequest( requestPDU, unitID );
	
		if( responsePDU == null ) {
		    _dispDebugLog(9,"mbtcWriteSingleCoil: mbtcRequest returned null value.");
		    return RET_ERRBRE;
		}
		else {
		    _dispDebugLog(9,"mbtcWriteSingleCoil: mbtcRequest returned: " + _byteArrayToHexString(responsePDU));
	
		    if( responsePDU[0] != requestPDU[0] ) {
		    	_dispDebugLog(9,"mbtcWriteSingleCoil: exception in responsePDU.");
		    	return RET_ERRXRE;
		    }
		    else {
		    	_dispDebugLog(9,"mbtcWriteSingleCoil: coil state=" + String.valueOf(coilState));
		    }
	
		}
	
		return RET_OK;
    }


    /**
     * Reads specified number of coils' status starting at given address.
     *
     * @param  startAddress    address of the first coil to read
     * @param  quantity        number of coils to read
     * @param  deviceID        modbus device id. If not sure specify 0.
     * @return boolean[]       List of boolean values associated with coils status
     *         
     */
    public boolean[] mbtcReadCoils( int startAddress, int quantity, int deviceID ) {
		// according to MODBUS specification maximum number of coils to read is 2000 (0x7d0)
		// the request PDU:
		//    Function code     = 1 byte - it will be 0x01 in this case
		//    Starting Address  = 2 bytes
		//    Quantity of Coils = 2 bytes
		//
		// the response PDU:
		//    Function code     = 1 byte - it will be the same as in request PDU
		//    Byte Count        = 1 byte - if i ask for 1 to 8 coils i will receive 1 byte etc
		//    Coil Status       = N bytes
		//
		// exception responce PDU
		//    Function code     = 1 byte - it will be 0x81
		//    Exception code    = 1 byte - 01 illegal function - not implemented in server device
		//                                 02 illegal data address - address is not supported by device
		//                                 03 illegal data value - fault in structure
		//                                 04 server device failure - problem at server site
	
		byte[] requestPDU = new byte[5];
		byte[] intToByte;
		byte[] responsePDU;
		byte unitID;
	
		if( ( startAddress < 0 ) || ( startAddress > 65535 ) ) {
			_dispDebugLog(9,"mbtcReadCoils: address out of range <0,65535> - startAddress=" + String.valueOf(startAddress));
			return null;
		}
		
	
		if( ( quantity < 1 ) || ( quantity > 2000 ) ) {
		    _dispDebugLog(9,"mbtcReadCoils: quantity out of range <1,2000> - quantity=" + String.valueOf(quantity));
		    return null;
		}
	
		// for reading coils we use function 0x01
		requestPDU[0] = 0x01;
	
		// lets convert 5code to PDU address
		intToByte = _intToByteArray( startAddress );
		requestPDU[1] = intToByte[2]; // HiByte of PDU address
		requestPDU[2] = intToByte[3]; // LoByte of PDU address
	
		// lets convert quantity
		intToByte = _intToByteArray( quantity );
		requestPDU[3] = intToByte[2]; // HiByte of coils quantity
		requestPDU[4] = intToByte[3]; // LoByte of coils quantity
	
		intToByte = _intToByteArray ( deviceID );
		unitID = intToByte[3];
	
		responsePDU = _sRequest( requestPDU, unitID );
	
		if( responsePDU == null ) {
		    _dispDebugLog(9,"mbtcReadCoils: _sRequest returned null value. Exit with null");
		    return null;
		}
		else {
		    _dispDebugLog(9,"mbtcReadCoils: _sRequest returned: " + _byteArrayToHexString(responsePDU));
	
		    // lets analyze the responsePDU 
		    if( responsePDU[0] != requestPDU[0] ) {
		    	_dispDebugLog(9,"mbtcReadCoils: exception in response PDU. Exit with null");
		    	return null;
		    }
	
		    int coilArrayIdx = 0;
		    boolean[] coilArray = new boolean[ quantity ];
		    for (int i = 2; i < (2 + responsePDU[1]); i++ ){
				_dispDebugLog(9,"mbtcReadCoils: data byte " + String.valueOf(i-2) + " = "+ String.format("0x%02x ",(responsePDU[i] & 0xFF) ) );
				for( int j = 0; j <= 7; j++ ) {
				    if( coilArrayIdx < quantity ) {
				    	coilArray[coilArrayIdx] = _readBitFromByte( responsePDU[i] , j );
				        _dispDebugLog(9,"mbtcReadCoils: coil " + String.valueOf(coilArrayIdx) + " = "+ String.valueOf(coilArray[coilArrayIdx]) );
				    	coilArrayIdx++;
				    }
				    else {
				    	// there is nothing to do next and we have to exit both loops
				    	j = 8;
				    	i = 2 + responsePDU[1];
				    }
				}
		    }
		    return coilArray;
		}
    }


    /**
     * Reads specified number of registers starting at given address.
     *
     * @param  startAddress    address of the first register to read
     * @param  quantity        number of registers to read
     * @param  deviceID        modbus device id. If not sure specify 0.
     * @return int[]           List of int values associated with registers
     *         
     */
    public int[] mbtcReadRegisters( int startAddress, int quantity, int deviceID ) {
    	// according to MODBUS specification maximum number of register to read is 125 (0x7d)
    	// the request PDU:
    	//    Function code     = 1 byte - it will be 0x03 for reading holding registers
    	//    Starting Address  = 2 bytes in range 0x0000 to 0xFFFF
    	//    Quantity of Coils = 2 bytes in range 0x01 to 0x7D (125)
    	//
    	// the response PDU:
    	//    Function code     = 1 byte - it will be the same as in request PDU
    	//    Byte Count        = 1 byte - two bytes per register 
    	//    Register Values   = N bytes
    	//
    	// exception response PDU
    	//    Function code     = 1 byte - it will be 0x83
    	//    Exception code    = 1 byte - 01 illegal function - not implemented in server device
    	//                                 02 illegal data address - address is not supported by device
    	//                                 03 illegal data value - fault in structure
    	//                                 04 server device failure - problem at server site

    	byte[] requestPDU = new byte[5];
    	byte[] intToByte;
    	byte[] responsePDU;
    	byte unitID;
    
	
    	if( ( startAddress < 0 ) || ( startAddress > 65535 ) ) {
    		_dispDebugLog(9,"mbtcReadRegisters: address out of range <0,65535> - startAddress=" + String.valueOf(startAddress));
    		return null;
    	}
	
    
    	if( ( quantity < 1 ) || ( quantity > 125 ) ) {
    		_dispDebugLog(9,"mbtcReadRegisters: quantity out of range <1,125> - quantity=" + String.valueOf(quantity));
    		return null;
    	}

    	// for reading registers we use function 0x03
    	requestPDU[0] = 0x03;

	
    	// lets convert 5code to PDU address
    	intToByte = _intToByteArray( startAddress );
    	requestPDU[1] = intToByte[2]; // HiByte of PDU address
    	requestPDU[2] = intToByte[3]; // LoByte of PDU address

	
    	// lets convert quantity
    	intToByte = _intToByteArray( quantity );
    	requestPDU[3] = intToByte[2]; // HiByte of coils quantity
    	requestPDU[4] = intToByte[3]; // LoByte of coils quantity

	
    	// lets convert devideID to unitID
    	intToByte = _intToByteArray ( deviceID );
    	unitID = intToByte[3];
	
    	responsePDU = _sRequest( requestPDU, unitID );

    	if( responsePDU == null ) {
    		_dispDebugLog(9,"mbtcReadRegisters: _sRequest returned null value. Exit with null");
    		return null;
    	}
    	else {
    		_dispDebugLog(9,"mbtcReadRegisters: _sRequest returned: " + _byteArrayToHexString(responsePDU));

    		// lets analyze the responsePDU
    		if( responsePDU[0] != requestPDU[0] ) {
    			_dispDebugLog(9,"mbtcReadRegisters: exception in responsePDU. Exit with null");
    			return null;
    		}

    		// reposncePDU[0] - function code 0x03 or 0x83 in case of error
    		// responcePDU[1] - number of bytes of data
    		// responcePDU[2] - Hi of register 1
    		// responcePDU[3] - Lo of register 1
    		// responcePDU[4] - Hi of register 2
    		// responcePDU[5] - Lo of register 2
    		// ....
    		// responcePDU[N]   - Hi of register N/2
    		// responcePDU[N+1] - Lo of register N/2
    		
    		int registryArrayIdx = 0;
    		int[] registersArray = new int[ quantity ];
    		for(int i=2; i < 2 + responsePDU[1]; i=i+2 ) {
    			_dispDebugLog(9,"mbtcReadRegisters: Hi data byte " + String.valueOf(i) + " = "+ String.format("0x%02x ",(responsePDU[i] & 0xFF) ) );
    			_dispDebugLog(9,"mbtcReadRegisters: Lo data byte " + String.valueOf(i+1) + " = "+ String.format("0x%02x ",(responsePDU[i+1] & 0xFF) ) );
    			registersArray[registryArrayIdx] = _twoBytesToInt(responsePDU[i],responsePDU[i+1]);
    			_dispDebugLog(9,"mbtcReadRegisters: register " + String.valueOf(registryArrayIdx) + " = "+ String.valueOf(registersArray[registryArrayIdx]) );
    			registryArrayIdx++;
    		}
 
    		return registersArray;
    	} // end if
    }
    
  
	/**
     * Disconnecting the socket from MODBUS master. Before closing socket
     * method flushes output stream and close both input and output streams
     *
     * @return RET_OK (0)      on success<br>
     *         RET_NOTCON (31) user is trying to disconnect already closed connection<br>
     *         RET_ERRFLU (32) error flushing input stream<br>
     *         RET_ERROCL (33) error closing output stream<br>
     *         RET_ERRICL (34) error closing input stream stream<br>
     *         RET_ERRCSO (35) error closing socket<br>
     *         
     */
    public int mbtcDisconnect() {
		int retval = RET_OK;
		
		// check if not already connected
		if( ! _amIConnected() )
		    return RET_NOTCON;
		else {
		    // output stream should be flushed and closed first
		    try {
		    	_oStream.flush();
		    	_dispDebugLog(9,"mbtcDisconnect: flushing output stream");
		    } catch (IOException ex) {
		    	_dispDebugLog(9,"mbtcDisconnect: cannot flush output stream");
		    	retval = RET_ERRFLU;
		    }
	
		    try {
		    	if ( _oStream != null ) {
		    		try {
		    			_oStream.close();
		    			_dispDebugLog(9,"mbtcDisconnect: closing output stream");
		    		} catch (NullPointerException ee) {
						retval = RET_ERROCL;
					}
					_oStream = null;
		    	}
		    } catch (IOException ex) {
				_oStream = null;
				_dispDebugLog(9,"mbtcDisconnect: cannot close output stream");
				retval = RET_ERROCL;
		    }
	
		    try {
		    	if ( _iStream != null ) {
		    		try {
		    			_iStream.close();
		    			_dispDebugLog(9,"mbtcDisconnect: closing input stream");
		    		} catch (NullPointerException ee) {
						retval = RET_ERROCL;
					}
					_iStream = null;
		    	}
		    } catch (IOException ex) {
				_iStream = null;
				_dispDebugLog(9,"mbtcDisconnect: cannot close input stream");
				retval = RET_ERRICL;
		    }
	
		    try {
		    	if ( _socClient != null) {
		    		try {
		    			_socClient.close();
		    			_dispDebugLog(9,"mbtcDisconnect: closing socket");
		    		} catch (NullPointerException ee) {
						retval = RET_ERRCSO;
					}
					_socClient = null;
		    	}
		    } catch (IOException ex) {
				_socClient = null;
				_dispDebugLog(9,"mbtcDisconnect: cannot close socket");
				retval = RET_ERRCSO;
		    }
		}
		_dispDebugLog(9,"mbtcDisconnect: disconnected successfully");
		return retval;
    }


    /**
     * Connecting to the MODBUS master using address port and parameters set in 
     * mbtcSetup method. Method creates new Socket for communication.
     *
     * @return RET_OK (0)      on success<br>
     *         RET_NOTSET (21) when mbtcSetup has not been invoked<br>
     *         RET_ERRCON (22) cannot connect socket to MODBUS master<br>
     *         RET_ERRIST (23) cannot create input stream<br>
     *         RET_ERROST (24) cannot create output stream<br>
     *         RET_ERRSTO (25) cannot set socket timeout<br>
     *         
     */
    public int mbtcConnect() {

		if( _socAddress == null ) return RET_NOTSET;
	
		// we are creating unconnected socket
		_socClient = new Socket();
	
		// now we need to setup socket parameters recommended for ModbusTCP
		// TCP_NODELAY - we need to disable NAGLE for better real-time behavior
		if( _tcpNoDelay == 1 ) {
		    try {
			if(!_socClient.getTcpNoDelay()) {
			    _socClient.setTcpNoDelay(true);
			    _dispDebugLog(9,"mbtcConnect: setTcpNoDelay");
			}
			else {
			    _dispDebugLog(9,"mbtcConnect: TcpNoDelay is already set");
			}
	
		    } catch(SocketException ex) {
			// this exception is raised if underlying TCPIP stack does not support
			// TCP_NODELAY - if it is not supported then we ignore it
			_dispDebugLog(9,"mbtcConnect: cannot setTcpDelay. Ignoring ...");
		    }
		}
	
		// SO_KEEPALIVE - Normally on idle connection no data are sent so we need to enable it
		if( _keepAlive == 1 ) {
		    try {
			if(!_socClient.getKeepAlive()) {
			    _socClient.setKeepAlive(true);
			    _dispDebugLog(9,"mbtcConnect: setKeepAlive");
			}
			else {
			    _dispDebugLog(9,"mbtcConnect: KeepAlive already set");
			}
	
		    } catch(SocketException ex) {
			// this exception is raised if underlying TCPIP stack does not support
			// TCP_KEEPALIVE - if it is not supported then we ignore it
			_dispDebugLog(9,"mbtcConnect: cannot setKeepAlive. Ignoring ...");
		    }
		}
	
		// SO_TIMEOUT - is used by the read method to raise InteruptedIOException when timeout occurs
		if(_soTimeout > 0 ) {
		    try {
			  _socClient.setSoTimeout(_soTimeout);
			  _dispDebugLog(9,"mbtcConnect: setSoTimeout to " + String.valueOf(_soTimeout));
		    } catch(SocketException ex) {
		    	// this exception is raised if underlying TCPIP report error on
		    	// SO_TIMEOUT - cleaning up and return an error
		    	_dispDebugLog(9,"mbtcConnect: cannot setSoTimeout(" + String.valueOf(_soTimeout) + ")");
		    	try {
		    		_socClient.close();
		    		_socClient = null;
		    	} catch(IOException eex) {
		    		// ignore
		    	}
		    	return RET_ERRSTO;
		    }
		}
	
		// now we are ready to connect to the server
		try {
		    _socClient.connect(_socAddress, _conTimeout);
		    _dispDebugLog(9,"mbtcConnect: connected with _conTimeout = " + String.valueOf(_conTimeout));
		} catch (IOException ex) {
		    try {
			_socClient.close(); // if we cannot connect the socket is useless and we close it
			_socClient = null;
		    } catch(IOException eex) {
		    	_socClient = null; // if we cannot close it then socket is useless and we cleanup
		    }
		    _dispDebugLog(9,"mbtcConnect: connection cannot be established");
		    return RET_ERRCON;
		}
		
	
		// the socket is created so we can now create Input and Output Streams
		try {
		    _oStream = new DataOutputStream( _socClient.getOutputStream());
		} catch(IOException ex) {
		    // i cannot create output stream, so we have to cleanup
		    // the socket end exit with error
		    _dispDebugLog(9,"mbtcConnect: cannot create output stream.");
		    try {
		        _socClient.close();
		        _socClient = null;
		    } catch (IOException eex) {
		    	_dispDebugLog(9,"mbtcConnect: cannot close socket.");
		    	_socClient = null; // if we cannot close it then socket is useless and we cleanup
		    }
		    return RET_ERROST;
		}
	
		try {
		    _iStream = new DataInputStream(  _socClient.getInputStream());
		} catch(IOException ex) {
		    // i cannot create input stream, so we have to cleanup
		    // Previously created output stream then the socket end exit with error
		    _dispDebugLog(9,"mbtcConnect: cannot create input stream.");
		    try {
			// we should always close the output stream before input stream
			_oStream.close();
			_socClient.close();
			_oStream = null;
			_socClient = null;
		    } catch (IOException eex) {
		    	_dispDebugLog(9,"mbtcConnect: cannot close output stream or socket.");
		    	_oStream = null;
		    	_socClient = null;
		    }
		    return RET_ERRIST;
		}
	
		_dispDebugLog(9,"mbtcConnect: connected successfully");
		return RET_OK;
    }

     
    /**
     * Setup the initial parameters for TCP connection like IP Address/DNS Name and TCP port. 
     * It also gather additional optional parameters for controlling the behavior of the 
     * TCP connection, like: connection retry counter, request retry counter and socket parameters. 
     * This method should be called as the first one before mbtcConnect. If this
     * method fails then it indicates a problem with the network, so other methods from 
     * this class (e.g. mbtcConnect) will fail as well.
     *
     * @param  host       a String value with the IP address or DNS name of the PLC Master
     * @param  port       an int value with the TCP port
     * @param  reqNum     (varargs) an int value indicates the number of read/write request to be done to the PLC Master before an error will be returned (default:3)
     * @param  reconNum   (varargs) an int value indicates the number of TCP connection requests before an error will be returned (default:2)
     * @param  soTimeout  (varargs) an SO_TIMEOUT to avoid permanent blocking of InputStream associated with the Socket (default:1000) 
     * @param  conTimeout (varargs) an Socket connection timeout (default:1000)
     * @param  tcpNoDelay (varargs) disable the Nagle buffering algorithm. Value 1 disables buffering. Do not change it for MODBUS-TCP (default:1)
     * @param  keepAlive  (varargs) enable keep-alive packets for a socket connection. Value 1 enables it. Do not change it for MODBUS-TCP (default:1)
     * @return RET_OK (0)      on success<br>
     *         RET_BADARG (11) when bad arguments has been passed to InetSocketAddress constructor<br>
     *         RET_SECRES (12) when DNS name cannot be resolved<br>
     *         
     */
    public int mbtcSetup(String host, int port, int... additionalParams ) {
		// we creating and checking address and port of the server
		try {
		    _socAddress = new InetSocketAddress(host,port);
	
		} catch(IllegalArgumentException ex) {
		      // host is null or port is out of range 0k,64k
		      _socAddress = null;
		      _dispDebugLog(9,"mbtcSetup: illegal arguments in InetSocketAddress");
		      return RET_BADARG;
		} catch(SecurityException ex) {
		      // security manager is present and prevents to resolve "hostname"
		      _socAddress = null;
		      _dispDebugLog(9,"mbtcSetup: unable to resolve " + host);
		      return RET_SECRES;
		}
	
		if( additionalParams.length >= 1 ) _rawRequestRetry             = additionalParams[0];
		if( additionalParams.length >= 2 ) _rawRequestReconnectAndRetry = additionalParams[1];
		if( additionalParams.length >= 3 ) _soTimeout                   = additionalParams[2];
		if( additionalParams.length >= 4 ) _conTimeout                  = additionalParams[3];
		if( additionalParams.length >= 5 ) _tcpNoDelay                  = additionalParams[4];
		if( additionalParams.length >= 6 ) _keepAlive                   = additionalParams[5];
		
		_dispDebugLog(9,"mbtcSetup: ### Network parameters:");
		_dispDebugLog(9,"mbtcSetup:                         host = \"" + host + "\"");
		_dispDebugLog(9,"mbtcSetup:                         port = " + Integer.toString(port));
		_dispDebugLog(9,"mbtcSetup:             _rawRequestRetry = " + Integer.toString(_rawRequestRetry));
		_dispDebugLog(9,"mbtcSetup: _rawRequestReconnectAndRetry = " + Integer.toString(_rawRequestReconnectAndRetry));
		_dispDebugLog(9,"mbtcSetup:                   _soTimeout = " + Integer.toString(_soTimeout));
		_dispDebugLog(9,"mbtcSetup:                  _conTimeout = " + Integer.toString(_conTimeout));
		_dispDebugLog(9,"mbtcSetup:                  _tcpNoDelay = " + Integer.toString(_tcpNoDelay));
		_dispDebugLog(9,"mbtcSetup:                   _keepAlive = " + Integer.toString(_keepAlive));
		
		return RET_OK;
    } // end of mbtcSetup

}
