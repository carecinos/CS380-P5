import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

/**
 * @author cesar
 *
 */
public class UdpClient {

	/**
	 * @param args
	 */
	static byte[] dstAddr;
	public static void main(String[] args) throws IOException {
		try (Socket socket = new Socket("codebank.xyz", 38005)) {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			dstAddr = socket.getInetAddress().getAddress();
			double avgRTT = 0;


			//Hardcode handshake data 0xDEADBEEF to the initial IPv4 packet
			//Initial packet sent to server is the handshaking step
			byte[] handshakeData = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
			byte[] handshake = Ipv4(handshakeData);
			os.write(handshake);


			//Receive & Display Handshake response
			byte[] code = new byte[4];
			is.read(code);
			System.out.print("Handshake Response:\n0x");
			for(byte e: code)
				System.out.printf("%02X", e);

			//Receive the port number from server & display
			int port = 0;
			port = ((is.read() << 8) | is.read());
			System.out.println("\nPort number received: " + port + "\n");


			//Sending 12 UDP packets via 12 IPv4 packets
			for (int i = 1; i < 13; i++) {
				int dataSize = (int)Math.pow(2,i);

				byte[] udp = Udp(port, dataSize);
				byte[] ipv4 = Ipv4(udp);
				
				long rtt = 0;
				long end = 0;
				//Start timer
				long start = System.currentTimeMillis();

				//Send IPv4 packet to server
				os.write(ipv4);
				
				System.out.println("Sending packet with " + dataSize + " bytes of data");
				
				//Read response
				byte[] response = new byte[4];
				is.read(response);
				
				//End timer, find RTT
				end = System.currentTimeMillis();
				rtt = (end-start);
				avgRTT += rtt;
				
				//Display response & RTT
				System.out.print("Response: 0x");
				for(byte e: response)
					System.out.printf("%02X", e);
				
				System.out.println("\nRTT: " + rtt + "ms\n");

			}

			//Calculate & Display average RTT
			avgRTT /= 12;
			System.out.printf("Average RTT: %.2fms", avgRTT);
			System.out.println();
		}
	}

	/**
	 * This method creates an IPv4 packet
	 * @param data is the array of data to be sent in the packet 
	 * @return the IPv4 packet
	 */
	private static byte[] Ipv4(byte[] data) {
		
		byte[] packet = new byte[20+data.length];
		int size = (20+data.length);

		packet[0] = 0x45; 
		packet[1] = 0; 
		packet[2] = (byte) (size >> 8); 
		packet[3] = (byte) size; 
		packet[4] = 0; 
		packet[5] = 0; 
		packet[6] = 0x40; 
		packet[7] = 0; 
		packet[8] = 50; 
		packet[9] = 17; 
		
		for(int j = 12; j<16; j++){
			packet[j] = 0x00; //0 for sourcAddr
		}
		for(int j = 16; j < 20; j++){
			packet [j] = dstAddr[j-16];//destinationAddr
		}
		
		short check = checksum(packet);
		//Calculate checksum
		packet[10] = (byte) (check >>> 8);
		packet[11] = (byte) check; 
		
		//Packet Data
		for (int i = 0; i < data.length; i++) {
			packet[20+i] = (byte) data[i];
		}
		return packet;
	}

	/**
	 * This method creates a UDP packet that will be sent in an IPv4
	 * packet. 
	 * @param port is the destination port header for the UDP packet
	 * @param size is the data size for the UDP packet
	 * @return the UDP packet
	 */
	private static byte[] Udp(int port, int size) {
		byte[] packet = new byte[8+size];
		packet[0] = 0; 
		packet[1] = 0; 
		packet[2] = (byte) (port >> 8); 
		packet[3] = (byte) port; 
		packet[4] = (byte) (size >> 8); 
		packet[5] = (byte) size; 
		
		//Randomize Data
		Random rand = new Random();
		for (int i = 0; i < size; i++) {
			packet[i+8] = (byte) rand.nextInt();
		}
		byte[] pseudoheader = new byte[12];
		for(int j = 0; j<4; j++){
			pseudoheader[j] = 0; //0 for sourcAddr
		}
		for(int j = 4; j < 8; j++){
			pseudoheader[j] = dstAddr[j-4];//destinationAddr
		}
		pseudoheader[8] = 0; 
		pseudoheader[9] = 17; 
		pseudoheader[10] = packet[4];
		pseudoheader[11] = packet[5];


		byte[] checkUDP = new byte[8+size+12];
		for (int i = 0; i < checkUDP.length; i++) {
			if (i < (8+size)) {
				checkUDP[i] = packet[i];
			} else {
				checkUDP[i] = pseudoheader[i - (size+8)];
			}
		}

		//Calculate UDP checksum
		short checksum = checksum(checkUDP);
		packet[6] = (byte) ((checksum >> 8) & 0xff);
		packet[7] = (byte) (checksum & 0xff);

		return packet;
	}

	/**
	 * This method generates a checksum.
	 * @param header is the packet passed in to calculate the checksum
	 * @return the checksum
	 */
	public static short checksum(byte[] header) {
		 long sum = 0;
		 int l = header.length;
		 int i = 0;

	     while (l > 1) {
	    	 sum += ((header[i] << 8 & 0xFF00) | ((header[i+1]) & 0x00FF));
	    	 i +=2;
	    	 l -=2;
	    	 if ((sum & 0xFFFF0000) > 0) {
	    		 sum &= 0xFFFF;
	    		 sum++;
	    	 }
	     }
	     if (l > 0) {
	    	 sum += (header[i] << 8 & 0xFF00);
	    	 if((sum & 0xFFFF0000) > 0) {
	    		 sum &= 0xFFFF;
	    		 sum++;
	    	 }
	     }
	     return (short)((~((sum & 0xFFFF) + (sum >> 16))) & 0xFFFF);
	}
}
