package sh.hell.jsmtp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.hell.jsmtp.content.SMTPAddress;
import sh.hell.jsmtp.content.SMTPContent;
import sh.hell.jsmtp.content.SMTPMail;
import sh.hell.jsmtp.exceptions.InvalidAddressException;
import sh.hell.jsmtp.exceptions.TLSNegotiationFailedException;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Scanner;

@SuppressWarnings({"WeakerAccess", "unused"})
public class SMTPSession extends Thread
{
	private static final Logger logger = LoggerFactory.getLogger(SMTPSession.class);
	private final SMTPServer server;
	public String hostname;
	public boolean extendedSMTP = false;
	private Socket socket;
	private OutputStreamWriter writer;
	private Scanner scanner;
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
								write("501 Syntax: HELO <hostname>");
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
								write("250-PIPELINING");
								if(!isEncrypted())
								{
									write("250-STARTTLS");
								}
								final int sizeLimit = server.eventHandler.getSizeLimit(this);
								if(sizeLimit > -1)
								{
									write("250-SIZE " + sizeLimit);
								}
								if(server.eventHandler.isVRFYallowed(this))
								{
									write("250-VRFY");
								}
								write("250-8BITMIME");
								write("250 SMTPUTF8");
							}
							else
							{
								write("501 Syntax: EHLO <hostname>");
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
								write("220 Cast the Transport Layer Security spell.");
								writer.flush();
								try
								{
									final SSLSocket sslSocket = (SSLSocket) server.sslSocketFactory.createSocket(socket, ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostName(), socket.getPort(), true);
									sslSocket.setUseClientMode(false);
									sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
									sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
									sslSocket.startHandshake();
									if(sslSocket.getSession().getCipherSuite().startsWith("TLS handshake failed"))
									{
										throw new TLSNegotiationFailedException(sslSocket.getSession().getCipherSuite());
									}
									logger.debug((hostname == null ? socket.getRemoteSocketAddress().toString() : hostname) + " = Cipher suite: " + sslSocket.getSession().getCipherSuite());
									this.socket = sslSocket;
									this.writer = new OutputStreamWriter(socket.getOutputStream());
									this.reset();
									throw new UpgradeToTLSException();
								}
								catch(IOException | TLSNegotiationFailedException e)
								{
									logger.info((hostname == null ? socket.getRemoteSocketAddress().toString() : hostname) + " = TLS handshake failed: " + e.getMessage());
									break;
								}
							}
						}
						else if(line.toUpperCase().startsWith("MAIL"))
						{
							if(!isEncrypted() && server.eventHandler.isEncryptionRequired(this))
							{
								write("503 Encryption is required. Send STARTTLS first.");
							}
							else if(buildingMail != null)
							{
								write("503 You've already sent MAIL.");
							}
							else
							{
								SMTPAddress sender = null;
								String[] arr = line.split(" ");
								int size = 0;
								for(int i = 1; i < arr.length; i++)
								{
									if(arr[i].toUpperCase().startsWith("FROM:<") && arr[i].endsWith(">"))
									{
										try
										{
											sender = SMTPAddress.fromText(arr[i].substring(6, arr[i].length() - 1));
										}
										catch(InvalidAddressException ignored)
										{
											write("553 " + arr[i].substring(5) + " is not a valid address.");
										}

									}
									else if(arr[i].toUpperCase().startsWith("SIZE="))
									{
										try
										{
											size = Integer.valueOf(arr[i].substring(5));
										}
										catch(NumberFormatException ignored)
										{
										}
									}
								}
								if(sender == null)
								{
									write("501 Syntax: MAIL FROM:<address>");
								}
								else
								{
									final int sizeLimit = server.eventHandler.getSizeLimit(this);
									if(size > 0 && sizeLimit >= 0 && size > sizeLimit)
									{
										write("552 I don't accept " + size + "-byte emails.");
									}
									else if(server.eventHandler.isSenderAccepted(this, sender))
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
							}
						}
						else if(line.toUpperCase().startsWith("RCPT"))
						{
							if(buildingMail == null)
							{
								write("503 Send MAIL first.");
							}
							else
							{
								SMTPAddress recipient = null;
								String[] arr = line.split(" ");
								for(int i = 1; i < arr.length; i++)
								{
									if(arr[i].toUpperCase().startsWith("TO:<") && arr[i].endsWith(">"))
									{
										try
										{
											recipient = SMTPAddress.fromText(arr[i].substring(4, arr[i].length() - 1));
										}
										catch(InvalidAddressException ignored)
										{
											write("553 " + arr[i].substring(3) + " is not a valid address.");
										}
									}
								}
								if(recipient == null)
								{
									write("501 Syntax: RCPT TO:<address>");
								}
								else if(server.eventHandler.isRecipientAccepted(this, recipient))
								{
									buildingMail.recipients.add(recipient);
									write("250 OK");
								}
								else
								{
									write("553 Can't deliver to " + recipient.toString());
								}
							}
						}
						else if(line.toUpperCase().startsWith("VRFY"))
						{
							if(server.eventHandler.isVRFYallowed(this))
							{
								if(!line.toUpperCase().startsWith("VRFY "))
								{
									write("501 Syntax error in parameters or arguments");
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
							else
							{
								write("502 Command not implemented");
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
								final StringBuilder body = new StringBuilder();
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
											continue;
										}
										if(lastHeader != null)
										{
											buildingMail.headers.put(lastHeader, buildingMail.headers.get(lastHeader) + mailLine.trim());
										}
									}
									if(mailLine.length() > 0 && mailLine.startsWith("."))
									{
										if(mailLine.length() == 1)
										{
											final String bodystr = body.toString();
											if(bodystr.equals(""))
											{
												write("554 Transaction failed successfully");
											}
											else
											{
												final int sizeLimit = server.eventHandler.getSizeLimit(this);
												if(sizeLimit >= 0 && body.length() > sizeLimit)
												{
													write("552 Your email is too big.");
												}
												else
												{
													buildingMail.headers.put("date", SMTPContent.RFC2822.format(new Date()));
													buildingMail.contents = SMTPContent.from(buildingMail.headers, bodystr);
													if(sizeLimit >= 0 && buildingMail.getRawContents().length() > sizeLimit)
													{
														write("552 Your email is too big.");
													}
													else if(server.eventHandler.onMailComposed(this, buildingMail))
													{
														write("250 OK");
													}
													else
													{
														write("554 Failed to deliver mail");
													}
												}
											}
											buildingMail = null;
											break;
										}
										if(headersDefined)
										{
											body.append(mailLine.substring(1)).append("\n");
										}
									}
									else if(headersDefined)
									{
										body.append(mailLine).append("\n");
									}
								}
								while(!this.isInterrupted());
								buildingMail = null;
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

	public void close()
	{
		this.close(null);
	}

	public void close(String message)
	{
		if(message != null)
		{
			try
			{
				write("421 Shutting down.");
				writer.flush();
			}
			catch(IOException ignored)
			{

			}
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
		if(scanner != null)
		{
			scanner.close();
		}
		this.interrupt();
	}

	public boolean isOpen()
	{
		return !socket.isClosed();
	}

	public boolean isEncrypted()
	{
		return (socket instanceof SSLSocket);
	}
}
