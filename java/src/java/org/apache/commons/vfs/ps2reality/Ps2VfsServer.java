package org.apache.commons.vfs.ps2reality;
import java.net.*;
import java.io.*;
import java.util.*;
public class Ps2VfsServer extends Thread
{   
	//todo admin delete client 
	static Vector clientList= new Vector();
	static Vector clientListcomandos= new Vector();
	static int counter=0;
	
	
	static PrintWriter screenOut = new PrintWriter(System.out, true);	
	public static void main(String[] args) throws IOException {
			Thread tempThread;
	
			Ps2VfsClientThread ps2Client;
	        ServerSocket serverSocket = null;
			boolean listening = true;
			int port=6969;
			try{
				serverSocket = new ServerSocket(port);
				screenOut.println("Playstation 2 Virtual File System Example");
				
				screenOut.println("#  _____     ___ ____");
				screenOut.println("#   ____|   |    ____|      PSX2 OpenSource Project");
				screenOut.println("#  |     ___|   |____       (C)2003,Bigboss, Mavy & Hermes ");
				screenOut.println("#             (bigboss@ps2reality.net,mavy@ps2reality.net,hermes@ps2reality.net)");
				screenOut.println("#  -----------------------------------------------------------------------------");


				screenOut.println("Server listening in port "+port);
				while (listening){
				
				ps2Client = new Ps2VfsClientThread(serverSocket.accept());
				//counter++;
				clientList.add(ps2Client);
				tempThread = new Thread(ps2Client);
				tempThread.start();
				
				}


			}catch(IOException e){
				System.err.println("Error opening server socket in  port: "+port);
				System.exit(-1);
			}
						
			
	}

}
