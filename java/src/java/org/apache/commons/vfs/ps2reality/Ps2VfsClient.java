package org.apache.commons.vfs.ps2reality;
import java.net.*;
import java.io.*;
import java.util.*;
//This import for support jakarta vfs for command dir, pwf, and next features
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.FileUtil;
import org.apache.commons.vfs.VFS;
import org.apache.commons.vfs.Selectors;


class Ps2VfsClientThread extends Thread {

    //Strings for our commands
	public String HELLO="HELLO";
	public String OPEN="OPENA";
	public String CLOSE="CLOSE";
	public String READ="READA";
	public String SEEK="SEEKA";
	public String EXIT="EXITA";
	public String DIR="DOPEN";
	public String PWD="PWDAA";
    public String IOERROR="IOERR";

    //Byte structure for our commands
	public byte cmdHello[];
	public byte cmdOpen[];
	public byte cmdClose[];
	public byte cmdRead[];
	public byte cmdSeek[];
	public byte cmdExit[];
	public byte cmdDir[];
	public byte cmdPwd[];

	//For pwd and dir commands we need a jakarta VFS FileSystemManager and a FileObject
	private  FileSystemManager mgr;
	private FileObject cwd;
    //hashtable for our open files, now ps2vfs in ps2 side open a new thread for file. Perhaps i have to change this hashtable
	private Hashtable filesopened;
	//max number of file in java side, but if i am opening a new thread with each open ... need change
	private final static int maxnum=5;
    //index for add to hashtable... need change
	private int numfilesopened=0;
	
	//Our eyes in java side
	PrintWriter screenOut = new PrintWriter(System.out, true);


	private Socket socket = null;
	public String clientIP;
	//our data io 
	public DataInputStream in;
	public DataOutputStream out;
	
	public String commandString;
	public int numbytes;
	public int mode;
	public int code;
	public boolean done=false;
	
	public Ps2VfsClientThread(Socket socket){
		super("Ps2VfsClientThread");
		this.socket = socket;
		
		initVFS(); //for command pwd and dir
		
		initCommand(); //init our commands
		//open our io
		try
		{
		 this.in=new DataInputStream(socket.getInputStream());
		 this.out=new DataOutputStream(socket.getOutputStream());
		 
		}
		catch(IOException e)
		{			
			 screenOut.println("Error initalizing IO");
		}
	}

	
		
	

	
	public String readCommandString( )
	{
		
		byte command[]=new byte[5];
		try
		{
			code=in.readInt();
			in.read(command);
			return new String(command);
		}
		catch(IOException e)
		{
			
			return IOERROR;  

		}
	}
	
