package sh.hell.jsmtp.exceptions;

public class InvalidAddressException extends SMTPException
{
	public InvalidAddressException()
	{
		super("Invalid address.");
	}
}
