package sh.hell.jsmtp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.hell.jsmtp.exceptions.InvalidStateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;

@SuppressWarnings({"WeakerAccess", "UnusedReturnValue", "unused"})
public class SMTPServer
{
	private static final Logger logger = LoggerFactory.getLogger(SMTPServer.class);
	public final SSLSocketFactory sslSocketFactory;
	public final ArrayList<SMTPListener> listeners = new ArrayList<>();
	public final ArrayList<SMTPSession> sessions = new ArrayList<>();
	final SMTPEventHandler eventHandler;
	public int[] ports = new int[]{25, 587};

	public SMTPServer(SMTPEventHandler eventHandler)
	{
		this(eventHandler, null);
	}

	public SMTPServer(SMTPEventHandler eventHandler, SSLSocketFactory sslSocketFactory)
	{
		this.eventHandler = eventHandler;
		if(sslSocketFactory == null)
		{
			this.sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		}
		else
		{
			this.sslSocketFactory = sslSocketFactory;
		}
	}

	public SMTPServer(SMTPEventHandler eventHandler, String keyStoreFile, String keyStorePassword) throws IOException, GeneralSecurityException
	{
		final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager()
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
		}};
		final SSLContext sctx = SSLContext.getInstance("TLSv1.2");
		final char[] pw = keyStorePassword.toCharArray();
		KeyStore ks = KeyStore.getInstance("JKS");
		InputStream ksIs = new FileInputStream(keyStoreFile);
		ks.load(ksIs, pw);
		ksIs.close();
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, pw);
		sctx.init(kmf.getKeyManagers(), trustAllCerts, new SecureRandom());
		this.eventHandler = eventHandler;
		this.sslSocketFactory = sctx.getSocketFactory();
	}

	public boolean isOnline()
	{
		return listeners.size() > 0;
	}

	public SMTPServer setPorts(int... ports) throws InvalidStateException
	{
		if(isOnline())
		{
			throw new InvalidStateException("Ports can only be set when the server is offline.");
		}
		this.ports = ports;
		return this;
	}

	public SMTPServer start() throws InvalidStateException, IOException
	{
		if(isOnline())
		{
			throw new InvalidStateException("The server is already online.");
		}
		if(ports.length == 0)
		{
			throw new InvalidStateException("Can't start a server with no ports to listen on.");
		}
		synchronized(listeners)
		{
			for(int port : ports)
			{
				logger.info("Binding to *:" + port + "...");
				listeners.add(new SMTPListener(this, port));
			}
		}
		logger.info("SMTP Server started.");
		return this;
	}

	/**
	 * Stops listening for new connections on the SMTPServer. Optionally, disconnects all clients.
	 *
	 * @param closeSessions Set to true to not only stop listening but also to disconnect all clients.
	 * @return this
	 */
	public SMTPServer stop(boolean closeSessions)
	{
		if(isOnline())
		{
			logger.info("Interrupting listeners...");
			synchronized(listeners)
			{
				for(SMTPListener listener : listeners)
				{
					try
					{
						listener.socket.close();
					}
					catch(IOException ignored)
					{
					}
				}
				listeners.clear();
			}
		}
		if(closeSessions)
		{
			logger.info("Closing sessions...");
			synchronized(sessions)
			{
				for(SMTPSession session : sessions)
				{
					try
					{
						session.write("421 Shutting down.");
						session.writer.flush();
					}
					catch(IOException ignored)
					{

					}
					try
					{
						session.writer.close();
					}
					catch(IOException ignored)
					{
					}
					try
					{
						session.socket.close();
					}
					catch(IOException ignored)
					{
					}
					if(session.scanner != null)
					{
						session.scanner.close();
					}
					session.interrupt();
				}
				sessions.clear();
			}
		}
		return this;
	}
}