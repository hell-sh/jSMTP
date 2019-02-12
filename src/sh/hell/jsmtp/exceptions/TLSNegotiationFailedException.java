package sh.hell.jsmtp.exceptions;

public class TLSNegotiationFailedException extends SMTPException
{
	public TLSNegotiationFailedException()
	{
		super("TLS negotiation failed.");
	}

	public TLSNegotiationFailedException(String message)
	{
		super(message);
	}
}