	private void initVFS()
	{

		try
		{
			mgr = VFS.getManager();
			cwd = mgr.resolveFile( System.getProperty( "user.dir" ) );
			filesopened=new Hashtable();
			numfilesopened=0;
			screenOut.println("New Vfs manager");
			screenOut.println("Hashtable fileopened created");
            screenOut.println("Number of files opened in ps2 side "+numfilesopened);
		}
		catch ( Exception e )
        {
            e.printStackTrace();
            System.exit( 1 );
        }

	}
	public void Hello()
	{   
	
		try
		{
			//hello command only called with initial conection response with 0+HELLO
            System.arraycopy(returnInt(0),0,cmdHello,0,4);
			this.out.write(cmdHello,0,9);
			this.out.flush();
		}
		catch(IOException e)
		{
			screenOut.println("Error IO processing command HELLO");
		}
	}
	public  void Exit()
	{
       try
		{   //exit command only called to end conection response with 0+EXITA
			System.arraycopy(returnInt(0),0,cmdExit,0,4);
			this.out.write(cmdExit,0,9);
			this.out.flush();
		}
		catch(IOException e)
		{
			screenOut.println("Error IO processing command EXIT");
		}

	}
	public void Open()
	{
		//open command we read filename and try to open in read mode(default mode now)
		//if succes response fileid+OPENA 
		//error reponse -1+OPENA
		int fd;
		try{
            byte name []=new byte[this.code];
			this.in.read(name);
            String filename=new String(name);
            System.out.println("filename: "+filename);
			if(numfilesopened<maxnum)
			{
				RandomAccessFile rfile=new RandomAccessFile(filename,"r");
				numfilesopened++;
				filesopened.put(new Integer(numfilesopened),rfile);
				screenOut.println("Number of files opened in ps2 side "+numfilesopened);
			    fd=numfilesopened;
			}
			else
			{
                screenOut.println("Sorry maxnum files already opened");
				fd=-1;
			}
	
			System.arraycopy(returnInt(fd),0,cmdOpen,0,4);
			this.out.write(cmdOpen,0,9);
			this.out.flush();
			 
		}catch (IOException e ) //catch first exception for file not found
        {

           e.printStackTrace();
		    fd=-1;
			try{
			System.arraycopy(returnInt(fd),0,cmdOpen,0,4);
			this.out.write(cmdOpen,0,9);
			this.out.flush();
			}catch (IOException e2 )
			{
				e2.printStackTrace();
				System.exit( 1 );
			}
          
        }
		
		

	}
	public void Close()
	{
		//command for closing file
		Integer fdint=new Integer(this.code);
		int returnCode;
		try{
			if(filesopened.containsKey(fdint))
			{
				RandomAccessFile rfile=(RandomAccessFile)filesopened.get(fdint);
				rfile.close();
                screenOut.println("File with fd "+ this.code+" closed");
				filesopened.remove(fdint);
                numfilesopened--;
				screenOut.println("Number of files opened in ps2 side "+numfilesopened);
                returnCode=0;

			}
			else
			{
				screenOut.println("Sorry fd "+ this.code+"does not exist");
				returnCode=-1;

			}
			System.arraycopy(returnInt(returnCode),0,cmdClose,0,4);
			this.out.write(cmdClose,0,9);
			this.out.flush();
		}
		catch ( IOException e1 )
        {
            e1.printStackTrace();
            returnCode=-1;
		    try{
			System.arraycopy(returnInt(returnCode),0,cmdClose,0,4);
			this.out.write(cmdClose,0,9);
			this.out.flush();
			}catch (IOException e2 )
			{
				e2.printStackTrace();
				System.exit( 1 );
			}
			
        }



	}
	public void  Read()
	{
				 
		//command for read we have already fileid and now is time to read size 
		int returnCode;
		Integer fdint=new Integer(this.code);
		int size;
		byte buffer[]=null;
		try{
			this.numbytes=in.readInt();
            screenOut.println(" numbytes to read "+ this.numbytes);
			if(filesopened.containsKey(fdint))
			{
				RandomAccessFile rfile=(RandomAccessFile)filesopened.get(fdint);
                
				long tam=rfile.length()-rfile.getFilePointer();
				//check if we have bytes
				if (tam>=numbytes)
				{
					size=numbytes;
				}
				else
				{
					size=(int)tam;
				}
				buffer=new byte[size];
				rfile.readFully(buffer);
                returnCode=size;

			}
			else
			{
				screenOut.println("Sorry fd "+ this.code+"does not exist");
				returnCode=-1;

			}
			System.arraycopy(returnInt(returnCode),0,cmdRead,0,4);
			if(returnCode>=0)
			{
				//we send our command Number of bytes readed+ READA and our bytes... we need put cache buffer now we are reading blocks of 16 k in iop side... we need change this for ps2vfs
				
				this.out.write(cmdRead,0,9);
				this.out.write(buffer,0,returnCode);
				this.out.flush();
			}
			else
			{
				
				this.out.write(cmdRead,0,9);
				this.out.flush();

			}

		}
		catch ( IOException e1 )
        {
            e1.printStackTrace();
            returnCode=-1;
			try{
			System.arraycopy(returnInt(returnCode),0,cmdRead,0,4);
			this.out.write(cmdRead,0,9);
			this.out.flush();

			}catch (IOException e2 )
			{
				e2.printStackTrace();
				System.exit( 1 );
			}
        }	
	}
	public void  Seek()
	{
		//command seek now we read offset and whence
		int numBytes;
		int modeSeek;
		long lnumbytes;
		long currentpos;
	    int returnCode;
		Integer fdint=new Integer(this.code);
		
		try{
				numBytes=in.readInt();
				modeSeek=in.readInt();
               screenOut.println(numBytes+" mode"+modeSeek+" code "+this.code);//only for debug we need add defines for log
				
				lnumbytes=(long)numBytes;
				if(filesopened.containsKey(fdint))
				{
					RandomAccessFile rfile=(RandomAccessFile)filesopened.get(fdint);
					long size=rfile.length();
                
					switch(modeSeek)
					{   //java seek take always SEEK_SET 
						case 0://SEEK_SET
								if(lnumbytes<=size)
								{
									rfile.seek(lnumbytes);
									returnCode=numBytes;
								}
								else
								{	rfile.seek(size);
									returnCode=(int)size;
								}
								
								break;
						case 1://SEEK_CURR
								currentpos=rfile.getFilePointer();
								if(currentpos+lnumbytes<=size && currentpos+lnumbytes>=0 )
								{
									rfile.seek(currentpos+lnumbytes);
									returnCode=(int)(currentpos+lnumbytes);
								}
								else
								{
									if(currentpos+lnumbytes>size)
									{
										rfile.seek(size);
										returnCode=(int)size;
									}
									else
									{	
										rfile.seek(0);
										returnCode=0;

									}
								}
								returnCode=(int)rfile.getFilePointer();
								break;
						case 2:  //SEEK_END
								if(lnumbytes==0)
								{	
									rfile.seek(size);
									returnCode=(int)size;
								}
								else
								{
									returnCode=-1;
								}
								
								break;
						default:   
								returnCode=-1;
								break;
					}
					
			//	if(rfile.length()==code)
			//	{
			//		rfile.seek(0);
			//	}
				}
				else
				{
					  screenOut.println("Sorry fd "+ this.code+" does not exist in java server");
					returnCode=-1;

				}
				System.arraycopy(returnInt(returnCode),0,cmdSeek,0,4);
				
				this.out.write(cmdSeek,0,9);
				this.out.flush();
		}
		catch ( IOException e1 )
        {
            e1.printStackTrace();
            returnCode=-1;
			try{
			System.arraycopy(returnInt(returnCode),0,cmdSeek,0,4);
				
				this.out.write(cmdSeek,0,9);
			//this.out.write(returnInt(returnCode),0,4);
			//this.out.write(SEEK.getBytes(),0,5);
			this.out.flush();
		    }catch (IOException e2 )
			{
				e2.printStackTrace();
				System.exit( 1 );
			}
        }	
	}
	public void Pwd()
	{
		int size;
		try{
	
			String dir=cwd.getName().getPath();
		    int path=(cwd.getName().getPath()).length();
            System.arraycopy(returnInt(path),0,cmdPwd,0,4);
            this.out.write(cmdPwd,0,9);
		    this.out.write(dir.getBytes(),0,path);
			this.out.flush();
		 
		}catch ( IOException e )//catch first  FileSystemException 
        {
            e.printStackTrace();
			
			try{
				System.arraycopy(returnInt(-1),0,cmdPwd,0,4);
				this.out.write(cmdPwd,0,9);

			}catch (IOException e2 )
			{
				e2.printStackTrace();
				System.exit( 1 );
			}
        }
	}
	public void Dir()
	{
		int size;
		
		try{
			//we can add here name directory to read, now only list default directory
		String dir=cwd.getName().getPath();
		
		int path=(cwd.getName().getPath()).length();
 
        FileObject realDirs[] = cwd.getChildren();
		
		System.arraycopy(returnInt(realDirs.length),0,cmdDir,0,4);
		this.out.write(cmdDir,0,9);
        for(int i=0;i<realDirs.length;i++)
		{	
			
			String name=realDirs[i].getName().getBaseName();
			 this.out.write(returnInt(name.length()),0,4);
            this.out.write(name.getBytes(),0,name.length());
			
		}
		
		}catch ( Exception e )
        {
            e.printStackTrace();
			try{
				System.arraycopy(returnInt(-1),0,cmdDir,0,4);
				this.out.write(cmdDir,0,9);

			}catch (IOException e2 )
			{
				e2.printStackTrace();
				System.exit( 1 );
			}
           
        }
		

	
	}

