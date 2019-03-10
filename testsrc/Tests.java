import org.junit.Test;
import sh.hell.jsmtp.client.SMTPClient;
import sh.hell.jsmtp.content.SMTPAddress;
import sh.hell.jsmtp.content.SMTPContent;
import sh.hell.jsmtp.content.SMTPEncoding;
import sh.hell.jsmtp.content.SMTPMail;
import sh.hell.jsmtp.content.SMTPMultipartContent;
import sh.hell.jsmtp.content.SMTPTextContent;
import sh.hell.jsmtp.server.SMTPEventHandler;
import sh.hell.jsmtp.server.SMTPServer;
import sh.hell.jsmtp.server.SMTPSession;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotEquals;

@SuppressWarnings("ConstantConditions")
public class Tests
{
	@Test(timeout = 1000L)
	public void testEncodingAndDecoding()
	{
		assertEquals("H=C3=AAll=C3=B3, w=C3=B6rld!", SMTPEncoding.QUOTED_PRINTABLE.encode("Hêlló, wörld!"));
		assertEquals("Hêlló, wörld!", SMTPEncoding.QUOTED_PRINTABLE.decode("H=C3=AAll=C3=B3, w=C3=B6rld!"));
		assertEquals("SMOqbGzDsywgd8O2cmxkIQ==", SMTPEncoding.BASE64.encode("Hêlló, wörld!"));
		assertEquals("Hêlló, wörld!", SMTPEncoding.BASE64.decode("SMOqbGzDsywgd8O2cmxkIQ=="));
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

	private static final SMTPContent testContent = new SMTPMultipartContent().addPart(new SMTPTextContent("text/plain", "Hêlló, wörld!\r\n\r\n.\r\n")).addPart(new SMTPTextContent("text/html", "<b>Hêlló, wörld!</b>\r\n\r\n.\r\n"));

	@Test(timeout = 10000L)
	public void testClientAgainstRemoteServer() throws Exception
	{
		SMTPClient.sendMail(new SMTPMail().from(SMTPAddress.fromText("Sender <sender@justsometestdomain.de>")).to(SMTPAddress.fromText("Recipient <jsmtp@existiert.net>")).subject("This is a test.").data(testContent));
	}

	@Test(timeout = 5000L)
	public void testServerAndClient() throws Exception
	{
		// Starting server
		SMTPServer server = new SMTPServer(new SMTPEventHandler()
		{
			@Override
			public String getWelcomeMessage(SMTPSession session)
			{
				return "Wêlcömé";
			}

			@Override
			public String getHostname(SMTPSession session)
			{
				return "localhost";
			}

			public int getSizeLimit(SMTPSession session)
			{
				return 1000;
			}

			@Override
			public boolean isSenderAccepted(SMTPSession session, SMTPAddress address)
			{
				return !address.getInboxName().equals("denied");
			}

			@Override
			public boolean isRecipientAccepted(SMTPSession session, SMTPAddress address)
			{
				return !address.getInboxName().equals("denied");
			}

			@Override
			public boolean onMailComposed(SMTPSession session, SMTPMail mail)
			{
				System.out.println("Mail was composed: " + mail.toString());
				assertTrue(mail.contents instanceof SMTPMultipartContent);
				assertEquals(2, ((SMTPMultipartContent) mail.contents).parts.size());
				for(SMTPContent body : ((SMTPMultipartContent) mail.contents).parts)
				{
					assertTrue(body instanceof SMTPTextContent);
					if(((SMTPTextContent) body).type.equals("text/plain"))
					{
						assertEquals("Hêlló, wörld!\r\n\r\n.\r\n", ((SMTPTextContent) body).body);
					}
					else
					{
						assertEquals("text/html", ((SMTPTextContent) body).type);
						assertEquals("<b>Hêlló, wörld!</b>\r\n\r\n.\r\n", ((SMTPTextContent) body).body);
					}
				}
				return true;
			}
		}).setPorts(0).start();
		// Connecting to the server
		SMTPClient client = new SMTPClient("localhost", server.listeners.get(0).socket.getLocalPort());
		assertNotNull(client);
		assertTrue(client.isOpen());
		assertEquals("Wêlcömé", client.serverWelcomeMessage);
		// Identifying
		client.hello("localhost");
		assertTrue(client.extendedSMTP);
		assertEquals("localhost", client.serverHostname);
		// Define recipient before sender was set
		client.write("RCPT TO:<recipient@localhost>").flush();
		assertNotEquals("250", client.readResponse().status);
		// Defining sender which should be denied
		client.write("MAIL FROM:<denied@localhost>").flush();
		assertNotEquals("250", client.readResponse().status);
		// Attemting to send email that's too big
		client.write("MAIL FROM:<sender@localhost> SIZE=1337").flush();
		assertEquals("552", client.readResponse().status);
		// Pipelining
		client.write("MAIL FROM:<sender@localhost>");
		client.write("RCPT TO:<allowed@localhost>");
		client.write("RCPT TO:<denied@localhost>");
		client.write("DATA");
		client.flush();
		assertEquals("250", client.readResponse().status);
		assertEquals("250", client.readResponse().status);
		assertNotEquals("250", client.readResponse().status);
		assertEquals("354", client.readResponse().status);
		client.write(".").flush();
		assertEquals("554", client.readResponse().status);
		// Send email
		client.send(new SMTPMail().from(new SMTPAddress("sender@localhost")).to(new SMTPAddress("recipient-1@localhost")).to(new SMTPAddress("recipient-2@localhost")).subject("Test").data(testContent));
		// Stop
		client.close();
		server.stop(true);
	}
}
