package sh.hell.jsmtp;

import sh.hell.jsmtp.content.SMTPMail;
import sh.hell.jsmtp.server.SMTPEventHandler;
import sh.hell.jsmtp.server.SMTPServer;
import sh.hell.jsmtp.server.SMTPSession;

public class Main
{
	public static void main(String[] args) throws Exception
	{
		System.out.println("I'm a library, but because you executed me I'm opening an SMTP server on *:465.");
		new SMTPServer(new SMTPEventHandler()
		{
			@Override
			public String getHostname(SMTPSession session)
			{
				return "localhost";
			}

			@Override
			public boolean onMailComposed(SMTPSession session, SMTPMail mail)
			{
				System.out.println(mail);
				return true;
			}
		}, ".jsmtp_keystore", "123456").setPorts(465).start();
	}
}