		public void run(){
		
		
		clientIP = socket.getInetAddress().getHostAddress();
		screenOut.println("PS2 Client conected from: "+clientIP);
		
			while(!done)
			{   
	
				commandString=readCommandString();
				screenOut.println("Receive cmd:"+commandString+" code= "+code);
				switch(commandString.charAt(0))
				{

					case 'h': 
						screenOut.println("command Hello called ");
						Hello();
						break;
					case 'o': 

						screenOut.println("command Open called ");
						
						Open();
						break;
					case 'c':
								screenOut.println("Ps2Vfs Close called ");

						Close();
						break;
					case 'r': 
								screenOut.println("Ps2Vfs Read called ");
                       
						Read();
						break;
					
					case 's':
								
                        screenOut.println("Ps2Vfs Seek called ");
						
						Seek();
						break;
					case 'd':
								
                        screenOut.println("Ps2Vfs Dir called ");
						
						Dir();
						break;
					case 'p':
								
                        screenOut.println("Ps2Vfs Pwd called ");
						
						Pwd();
						break;
					
					default:
						screenOut.println("Command not recognized:"+commandString);
						break;
					case 'e': 
								screenOut.println("Ps2Vfs Exit called ");

						Exit();
						done=true;
						break;
				}
				
	
			}
			
			try
		{
			this.in.close();
			this.out.close();
			this.socket.close();
		}
		catch(IOException e)
		{
			screenOut.println("Error closing IO");
		}
		
	}
	public byte[] returnInt(int a)
	{
		byte array[]=new byte[4];

		array[0]=(byte)(a >> 24 & 0xff);
        array[1]=(byte)(a >> 16 & 0xff);
		array[2]=(byte)(a >> 8 & 0xff);
		array[3]=(byte)(a >> 0 & 0xff);
		return array;
	}

	//initializing byte structure for our commands 
	public void initCommand()
	{

		cmdHello=new byte[9];
		cmdOpen=new byte[9];
		cmdClose=new byte[9];
		cmdRead=new byte[9];
		cmdSeek=new byte[9];
		cmdExit=new byte[9];
		cmdPwd=new byte[9];
		cmdDir=new byte[9];
		System.arraycopy(HELLO.getBytes(),0,cmdHello,4,5);
		System.arraycopy(OPEN.getBytes(),0,cmdOpen,4,5);
		System.arraycopy(CLOSE.getBytes(),0,cmdClose,4,5);
		System.arraycopy(READ.getBytes(),0,cmdRead,4,5);
		System.arraycopy(SEEK.getBytes(),0,cmdSeek,4,5);
		System.arraycopy(EXIT.getBytes(),0,cmdExit,4,5);
		System.arraycopy(PWD.getBytes(),0,cmdPwd,4,5);
		System.arraycopy(DIR.getBytes(),0,cmdDir,4,5);

	}
			
}