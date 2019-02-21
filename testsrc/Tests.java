import org.junit.Test;
import sh.hell.jsmtp.SMTPAddress;
import sh.hell.jsmtp.client.SMTPClient;
import sh.hell.jsmtp.content.SMTPContent;
import sh.hell.jsmtp.content.SMTPEncoding;
import sh.hell.jsmtp.content.SMTPMultipartContent;
import sh.hell.jsmtp.content.SMTPTextContent;
import sh.hell.jsmtp.exceptions.SMTPException;
import sh.hell.jsmtp.server.SMTPEventHandler;
import sh.hell.jsmtp.server.SMTPMail;
import sh.hell.jsmtp.server.SMTPServer;
import sh.hell.jsmtp.server.SMTPSession;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings("ConstantConditions")
public class Tests
{
	@Test(timeout = 1000L)
	public void testEncodingAndDecoding()
	{
		final String text = "Hêlló, wörld!\n\n.\n";
		assertEquals(text, SMTPEncoding.EIGHTBIT.decode(SMTPEncoding.EIGHTBIT.encode(text)));
		assertEquals(text, SMTPEncoding.QUOTED_PRINTABLE.decode(SMTPEncoding.QUOTED_PRINTABLE.encode(text)));
		assertEquals(text, SMTPEncoding.BASE64.decode(SMTPEncoding.BASE64.encode(text)));
	}

	@Test(timeout = 5000L)
	public void testLookups() throws Exception
	{
		// With MX Record
		String[] mailServers = SMTPAddress.fromText("lookup-test@timmyrs.de").getMailServers();
		assertEquals(1, mailServers.length);
		assertEquals("m1.hell.sh", mailServers[0]);
		// Without MX Record
		mailServers = SMTPAddress.fromText("lookup-test@trash-mail.com").getMailServers();
		assertEquals(1, mailServers.length);
		assertEquals("trash-mail.com", mailServers[0]);
	}

	@Test(timeout = 10000L)
	public void testClientAgainstRemoteServer() throws Exception
	{
		SMTPAddress recipient1 = SMTPAddress.fromText("Recipient 1 <jsmtp@existiert.net>");
		SMTPClient.fromAddress(recipient1).hello("justsometestdomain.de", false).from(SMTPAddress.fromText("Sender <sender@justsometestdomain.de>")).to(recipient1).cc(SMTPAddress.fromText("Recipient 2 <cc@existiert.net>")).bcc(SMTPAddress.fromText("Recipient 3 <bcc@existiert.net>")).close();
	}

	@Test(timeout = 5000L)
	public void testServerAndClient() throws Exception
	{
		// Variables for the test
		final String welcome = "Wêlcömé";
		final String textMessage = "Hêlló, wörld!\n\n.\n";
		final String htmlMessage = "<b>Hêlló, wörld!</b>\n\n.\n";
		// Starting server
		SMTPServer server = new SMTPServer(new SMTPEventHandler()
		{
			@Override
			public String getWelcomeMessage(SMTPSession session)
			{
				return welcome;
			}

			@Override
			public String getHostname(SMTPSession session)
			{
				return "localhost";
			}

			@Override
			public boolean isSenderAccepted(SMTPSession session, SMTPAddress address)
			{
				return !address.mail.equals("denied@localhost");
			}

			@Override
			public boolean isRecipientAccepted(SMTPSession session, SMTPAddress address)
			{
				return !address.mail.equals("denied@localhost");
			}

			@Override
			public boolean onMailComposed(SMTPSession session, SMTPMail mail)
			{
				System.out.println("Mail was composed: " + mail.toString());
				assertTrue(mail.content instanceof SMTPMultipartContent);
				assertEquals(2, ((SMTPMultipartContent) mail.content).parts.size());
				for(SMTPContent body : ((SMTPMultipartContent) mail.content).parts)
				{
					assertTrue(body instanceof SMTPTextContent);
					if(((SMTPTextContent) body).type.equals("text/plain"))
					{
						assertEquals(textMessage, ((SMTPTextContent) body).body);
					}
					else
					{
						assertEquals("text/html", ((SMTPTextContent) body).type);
						assertEquals(htmlMessage, ((SMTPTextContent) body).body);
					}
				}
				return true;
			}
		}).setPorts(0).start();
		// Connecting to the server
		SMTPClient client = new SMTPClient("localhost", server.listeners.get(0).socket.getLocalPort());
		assertNotNull(client);
		assertTrue(client.isOpen());
		assertEquals(welcome, client.serverWelcomeMessage);
		// Identifying
		client.hello("localhost", System.getProperty("java.version").startsWith("11")); // TLS from localhost to localhost doesn't work starting in Java 11, but "testClientAgainstRemoteServer" above ensures that encryption works (at least in the client).
		assertTrue(client.extendedSMTP);
		assertEquals("localhost", client.serverHostname);
		// Defining sender which should be denied
		boolean failed = false;
		try
		{
			client.from(new SMTPAddress("denied@localhost"));
		}
		catch(SMTPException ignored)
		{
			failed = true;
		}
		assertTrue(failed);
		// Define recipient before sender was set
		failed = false;
		try
		{
			client.to(new SMTPAddress("recipient@localhost"));
		}
		catch(SMTPException ignored)
		{
			failed = true;
		}
		assertTrue(failed);
		// Define sender
		client.from(new SMTPAddress("test@localhost"));
		// Define denied recipient
		assertFalse(client.verify(new SMTPAddress("denied@" + "localhost")));
		failed = false;
		try
		{
			client.to(new SMTPAddress("denied@localhost"));
		}
		catch(SMTPException ignored)
		{
			failed = true;
		}
		assertTrue(failed);
		// Define recipients
		client.to(new SMTPAddress("recipient-1@localhost"));
		client.to(new SMTPAddress("recipient-2@localhost"));
		// Send email
		client.subject("Test");
		client.send(new SMTPMultipartContent().addPart(new SMTPTextContent("text/plain", textMessage)).addPart(new SMTPTextContent("text/html", htmlMessage)));
		// Stopping
		client.close();
		server.stop(true);
	}
}
