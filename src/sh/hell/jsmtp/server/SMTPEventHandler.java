package sh.hell.jsmtp.server;

import sh.hell.jsmtp.content.SMTPAddress;
import sh.hell.jsmtp.content.SMTPMail;

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

	/**
	 * Returns the size limit of emails in bytes. Use -1 for unlimited.
	 *
	 * @param session The SMTPSession of the client the size limit will apply to.
	 * @return the size limit of emails in bytes. Use -1 for unlimited.
	 */
	public int getSizeLimit(SMTPSession session)
	{
		return -1;
	}

	/**
	 * Returns whether VRFY commands are supported for the given session.
	 *
	 * @param session The SMTPSession of the client this permission will apply to.
	 * @return whether VRFY commands are supported for the given session.
	 */
	public boolean isVRFYallowed(SMTPSession session)
	{
		return true;
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
