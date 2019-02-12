package sh.hell.jsmtp.server;

import sh.hell.jsmtp.SMTPAddress;

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class SMTPEventHandler
{
	public boolean isIPAccepted(String ip)
	{
		return true;
	}

	public String getWelcomeMessage(SMTPSession session)
	{
		return "Welcome to " + this.getHostname(session);
	}

	public abstract String getHostname(SMTPSession session);

	public boolean isEncryptionRequired(SMTPSession session)
	{
		return false;
	}

	public boolean isSenderAccepted(SMTPSession session, SMTPAddress address)
	{
		return true;
	}

	public boolean isRecipientAccepted(SMTPSession session, SMTPAddress address)
	{
		return true;
	}

	/**
	 * Delivers the composed mail.
	 *
	 * @param session The SMTPSession of the composer.
	 * @param mail    The email that has been composed.
	 * @return True if delivery was successful.
	 */
	public abstract boolean onMailComposed(SMTPSession session, SMTPMail mail);
}
