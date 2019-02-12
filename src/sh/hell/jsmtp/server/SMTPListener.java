package sh.hell.jsmtp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SMTPListener extends Thread
{
	public final ServerSocket socket;
	private final SMTPServer server;

	SMTPListener(SMTPServer server, int port) throws IOException
	{
		super("SMTPListener");
		this.server = server;
		this.socket = new ServerSocket(port);
		this.start();
	}

	@Override
	public void run()
	{
		try
		{
			do
			{
				Socket clientSocket = socket.accept();
				try
				{
					if(server.eventHandler.isIPAccepted(clientSocket.getRemoteSocketAddress().toString()))
					{
						synchronized(server.sessions)
						{
							server.sessions.add(new SMTPSession(server, clientSocket));
						}
					}
					else
					{
						clientSocket.close();
					}
				}
				catch(IOException ignored)
				{
				}
			}
			while(!this.isInterrupted());
		}
		catch(IOException ignored)
		{
		}
	}
}
