package sh.hell.jsmtp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.hell.jsmtp.SMTPAddress;
import sh.hell.jsmtp.content.SMTPContent;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
	public final HashMap<String, String> emailHeaders = new HashMap<>();
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

	public static SMTPResponse sendMail(String from, String to, String subject, SMTPContent content) throws NamingException, IOException, SMTPException
	{
		return sendMail(SMTPAddress.fromText(from), SMTPAddress.fromText(to), subject, content);
	}

	public static SMTPResponse sendMail(SMTPAddress from, SMTPAddress to, String subject, SMTPContent content) throws NamingException, IOException, SMTPException
	{
		SMTPClient client = SMTPClient.fromAddress(to);
		if(client == null)
		{
			throw new SMTPException("Unable to find server for " + to.mail);
		}
		SMTPResponse response = client.hello(from.getDomain()).from(from).to(to).subject(subject).send(content);
		client.close();
		return response;
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
		writer.write(message + "\r\n");
		logger.debug(serverIP + " < " + message);
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
	 * @param hostname The hostname of the machine sending the email.
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

	/**
	 * Specifies the given address as the email sender, and starts TLS if the server requires it.
	 *
	 * @param smtpAddress The sender address.
	 * @return this
	 * @throws IOException   If writing fails.
	 * @throws SMTPException If an SMTP protocol error occurred.
	 */
	public SMTPClient from(SMTPAddress smtpAddress) throws IOException, SMTPException
	{
		if(!smtpAddress.isValid())
		{
			smtpAddress = smtpAddress.validCopy();
		}
		write("MAIL FROM:<" + smtpAddress.mail + ">").flush();
		addHeader("from", smtpAddress.toString());
		SMTPResponse response = readResponse();
		if(!response.status.equals("250"))
		{
			throw new SMTPException("Server denied " + smtpAddress.mail + " as sender address: " + response.toString());
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

	public SMTPClient to(SMTPAddress smtpAddress) throws IOException, SMTPException
	{
		if(!smtpAddress.isValid())
		{
			smtpAddress = smtpAddress.validCopy();
		}
		write("RCPT TO:<" + smtpAddress.mail + ">").flush();
		SMTPResponse response = readResponse();
		if(!response.status.equals("250"))
		{
			throw new SMTPException("Server denied " + smtpAddress.mail + " as recipient: " + response.toString());
		}
		synchronized(emailHeaders)
		{
			if(emailHeaders.containsKey("to"))
			{
				addHeader("cc", smtpAddress.toString());
			}
			else
			{
				emailHeaders.put("to", smtpAddress.toString());
			}
		}
		return this;
	}

	public SMTPClient cc(SMTPAddress smtpAddress) throws IOException, SMTPException
	{
		return this.to(smtpAddress);
	}

	public SMTPClient bcc(SMTPAddress smtpAddress) throws IOException, SMTPException
	{
		if(!smtpAddress.isValid())
		{
			smtpAddress = smtpAddress.validCopy();
		}
		write("RCPT TO:<" + smtpAddress.mail + ">").flush();
		SMTPResponse response = readResponse();
		if(!response.status.equals("250"))
		{
			throw new SMTPException("Server denied " + smtpAddress.mail + " as recipient: " + response);
		}
		return this;
	}

	/**
	 * Sets the subject header or appends to it if already present.
	 *
	 * @param subject The subject of the email.
	 * @return this
	 */
	public SMTPClient subject(String subject)
	{
		try
		{
			return this.addHeader("subject", subject);
		}
		catch(SMTPException ignored)
		{
		}
		return this;
	}

	/**
	 * Creates a header or appends to its value if it already exists.
	 *
	 * @param name  The name of the header.
	 * @param value The value of the header.
	 * @return this
	 * @throws SMTPException If the header name contains a colon, making it invalid.
	 */
	public SMTPClient addHeader(String name, String value) throws SMTPException
	{
		if(name.contains(":"))
		{
			throw new SMTPException("Invalid header name: " + name);
		}
		name = name.toLowerCase();
		synchronized(emailHeaders)
		{
			if(emailHeaders.containsKey(name))
			{
				emailHeaders.put(name, emailHeaders.get(name) + ", " + value);
			}
			else
			{
				emailHeaders.put(name, value);
			}
		}
		return this;
	}

	public SMTPClient removeHeader(String name)
	{
		synchronized(emailHeaders)
		{
			emailHeaders.remove(name);
		}
		return this;
	}

	public SMTPResponse send(SMTPContent _body) throws IOException, SMTPException
	{
		write("DATA").flush();
		SMTPResponse response = readResponse();
		if(!response.status.equals("354"))
		{
			throw new SMTPException("Invalid response to DATA: " + response);
		}
		synchronized(emailHeaders)
		{
			if(!emailHeaders.containsKey("date"))
			{
				emailHeaders.put("date", SMTPContent.RFC2822.format(new Date()));
			}
			if(!emailHeaders.containsKey("mime-version"))
			{
				emailHeaders.put("mime-version", "1.0");
			}
			emailHeaders.remove("content-type");
			emailHeaders.remove("content-transfer-encoding");
			for(Map.Entry<String, String> header : emailHeaders.entrySet())
			{
				write(header.getKey() + ": " + header.getValue());
			}
		}
		final String body = _body.getBody();
		for(String line : body.split("\n"))
		{
			if(line.startsWith("."))
			{
				write("." + line);
			}
			else
			{
				write(line);
			}
		}
		write(".").flush();
		response = readResponse();
		if(!response.status.equals("250"))
		{
			throw new SMTPException("Server refused to accept email: " + response);
		}
		return response;
	}
}
