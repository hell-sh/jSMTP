package sh.hell.jsmtp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.hell.jsmtp.SMTPAddress;
import sh.hell.jsmtp.content.SMTPContent;
import sh.hell.jsmtp.exceptions.InvalidAddressException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Scanner;

@SuppressWarnings("WeakerAccess")
public class SMTPSession extends Thread
{
	private static final Logger logger = LoggerFactory.getLogger(SMTPSession.class);
	private final SMTPServer server;
	public String hostname;
	public boolean extendedSMTP = false;
	Socket socket;
	OutputStreamWriter writer;
	Scanner scanner;
	private SMTPMail buildingMail;

	SMTPSession(SMTPServer server, Socket socket) throws IOException
	{
		super("SMTPSession on :" + socket.getLocalPort());
		this.server = server;
		this.socket = socket;
		this.writer = new OutputStreamWriter(socket.getOutputStream());
		this.start();
	}

	void write(String message) throws IOException
	{
		writer.write(message + "\r\n");
		logger.debug((hostname == null ? socket.getRemoteSocketAddress().toString() : hostname) + " < " + message);
	}

	private void reset()
	{
		this.hostname = null;
		this.extendedSMTP = false;
		this.buildingMail = null;
	}

	@Override
	public void run()
	{
		try
		{
			write("220 " + server.eventHandler.getWelcomeMessage(this));
			writer.flush();
			boolean continueDo;
			do
			{
				continueDo = false;
				try
				{
					scanner = new Scanner(new InputStreamReader(socket.getInputStream())).useDelimiter("\r\n");
					do
					{
						String line = scanner.next();
						logger.debug((hostname == null ? socket.getRemoteSocketAddress().toString() : hostname) + " > " + line);
						if(line.toUpperCase().startsWith("HELO"))
						{
							String[] arr = line.split(" ");
							if(arr.length == 2)
							{
								hostname = arr[1];
								write("250 " + server.eventHandler.getHostname(this));
							}
							else
							{
								write("501 Syntax: HELO hostname");
							}
						}
						else if(line.toUpperCase().startsWith("EHLO"))
						{
							String[] arr = line.split(" ");
							if(arr.length == 2)
							{
								hostname = arr[1];
								extendedSMTP = true;
								write("250-" + server.eventHandler.getHostname(this));
								write("250-VRFY");
								if(!isEncrypted())
								{
									write("250-STARTTLS");
								}
								write("250-8BITMIME");
								write("250 SMTPUTF8");
							}
							else
							{
								write("501 Syntax: EHLO hostname");
							}
						}
						else if(line.toUpperCase().startsWith("NOOP"))
						{
							write("250 OK");
						}
						else if(line.toUpperCase().startsWith("RSET"))
						{
							this.reset();
							write("250 OK");
						}
						else if(line.toUpperCase().startsWith("QUIT"))
						{
							write("221 Make sure to share the hell out of it if you liked it! ...and I'll see you next time.");
							this.interrupt();
						}
						else if(hostname == null)
						{
							write("503 Send HELO or EHLO first.");
						}
						else if(line.toUpperCase().startsWith("STARTTLS"))
						{
							if(isEncrypted())
							{
								write("454 The Transport Layer Security spell is still active.");
							}
							else
							{
								write("220 Cast the Transport Layer Security spell." + (server.eventHandler.isEncryptionRequired(this) ? " If this fails, I will disconnect." : ""));
								writer.flush();
								try
								{
									InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
									socket = server.sslSocketFactory.createSocket(socket, remoteAddress.getHostName(), socket.getPort(), true);
									((SSLSocket) socket).setUseClientMode(false);
									((SSLSocket) socket).setEnabledProtocols(((SSLSocket) socket).getSupportedProtocols());
									((SSLSocket) socket).setEnabledCipherSuites(((SSLSocket) socket).getSupportedCipherSuites());
									((SSLSocket) socket).startHandshake();
									logger.debug((hostname == null ? socket.getRemoteSocketAddress().toString() : hostname) + " = Cipher suite: " + ((SSLSocket) socket).getSession().getCipherSuite());
									writer = new OutputStreamWriter(socket.getOutputStream());
									this.reset();
									throw new UpgradeToTLSException();
								}
								catch(SSLHandshakeException e)
								{
									logger.debug((hostname == null ? socket.getRemoteSocketAddress().toString() : hostname) + " = TLS handshake failed: " + e.getMessage());
									if(server.eventHandler.isEncryptionRequired(this))
									{
										break;
									}
									this.reset();
								}
							}
						}
						else if(line.toUpperCase().startsWith("MAIL"))
						{
							if(!line.toUpperCase().startsWith("MAIL FROM:<") || !line.endsWith(">"))
							{
								write("501 Syntax: MAIL FROM:<address>");
							}
							else if(!isEncrypted() && server.eventHandler.isEncryptionRequired(this))
							{
								write("503 Encryption is required. Send STARTTLS first.");
							}
							else if(buildingMail != null)
							{
								write("503 You've already sent MAIL.");
							}
							else
							{
								try
								{
									SMTPAddress sender = SMTPAddress.fromText(line.substring(11, line.length() - 1));
									if(server.eventHandler.isSenderAccepted(this, sender))
									{
										buildingMail = new SMTPMail();
										buildingMail.sender = sender;
										write("250 OK");
									}
									else
									{
										write("553 You're not allowed to send mail.");
									}
								}
								catch(InvalidAddressException ignored)
								{
									write("553 " + line.substring(10) + " is not a valid address.");
								}
							}
						}
						else if(line.toUpperCase().startsWith("RCPT"))
						{
							if(!line.toUpperCase().startsWith("RCPT TO:<") || !line.endsWith(">"))
							{
								write("501 Syntax: RCPT TO:<address>");
							}
							else if(buildingMail == null)
							{
								write("503 Send MAIL first.");
							}
							else
							{
								try
								{
									SMTPAddress address = SMTPAddress.fromText(line.substring(9, line.length() - 1));
									if(server.eventHandler.isRecipientAccepted(this, address))
									{
										buildingMail.recipients.add(address);
										write("250 OK");
									}
									else
									{
										write("553 Can't deliver to " + address.toString());
									}
								}
								catch(InvalidAddressException ignored)
								{
									write("553 " + line.substring(8) + " is not a valid address.");
								}
							}
						}
						else if(line.toUpperCase().startsWith("VRFY"))
						{
							if(!line.toUpperCase().startsWith("VRFY "))
							{
								write("501 Syntax: VRFY <address>");
							}
							try
							{
								SMTPAddress address = SMTPAddress.fromText(line.substring(5));
								if(server.eventHandler.isRecipientAccepted(this, address))
								{
									write("250 Can deliver to " + address.toString());
								}
								else
								{
									write("553 Can't deliver to " + address.toString());
								}
							}
							catch(InvalidAddressException ignored)
							{
								write("553 " + line.substring(5) + " is not a valid email address.");
							}
						}
						else if(line.toUpperCase().startsWith("DATA"))
						{
							if(buildingMail == null)
							{
								write("503 Send MAIL first.");
							}
							else if(buildingMail.recipients.size() == 0)
							{
								write("503 Send RCPT first.");
							}
							else
							{
								write("354 Start mail input; end with <CRLF>.<CRLF>");
								writer.flush();
								boolean headersDefined = false;
								String lastHeader = null;
								do
								{
									String mailLine = scanner.next();
									logger.debug((hostname == null ? socket.getRemoteSocketAddress().toString() : hostname) + " > " + mailLine);
									if(!headersDefined)
									{
										if(mailLine.contains(":"))
										{
											String[] arr = mailLine.split(":");
											if(arr.length > 1)
											{
												lastHeader = arr[0].toLowerCase();
												buildingMail.headers.put(lastHeader, mailLine.substring(arr[0].length() + 1).trim());
											}
											continue;
										}
										if(mailLine.length() == 0)
										{
											headersDefined = true;
										}
										else if(lastHeader != null)
										{
											buildingMail.headers.put(lastHeader, buildingMail.headers.get(lastHeader) + mailLine.trim());
										}
									}
									else
									{
										if(mailLine.length() > 0 && mailLine.substring(0, 1).equals("."))
										{
											if(mailLine.length() == 1)
											{
												buildingMail.headers.put("date", SMTPContent.RFC2822.format(new Date()));
												buildingMail.content = SMTPContent.from(buildingMail.headers, buildingMail.body.toString());
												if(server.eventHandler.onMailComposed(this, buildingMail))
												{
													write("250 OK");
												}
												else
												{
													write("553 Failed to deliver mail");
												}
												buildingMail = null;
												break;
											}
											mailLine = mailLine.substring(1);
										}
										buildingMail.body.append(mailLine).append("\n");
									}
								}
								while(!this.isInterrupted());
								buildingMail = new SMTPMail();
							}
						}
						else if(line.toUpperCase().startsWith("HELP"))
						{
							write("502 Command not implemented");
						}
						else
						{
							write("500 Command unrecognized");
						}
						writer.flush();
					}
					while(!this.isInterrupted());
				}
				catch(UpgradeToTLSException ignored)
				{
					continueDo = true;
				}
			}
			while(continueDo);
		}
		catch(NoSuchElementException ignored)
		{
			// Connection closed by client
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		if(!socket.isClosed())
		{
			try
			{
				socket.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
		synchronized(server.sessions)
		{
			server.sessions.remove(this);
		}
	}

	public boolean isEncrypted()
	{
		return (socket instanceof SSLSocket);
	}
}
