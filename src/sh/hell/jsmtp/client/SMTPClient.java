package sh.hell.jsmtp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.hell.jsmtp.content.SMTPAddress;
import sh.hell.jsmtp.content.SMTPMail;
import sh.hell.jsmtp.exceptions.SMTPException;
import sh.hell.jsmtp.exceptions.TLSNegotiationFailedException;

import javax.naming.NamingException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Scanner;

@SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
public class SMTPClient
{
	private static final Logger logger = LoggerFactory.getLogger(SMTPClient.class);
	public final String serverIP;
	public final short serverPort;
	public final String serverWelcomeMessage;
	public String serverHostname;
	public final ArrayList<String> serverCapabilities = new ArrayList<>();
	public final TrustManager[] trustManagers;
	public Scanner scanner;
	public OutputStreamWriter writer;
	public String hostname;
	public boolean extendedSMTP = true;
	private Socket socket;

	public SMTPClient(String serverIP, int serverPort) throws IOException, SMTPException
	{
		this(serverIP, serverPort, new TrustManager[]{new X509TrustManager()
		{
			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
			{
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
			{
			}

			public java.security.cert.X509Certificate[] getAcceptedIssuers()
			{
				return null;
			}
		}});
	}

	public SMTPClient(String serverIP, int serverPort, TrustManager[] trustManagers) throws IOException, SMTPException
	{
		logger.info("Connecting to " + serverIP + ":" + serverPort + "...");
		this.serverIP = serverIP;
		this.serverPort = (short) serverPort;
		this.socket = new Socket();
		this.socket.connect(new InetSocketAddress(serverIP, serverPort), 3000);
		this.socket.setSoTimeout(3000);
		this.scanner = new Scanner(new InputStreamReader(socket.getInputStream())).useDelimiter("\r\n");
		this.writer = new OutputStreamWriter(socket.getOutputStream());
		SMTPResponse response = readResponse();
		if(!response.status.equals("220"))
		{
			throw new SMTPException("Server declined connection: " + response.toString());
		}
		this.serverWelcomeMessage = response.toString().substring(4);
		this.trustManagers = trustManagers;
	}

	public static SMTPResponse sendMail(SMTPMail mail) throws NamingException, IOException, SMTPException
	{
		//noinspection ConstantConditions
		return SMTPClient.fromAddress(mail.recipients.get(0)).hello(mail.sender.getDomain()).send(mail);
	}

	public static SMTPClient fromAddress(SMTPAddress address) throws NamingException
	{
		for(String server : address.getMailServers())
		{
			SMTPClient client = SMTPClient.fromServer(server);
			if(client != null)
			{
				return client;
			}
		}
		logger.error("Didn't find any server for " + address + ".");
		return null;
	}

	public static SMTPClient fromServer(String serverIP)
	{
		try
		{
			return new SMTPClient(serverIP, 587);
		}
		catch(Exception e)
		{
			logger.info("Connection to " + serverIP + ":587 failed: " + e.getMessage());
		}
		try
		{
			return new SMTPClient(serverIP, 25);
		}
		catch(Exception e)
		{
			logger.info("Connection to " + serverIP + ":25 failed: " + e.getMessage());
		}
		try
		{
			return new SMTPClient(serverIP, 465);
		}
		catch(Exception e)
		{
			logger.info("Connection to " + serverIP + ":465 failed: " + e.getMessage());
		}
		return null;
	}

	public void close()
	{
		try
		{
			write("QUIT").flush();
		}
		catch(IOException ignored)
		{
		}
		try
		{
			writer.close();
		}
		catch(IOException ignored)
		{
		}
		try
		{
			socket.close();
		}
		catch(IOException ignored)
		{
		}
		scanner.close();
	}

	public boolean isOpen()
	{
		return !socket.isClosed();
	}

	public boolean isEncrypted()
	{
		return socket instanceof SSLSocket;
	}

	public SMTPResponse readResponse() throws SMTPException
	{
		int tries = 0;
		while(!scanner.hasNext())
		{
			if(++tries > 60)
			{
				throw new SMTPException("Server failed to reply within 3 seconds.");
			}
			try
			{
				Thread.sleep(50);
			}
			catch(InterruptedException ignored)
			{

			}
		}
		String line = scanner.next();
		final SMTPResponse response = new SMTPResponse(line);
		logger.debug(serverIP + " > " + line);
		while(line.substring(3, 4).equals("-"))
		{
			line = scanner.next();
			response.lines.add(line.substring(4));
			logger.debug(serverIP + " > " + line);
		}
		if(response.status.equals("421"))
		{
			throw new SMTPException("Remote server shut down.");
		}
		return response;
	}

	public SMTPClient write(String message) throws IOException
	{
		for(String line : message.split("\r\n"))
		{
			writer.write(line + "\r\n");
			logger.debug(serverIP + " < " + line);
		}
		return this;
	}

	public SMTPClient flush() throws IOException
	{
		writer.flush();
		return this;
	}

	/**
	 * Sends EHLO (or HELO) to the server, and STARTTLS if supported.
	 *
	 * @param hostname The hostname of the machine sending the email.
	 * @return this
	 * @throws IOException   If writing fails.
	 * @throws SMTPException If an SMTP protocol error occurred.
	 */
	public SMTPClient hello(String hostname) throws IOException, SMTPException
	{
		return this.hello(hostname, false);
	}

	/**
	 * Sends EHLO (or HELO) to the server, and STARTTLS if supported.
	 *
	 * @param hostname         The hostname of the machine sending the email.
	 * @param ignoreEncryption Set to true to ignore STARTTLS capabilities.
	 * @return this
	 * @throws IOException   If writing fails.
	 * @throws SMTPException If an SMTP protocol error occurred.
	 */
	public SMTPClient hello(String hostname, boolean ignoreEncryption) throws SMTPException, IOException
	{
		SMTPResponse response = null;
		if(extendedSMTP)
		{
			this.hostname = hostname;
			write("EHLO " + hostname).flush();
			response = readResponse();
			if(!response.status.equals("250"))
			{
				extendedSMTP = false;
				response = null;
			}
		}
		if(response == null)
		{
			write("HELO " + hostname).flush();
			response = readResponse();
		}
		if(response.status.equals("250"))
		{
			this.serverHostname = response.lines.get(0);
			synchronized(this.serverCapabilities)
			{
				this.serverCapabilities.clear();
				for(int i = 1; i < response.lines.size(); i++)
				{
					this.serverCapabilities.add(response.lines.get(i).toUpperCase());
				}
				if(!ignoreEncryption && !isEncrypted() && serverCapabilities.contains("STARTTLS"))
				{
					write("STARTTLS").flush();
					SMTPResponse starttls_response = readResponse();
					if(starttls_response.status.equals("220"))
					{
						try
						{
							final SSLContext sslContext = SSLContext.getInstance(Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) >= 11 ? "TLSv1.3" : "TLSv1.2");
							sslContext.init(null, this.trustManagers, new SecureRandom());
							final SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostName(), socket.getPort(), true);
							sslSocket.setUseClientMode(true);
							sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
							sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
							sslSocket.startHandshake();
							if(sslSocket.getSession().getCipherSuite().startsWith("TLS handshake failed"))
							{
								throw new TLSNegotiationFailedException(sslSocket.getSession().getCipherSuite());
							}
							logger.debug(serverIP + " = Cipher suite: " + sslSocket.getSession().getCipherSuite());
							this.socket = sslSocket;
							this.scanner = new Scanner(new InputStreamReader(socket.getInputStream())).useDelimiter("\r\n");
							this.writer = new OutputStreamWriter(socket.getOutputStream());
							this.hello(hostname, true);
						}
						catch(IOException | GeneralSecurityException e)
						{
							throw new TLSNegotiationFailedException(e.getMessage());
						}
					}
					else
					{
						throw new TLSNegotiationFailedException(starttls_response.toString());
					}
				}
			}
		}
		else
		{
			throw new SMTPException("Hello failed: " + response.toString());
		}
		return this;
	}

	public boolean verify(SMTPAddress address) throws IOException, SMTPException
	{
		write("VRFY " + address.toString()).flush();
		SMTPResponse response = readResponse();
		if(response.status.equals("250"))
		{
			return true;
		}
		else if(response.status.equals("501"))
		{
			throw new SMTPException("Couldn't verify " + address.toString() + ": " + response.toString());
		}
		return false;
	}

	public SMTPResponse send(SMTPMail mail) throws IOException, SMTPException
	{
		final String rawContents = mail.getRawContents();
		final int size = rawContents.length();
		boolean supportsSize = false;
		for(String serverCapability : serverCapabilities)
		{
			if(serverCapability.equals("SIZE"))
			{
				supportsSize = true;
			}
			else if(serverCapability.startsWith("SIZE "))
			{
				supportsSize = true;
				try
				{
					final int sizeLimit = Integer.parseInt(serverCapability.substring(5));
					if(sizeLimit > 0 && sizeLimit < size)
					{
						throw new SMTPException("Our email is too big for the server.");
					}
				}
				catch(NumberFormatException ignored)
				{

				}
				break;
			}
		}
		write("MAIL FROM:<" + mail.sender.mail + ">" + (supportsSize ? " SIZE=" + size : "")).flush();
		SMTPResponse response = readResponse();
		if(!response.status.equals("250"))
		{
			throw new SMTPException("Server denied " + mail.sender.toString() + " as sender address: " + response.toString());
		}
		for(SMTPAddress recipient : mail.recipients)
		{
			write("RCPT TO:<" + recipient.mail + ">").flush();
			response = readResponse();
			if(!response.status.equals("250"))
			{
				throw new SMTPException("Server denied " + recipient.mail + " as recipient: " + response.toString());
			}
		}
		write("DATA").flush();
		response = readResponse();
		if(!response.status.equals("354"))
		{
			throw new SMTPException("Invalid response to DATA: " + response);
		}
		write(rawContents).write(".").flush();
		response = readResponse();
		if(!response.status.equals("250"))
		{
			throw new SMTPException("Server refused to accept email: " + response);
		}
		return response;
	}
}
